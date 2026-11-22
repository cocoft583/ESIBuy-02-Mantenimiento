package es.uclm.esibuy.statistics.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// Necesita Docker: sin Docker estos tests se saltan en lugar de fallar
@DataMongoTest
@Testcontainers(disabledWithoutDocker = true)
@Import(MongoStatisticsRepository.class)
class MongoStatisticsRepositoryTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private MongoStatisticsRepository repository;

    @BeforeEach
    void setUp() {
        mongoTemplate.dropCollection("pedidos");
    }

    @Test
    @DisplayName("HU22-CP01: solo cuentan los pedidos pagados que no están cancelados")
    void shouldOnlyCountPaidOrdersThatAreNotCancelled() {
        // Un pedido recién pagado queda PENDIENTE de preparar con el pago en PAGADO
        order("PAGADO", "PENDIENTE", line("p1", "Auriculares", "v1", 2, "20.00"));
        order("PAGADO", "ENTREGADO", line("p1", "Auriculares", "v1", 1, "10.00"));
        order("PENDIENTE", "PENDIENTE", line("p1", "Auriculares", "v1", 50, "500.00"));
        order("PAGADO", "CANCELADO", line("p1", "Auriculares", "v1", 40, "400.00"));

        List<ProductSalesRow> ranking = repository.findTopSellingProducts(10);

        assertThat(ranking).hasSize(1);
        assertThat(ranking.get(0).unitsSold()).isEqualTo(3);
        assertThat(ranking.get(0).revenue()).isEqualByComparingTo("30.00");
    }

    @Test
    @DisplayName("HU22-CP02: se suman unidades e ingresos del mismo producto entre pedidos y líneas")
    void shouldAddUpUnitsAndRevenueAcrossOrdersAndLines() {
        order("PAGADO", "ENVIADO",
                line("p1", "Auriculares", "v1", 2, "20.00"),
                line("p2", "Altavoz", "v2", 1, "80.00"));
        order("PAGADO", "ENTREGADO", line("p1", "Auriculares", "v1", 3, "30.00"));

        List<ProductSalesRow> ranking = repository.findTopSellingProducts(10);

        assertThat(ranking).extracting(ProductSalesRow::productId).containsExactly("p1", "p2");
        assertThat(ranking.get(0).productName()).isEqualTo("Auriculares");
        assertThat(ranking.get(0).unitsSold()).isEqualTo(5);
        assertThat(ranking.get(0).revenue()).isEqualByComparingTo("50.00");
        assertThat(ranking.get(1).revenue()).isEqualByComparingTo("80.00");
    }

    @Test
    @DisplayName("HU22-CP03: los importes guardados como texto o como decimal suman igual")
    void shouldSumAmountsStoredAsTextOrAsDecimal() {
        // Como en la base real, unos pedidos guardan el subtotal como texto y otros como decimal
        order("PAGADO", "ENVIADO", line("p1", "Auriculares", "v1", 1, "19.99"));
        order("PAGADO", "ENVIADO", line("p1", "Auriculares", "v1", 1, new Decimal128(new BigDecimal("19.99"))));

        List<ProductSalesRow> ranking = repository.findTopSellingProducts(10);

        assertThat(ranking.get(0).revenue()).isEqualByComparingTo("39.98");
    }

    @Test
    @DisplayName("HU22-CP04: el ranking va de más a menos unidades y desempata por ingresos")
    void shouldSortByUnitsThenRevenue() {
        order("PAGADO", "ENVIADO",
                line("barato", "Barato", "v1", 5, "5.00"),
                line("caro", "Caro", "v1", 5, "500.00"),
                line("top", "Top", "v1", 9, "9.00"),
                line("poco", "Poco", "v1", 1, "1.00"));

        List<ProductSalesRow> ranking = repository.findTopSellingProducts(10);

        assertThat(ranking).extracting(ProductSalesRow::productId)
                .containsExactly("top", "caro", "barato", "poco");
    }

    @Test
    @DisplayName("HU22-CP05: con empate en el puesto 10 se devuelven solo 10, siempre los mismos")
    void shouldReturnExactlyTenWhenThereIsATieAtTenthPlace() {
        for (int i = 1; i <= 12; i++) {
            order("PAGADO", "ENVIADO", line("p%02d".formatted(i), "Producto " + i, "v1", 4, "40.00"));
        }

        List<ProductSalesRow> first = repository.findTopSellingProducts(10);
        List<ProductSalesRow> second = repository.findTopSellingProducts(10);

        assertThat(first).hasSize(10);
        assertThat(first).extracting(ProductSalesRow::productId)
                .containsExactly("p01", "p02", "p03", "p04", "p05", "p06", "p07", "p08", "p09", "p10");
        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("HU22-CP06: se conserva el vendedor de la línea aunque otras líneas no lo guarden")
    void shouldKeepTheSellerWhenSomeLinesDoNotStoreIt() {
        order("PAGADO", "ENVIADO", line("p1", "Auriculares", null, 1, "10.00"));
        order("PAGADO", "ENVIADO", line("p1", "Auriculares", "v1", 1, "10.00"));
        order("PAGADO", "ENVIADO", line("p2", "Altavoz", null, 1, "10.00"));

        List<ProductSalesRow> ranking = repository.findTopSellingProducts(10);

        assertThat(ranking.get(0).productId()).isEqualTo("p1");
        assertThat(ranking.get(0).sellerId()).isEqualTo("v1");
        assertThat(ranking.get(1).sellerId()).isNull();
    }

    @Test
    @DisplayName("HU22-CP07: sin ventas el ranking sale vacío")
    void shouldReturnAnEmptyRankingWhenThereAreNoSales() {
        order("PENDIENTE", "PENDIENTE", line("p1", "Auriculares", "v1", 1, "10.00"));

        assertThat(repository.findTopSellingProducts(10)).isEmpty();
    }

    @Test
    @DisplayName("HU24-CP09: del vendedor solo cuentan sus líneas pagadas y no canceladas")
    void shouldOnlyCountPaidAndNotCancelledLinesOfTheSeller() {
        order("PAGADO", "ENTREGADO",
                line("p1", "Teclado", "v1", 2, "20.00"),
                line("p9", "Altavoz", "v2", 50, "500.00"));
        order("PAGADO", "CANCELADO", line("p1", "Teclado", "v1", 40, "400.00"));
        order("PENDIENTE", "PENDIENTE", line("p1", "Teclado", "v1", 30, "300.00"));

        List<ProductSalesRow> sales = repository.findSellerProductSales("v1", List.of("p1"));

        assertThat(sales).extracting(ProductSalesRow::productId).containsExactly("p1");
        assertThat(sales.get(0).unitsSold()).isEqualTo(2);
        assertThat(sales.get(0).revenue()).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("HU24-CP10: una línea antigua sin vendedor cuenta si el producto es del vendedor")
    void shouldAttributeLegacyLinesWithoutSellerByProduct() {
        order("PAGADO", "ENVIADO",
                line("p1", "Teclado", null, 2, "20.00"),
                line("p8", "Ratón", null, 4, "40.00"));
        order("PAGADO", "ENVIADO", line("p1", "Teclado", "", 1, "10.00"));

        List<ProductSalesRow> sales = repository.findSellerProductSales("v1", List.of("p1"));

        assertThat(sales).extracting(ProductSalesRow::productId).containsExactly("p1");
        assertThat(sales.get(0).unitsSold()).isEqualTo(3);
    }

    @Test
    @DisplayName("HU24-CP11: una línea de otro vendedor no cuenta aunque el producto sea ahora del vendedor")
    void shouldNotCountLinesOfAnotherSellerForTheSameProduct() {
        order("PAGADO", "ENTREGADO", line("p1", "Teclado", "v2", 5, "50.00"));

        assertThat(repository.findSellerProductSales("v1", List.of("p1"))).isEmpty();
    }

    @Test
    @DisplayName("HU24-CP12: se devuelven todas las filas del vendedor, de más a menos vendidas")
    void shouldReturnEveryRowOfTheSellerSortedByUnits() {
        for (int i = 0; i < 12; i++) {
            order("PAGADO", "ENTREGADO", line("p" + i, "Producto " + i, "v1", i + 1, "10.00"));
        }

        List<ProductSalesRow> sales = repository.findSellerProductSales("v1", List.of());

        assertThat(sales).hasSize(12);
        assertThat(sales.get(0).productId()).isEqualTo("p11");
        assertThat(sales.get(11).productId()).isEqualTo("p0");
    }

    private Document line(String productId, String name, String sellerId, int quantity, Object subtotal) {
        return new Document("productoId", productId)
                .append("nombre", name)
                .append("cantidad", quantity)
                .append("precioUnitario", "1.00")
                .append("subtotal", subtotal)
                .append("vendedorId", sellerId);
    }

    private void order(String paymentStatus, String status, Document... lines) {
        mongoTemplate.getCollection("pedidos").insertOne(new Document("clienteId", "cliente-1")
                .append("estadoPago", paymentStatus)
                .append("estado", status)
                .append("items", List.of(lines)));
    }
}
