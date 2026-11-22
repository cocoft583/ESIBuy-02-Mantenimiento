package es.uclm.esibuy.statistics.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.bson.Document;
import org.bson.types.ObjectId;
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
class MongoStatisticsRatingRepositoryTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private MongoStatisticsRepository repository;

    @BeforeEach
    void setUp() {
        mongoTemplate.dropCollection("products");
    }

    @Test
    @DisplayName("HU23-CP01: el ranking va de más a menos nota y desempata por valoraciones y por identificador")
    void shouldSortByRatingThenReviewsThenId() {
        product("p1", 4.0, 5);
        product("p2", 4.8, 3);
        product("p3", 4.0, 9);
        product("p4", 4.0, 5);
        product("p5", 5.0, 4);

        List<ProductRatingRow> ranking = repository.findTopRatedProducts(3, 10);

        assertThat(ranking).extracting(ProductRatingRow::productId)
                .containsExactly("p5", "p2", "p3", "p1", "p4");
    }

    @Test
    @DisplayName("HU23-CP02: un producto con menos valoraciones que el mínimo no entra, y con las justas sí")
    void shouldLeaveOutProductsBelowTheMinimumReviews() {
        product("p1", 5.0, 2);
        product("p2", 3.0, 3);
        product("p3", 4.0, 10);

        List<ProductRatingRow> ranking = repository.findTopRatedProducts(3, 10);

        assertThat(ranking).extracting(ProductRatingRow::productId).containsExactly("p3", "p2");
    }

    @Test
    @DisplayName("HU23-CP03: un producto sin valoraciones no entra aunque guarde una nota")
    void shouldLeaveOutProductsWithoutReviewsEvenIfTheyStoreARating() {
        product("p1", 4.0, 4);
        // Como en Atlas: nota 5 con reviewCount 0, y nota 5 sin el campo reviewCount
        product("p2", 5.0, 0);
        mongoTemplate.getCollection("products").insertOne(new Document("_id", "p3").append("rating", 5));
        mongoTemplate.getCollection("products").insertOne(new Document("_id", "p4").append("reviewCount", 6));

        List<ProductRatingRow> ranking = repository.findTopRatedProducts(1, 10);

        assertThat(ranking).extracting(ProductRatingRow::productId).containsExactly("p1");
    }

    @Test
    @DisplayName("HU23-CP04: un mínimo de 0 o menos se trata como 1: sin valoraciones nunca hay ranking")
    void shouldTreatAMinimumBelowOneAsOne() {
        product("p1", 4.0, 1);
        product("p2", 5.0, 0);

        List<ProductRatingRow> ranking = repository.findTopRatedProducts(0, 10);

        assertThat(ranking).extracting(ProductRatingRow::productId).containsExactly("p1");
    }

    @Test
    @DisplayName("HU23-CP05: el ranking se corta en el límite pedido")
    void shouldStopAtTheRequestedLimit() {
        for (int i = 1; i <= 12; i++) {
            product(String.format("p%02d", i), 3.0 + i / 10.0, 5);
        }

        List<ProductRatingRow> ranking = repository.findTopRatedProducts(3, 10);

        assertThat(ranking).hasSize(10);
        assertThat(ranking.get(0).productId()).isEqualTo("p12");
        assertThat(ranking.get(9).productId()).isEqualTo("p03");
    }

    @Test
    @DisplayName("HU23-CP06: lee la nota guardada como entero, el nombre, el vendedor y el identificador de Mongo")
    void shouldReadTheStoredFieldsOfTheProduct() {
        ObjectId id = new ObjectId();
        mongoTemplate.getCollection("products").insertOne(new Document("_id", id)
                .append("name", "Altavoz")
                .append("sellerId", "v1")
                .append("rating", 5)
                .append("reviewCount", 7));

        List<ProductRatingRow> ranking = repository.findTopRatedProducts(3, 10);

        assertThat(ranking).containsExactly(new ProductRatingRow(id.toHexString(), "Altavoz", "v1", 5.0, 7));
    }

    @Test
    @DisplayName("HU23-CP07: sin productos valorados el ranking está vacío")
    void shouldReturnAnEmptyRankingWhenNothingIsRated() {
        product("p1", 5.0, 0);

        assertThat(repository.findTopRatedProducts(3, 10)).isEmpty();
    }

    private void product(String id, double rating, int reviewCount) {
        mongoTemplate.getCollection("products").insertOne(new Document("_id", id)
                .append("name", "Producto " + id)
                .append("sellerId", "v1")
                .append("rating", rating)
                .append("reviewCount", reviewCount));
    }
}
