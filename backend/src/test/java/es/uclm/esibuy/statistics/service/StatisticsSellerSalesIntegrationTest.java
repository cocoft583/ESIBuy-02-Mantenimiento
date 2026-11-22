package es.uclm.esibuy.statistics.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import es.uclm.esibuy.configuration.service.GeneralConfigurationService;
import es.uclm.esibuy.products.model.Product;
import es.uclm.esibuy.products.model.ProductDetails;
import es.uclm.esibuy.products.repository.ProductRepository;
import es.uclm.esibuy.statistics.dto.ProductSalesDTO;
import es.uclm.esibuy.statistics.dto.SalesStatisticsDTO;
import es.uclm.esibuy.statistics.model.StatisticsScope;
import es.uclm.esibuy.statistics.repository.MongoStatisticsRepository;

/**
 * Estadísticas del vendedor de punta a punta: servicio y agregación reales sobre Mongo, con
 * productos y pedidos de dos vendedores que comparten carrito. Comprueba los productos sin
 * ventas y que un vendedor nunca ve los datos de otro (T24.4).
 */
// Necesita Docker: sin Docker estos tests se saltan en lugar de fallar
@DataMongoTest
@Testcontainers(disabledWithoutDocker = true)
@Import({ MongoStatisticsRepository.class, StatisticsService.class })
class StatisticsSellerSalesIntegrationTest {

    private static final String ANA = "vendedor-ana";
    private static final String BRUNO = "vendedor-bruno";

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @MockBean
    private GeneralConfigurationService configurationService;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StatisticsService statisticsService;

    @BeforeEach
    void setUp() {
        mongoTemplate.dropCollection("pedidos");
        mongoTemplate.dropCollection("products");
        mongoTemplate.dropCollection("users");
    }

    @Test
    @DisplayName("HU24-CP18: el vendedor ve sus productos sin ventas y nada de otro vendedor del mismo pedido")
    void shouldShowNeverSoldProductsAndNothingFromAnotherSellerInTheSameOrder() {
        product("ana-teclado", "Teclado", ANA);
        product("ana-raton", "Ratón", ANA);
        product("bruno-altavoz", "Altavoz", BRUNO);
        // Un mismo carrito con productos de los dos vendedores
        order("PAGADO", "ENTREGADO",
                line("ana-teclado", "Teclado", ANA, 2, "40.00"),
                line("bruno-altavoz", "Altavoz", BRUNO, 9, "900.00"));

        SalesStatisticsDTO sales = statisticsService.getSales(StatisticsScope.VENDEDOR, ANA);

        assertThat(sales.scope()).isEqualTo(StatisticsScope.VENDEDOR);
        assertThat(ids(sales.topSold())).containsExactly("ana-teclado");
        assertThat(ids(sales.leastSold())).containsExactly("ana-raton", "ana-teclado");
        ProductSalesDTO neverSold = sales.leastSold().get(0);
        assertThat(neverSold.productName()).isEqualTo("Ratón");
        assertThat(neverSold.unitsSold()).isZero();
        assertThat(neverSold.revenue()).isEqualByComparingTo("0.00");
        assertThat(sales.topSold().get(0).revenue()).isEqualByComparingTo("40.00");
        assertThat(all(sales)).allMatch(product -> ANA.equals(product.sellerId()));
    }

    @Test
    @DisplayName("HU24-CP19: con los mismos pedidos, cada vendedor ve solo sus ventas")
    void shouldGiveEachSellerOnlyItsOwnSalesFromTheSameOrders() {
        product("ana-teclado", "Teclado", ANA);
        product("bruno-altavoz", "Altavoz", BRUNO);
        product("bruno-cable", "Cable", BRUNO);
        order("PAGADO", "ENVIADO",
                line("ana-teclado", "Teclado", ANA, 1, "20.00"),
                line("bruno-altavoz", "Altavoz", BRUNO, 3, "300.00"));
        order("PAGADO", "PENDIENTE", line("bruno-altavoz", "Altavoz", BRUNO, 1, "100.00"));

        SalesStatisticsDTO ana = statisticsService.getSales(StatisticsScope.VENDEDOR, ANA);
        SalesStatisticsDTO bruno = statisticsService.getSales(StatisticsScope.VENDEDOR, BRUNO);

        assertThat(ids(all(ana))).containsOnly("ana-teclado");
        assertThat(ids(bruno.topSold())).containsExactly("bruno-altavoz");
        assertThat(bruno.topSold().get(0).unitsSold()).isEqualTo(4);
        assertThat(bruno.topSold().get(0).revenue()).isEqualByComparingTo("400.00");
        assertThat(ids(bruno.leastSold())).containsExactly("bruno-cable", "bruno-altavoz");
        assertThat(all(bruno)).allMatch(product -> BRUNO.equals(product.sellerId()));
    }

