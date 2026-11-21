package es.uclm.esibuy.statistics.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.bson.Document;
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

// Pedidos antiguos o editados a mano: el ranking no debe fallar por ellos.
// Necesita Docker: sin Docker estos tests se saltan en lugar de fallar
@DataMongoTest
@Testcontainers(disabledWithoutDocker = true)
@Import(MongoStatisticsRepository.class)
class MongoStatisticsRepositoryLegacyDataTest {

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
    @DisplayName("HU22-CP19: una línea sin producto se ignora y no crea una fila con identificador nulo")
    void shouldIgnoreLinesWithoutAProduct() {
        order(
                line("p1", "Auriculares", "v1", 2, "20.00"),
                // El identificador ausente, nulo o vacío no se puede atribuir a ningún producto
                new Document("nombre", "Sin identificador").append("cantidad", 9).append("subtotal", "90.00"),
                line(null, "Identificador nulo", null, 9, "90.00"),
                line("", "Identificador vacío", null, 9, "90.00"));

        List<ProductSalesRow> ranking = repository.findTopSellingProducts(10);

        assertThat(ranking).extracting(ProductSalesRow::productId).containsExactly("p1");
    }

    @Test
    @DisplayName("HU22-CP20: un importe que no es un número cuenta 0 y no rompe el ranking")
    void shouldCountANonNumericAmountAsZero() {
        order(line("p1", "Auriculares", "v1", 2, "N/A"));
        order(line("p1", "Auriculares", "v1", 3, "30.00"));
        order(line("p2", "Altavoz", "v2", 1, "80.00"));

        List<ProductSalesRow> ranking = repository.findTopSellingProducts(10);

        assertThat(ranking).extracting(ProductSalesRow::productId).containsExactly("p1", "p2");
        // Las unidades se cuentan igual; del importe solo suma la línea válida
        assertThat(ranking.get(0).unitsSold()).isEqualTo(5);
        assertThat(ranking.get(0).revenue()).isEqualByComparingTo("30.00");
    }

    @Test
    @DisplayName("HU22-CP21: un importe ausente o nulo cuenta 0")
    void shouldCountAMissingOrNullAmountAsZero() {
        order(
                line("p1", "Auriculares", "v1", 1, null),
                new Document("productoId", "p1").append("nombre", "Auriculares").append("cantidad", 1),
                line("p1", "Auriculares", "v1", 1, "10.00"));

        List<ProductSalesRow> ranking = repository.findTopSellingProducts(10);

        assertThat(ranking.get(0).unitsSold()).isEqualTo(3);
        assertThat(ranking.get(0).revenue()).isEqualByComparingTo("10.00");
    }

    private Document line(String productId, String name, String sellerId, int quantity, Object subtotal) {
        return new Document("productoId", productId)
                .append("nombre", name)
                .append("cantidad", quantity)
                .append("subtotal", subtotal)
                .append("vendedorId", sellerId);
    }

    private void order(Document... lines) {
        mongoTemplate.getCollection("pedidos").insertOne(new Document("clienteId", "cliente-1")
                .append("estadoPago", "PAGADO")
                .append("estado", "ENVIADO")
                .append("items", List.of(lines)));
    }
}