    @Test
    @DisplayName("HU24-CP20: una línea antigua sin vendedor cuenta solo para el dueño del producto")
    void shouldCountLegacyLinesWithoutSellerOnlyForTheProductOwner() {
        product("ana-teclado", "Teclado", ANA);
        product("bruno-altavoz", "Altavoz", BRUNO);
        order("PAGADO", "ENTREGADO",
                line("ana-teclado", "Teclado", null, 1, "20.00"),
                line("bruno-altavoz", "Altavoz", null, 5, "500.00"));

        SalesStatisticsDTO ana = statisticsService.getSales(StatisticsScope.VENDEDOR, ANA);
        SalesStatisticsDTO bruno = statisticsService.getSales(StatisticsScope.VENDEDOR, BRUNO);

        assertThat(ids(ana.topSold())).containsExactly("ana-teclado");
        assertThat(ana.topSold().get(0).unitsSold()).isEqualTo(1);
        assertThat(ids(bruno.topSold())).containsExactly("bruno-altavoz");
        assertThat(bruno.topSold().get(0).unitsSold()).isEqualTo(5);
    }

    @Test
    @DisplayName("HU24-CP21: las ventas de otro vendedor no pasan al nuevo dueño de un producto")
    void shouldNotGiveTheSalesOfAnotherSellerToTheNewOwnerOfAProduct() {
        // El producto es ahora de Ana, pero esa venta la hizo Bruno
        product("traspasado", "Lámpara", ANA);
        order("PAGADO", "ENTREGADO", line("traspasado", "Lámpara", BRUNO, 6, "60.00"));

        SalesStatisticsDTO ana = statisticsService.getSales(StatisticsScope.VENDEDOR, ANA);

        assertThat(ana.topSold()).isEmpty();
        assertThat(ids(ana.leastSold())).containsExactly("traspasado");
        assertThat(ana.leastSold().get(0).unitsSold()).isZero();
    }

    @Test
    @DisplayName("HU24-CP22: sin compras pagadas y no canceladas todos los productos salen sin ventas")
    void shouldListEveryProductAsNeverSoldWithoutPaidOrders() {
        product("ana-teclado", "Teclado", ANA);
        product("ana-raton", "Ratón", ANA);
        order("PENDIENTE", "PENDIENTE", line("ana-teclado", "Teclado", ANA, 3, "60.00"));
        order("PAGADO", "CANCELADO", line("ana-raton", "Ratón", ANA, 2, "30.00"));

        SalesStatisticsDTO ana = statisticsService.getSales(StatisticsScope.VENDEDOR, ANA);

        assertThat(ana.topSold()).isEmpty();
        assertThat(ids(ana.leastSold())).containsExactly("ana-raton", "ana-teclado");
        assertThat(ana.leastSold()).allMatch(product -> product.unitsSold() == 0);
    }

    @Test
    @DisplayName("HU24-CP23: con más de 10 productos sin ventas salen 10, siempre los mismos")
    void shouldReturnTheSameTenWhenMoreThanTenProductsWereNeverSold() {
        for (int i = 1; i <= 12; i++) {
            product("ana-p%02d".formatted(i), "Producto " + i, ANA);
        }
        product("bruno-p00", "De Bruno", BRUNO);

        SalesStatisticsDTO first = statisticsService.getSales(StatisticsScope.VENDEDOR, ANA);
        SalesStatisticsDTO second = statisticsService.getSales(StatisticsScope.VENDEDOR, ANA);

        assertThat(ids(first.leastSold())).containsExactly(
                "ana-p01", "ana-p02", "ana-p03", "ana-p04", "ana-p05",
                "ana-p06", "ana-p07", "ana-p08", "ana-p09", "ana-p10");
        assertThat(second.leastSold()).isEqualTo(first.leastSold());
        assertThat(first.topSold()).isEmpty();
    }

    @Test
    @DisplayName("HU24-CP24: el ranking de la plataforma sigue mostrando las ventas de todos los vendedores")
    void shouldKeepThePlatformRankingWithAllSellers() {
        product("ana-teclado", "Teclado", ANA);
        product("bruno-altavoz", "Altavoz", BRUNO);
        order("PAGADO", "ENTREGADO",
                line("ana-teclado", "Teclado", ANA, 1, "20.00"),
                line("bruno-altavoz", "Altavoz", BRUNO, 3, "300.00"));

        SalesStatisticsDTO platform = statisticsService.getSales(StatisticsScope.PLATAFORMA, null);

        assertThat(ids(platform.topSold())).containsExactly("bruno-altavoz", "ana-teclado");
        assertThat(platform.leastSold()).isEmpty();
    }

    private static List<ProductSalesDTO> all(SalesStatisticsDTO sales) {
        return Stream.concat(sales.topSold().stream(), sales.leastSold().stream()).toList();
    }

    private static List<String> ids(List<ProductSalesDTO> sales) {
        return sales.stream().map(ProductSalesDTO::productId).toList();
    }

    private void product(String id, String name, String sellerId) {
        Product product = new Product(new ProductDetails(
                name, List.of("categoria-1"), 5, "Descripción", new BigDecimal("10.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, null, true, sellerId));
        ReflectionTestUtils.setField(product, "id", id);
        productRepository.save(product);
    }

    private Document line(String productId, String name, String sellerId, int quantity, String subtotal) {
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
