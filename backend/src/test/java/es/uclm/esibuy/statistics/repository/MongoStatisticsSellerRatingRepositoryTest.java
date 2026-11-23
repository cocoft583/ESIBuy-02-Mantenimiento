package es.uclm.esibuy.statistics.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

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

// Necesita Docker: sin Docker estos tests se saltan en lugar de fallar
@DataMongoTest
@Testcontainers(disabledWithoutDocker = true)
@Import(MongoStatisticsRepository.class)
class MongoStatisticsSellerRatingRepositoryTest {

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
    @DisplayName("HU23-CP08: la valoración del vendedor es la media de las medias de sus productos, no de las reseñas")
    void shouldAverageTheProductAveragesAndNotTheReviews() {
        // Si se promediaran las 101 reseñas saldría casi 5; la media de las medias es (5 + 3) / 2
        product("p1", "v1", 5.0, 100);
        product("p2", "v1", 3.0, 1);

        List<SellerRatingRow> ranking = repository.findTopRatedSellers(10);

        assertThat(ranking).hasSize(1);
        assertThat(ranking.get(0).sellerId()).isEqualTo("v1");
        assertThat(ranking.get(0).averageRating()).isCloseTo(4.0, within(0.0001));
        assertThat(ranking.get(0).ratedProducts()).isEqualTo(2);
    }

    @Test
    @DisplayName("HU23-CP09: el ranking va de más a menos media y desempata por productos valorados y por identificador")
    void shouldSortByAverageThenRatedProductsThenId() {
        product("a1", "v1", 4.0, 2);
        product("b1", "v2", 5.0, 2);
        product("c1", "v3", 4.0, 2);
        product("c2", "v3", 4.0, 2);
        product("d1", "v4", 4.0, 2);

        List<SellerRatingRow> ranking = repository.findTopRatedSellers(10);

        assertThat(ranking).extracting(SellerRatingRow::sellerId).containsExactly("v2", "v3", "v1", "v4");
    }

    @Test
    @DisplayName("HU23-CP10: un producto sin valoraciones no cuenta aunque guarde una nota")
    void shouldIgnoreProductsWithoutReviewsEvenIfTheyStoreARating() {
        product("p1", "v1", 4.0, 3);
        product("p2", "v1", 5.0, 0);
        mongoTemplate.getCollection("products").insertOne(
                new Document("_id", "p3").append("sellerId", "v1").append("rating", 5));

        List<SellerRatingRow> ranking = repository.findTopRatedSellers(10);

        assertThat(ranking).hasSize(1);
        assertThat(ranking.get(0).averageRating()).isCloseTo(4.0, within(0.0001));
        assertThat(ranking.get(0).ratedProducts()).isEqualTo(1);
    }

    @Test
    @DisplayName("HU23-CP11: un producto sin vendedor no se atribuye a nadie")
    void shouldIgnoreProductsWithoutASeller() {
        product("p1", "v1", 4.0, 3);
        product("p2", null, 5.0, 3);
        product("p3", "", 5.0, 3);
        mongoTemplate.getCollection("products").insertOne(
                new Document("_id", "p4").append("rating", 5.0).append("reviewCount", 3));

        List<SellerRatingRow> ranking = repository.findTopRatedSellers(10);

        assertThat(ranking).extracting(SellerRatingRow::sellerId).containsExactly("v1");
        assertThat(repository.findPlatformAverageRating()).isCloseTo(4.0, within(0.0001));
    }

    @Test
    @DisplayName("HU23-CP12: el ranking se corta en el límite pedido")
    void shouldStopAtTheRequestedLimit() {
        for (int i = 1; i <= 12; i++) {
            product("p" + i, String.format("v%02d", i), 3.0 + i / 10.0, 2);
        }

        List<SellerRatingRow> ranking = repository.findTopRatedSellers(10);

        assertThat(ranking).hasSize(10);
        assertThat(ranking.get(0).sellerId()).isEqualTo("v12");
        assertThat(ranking.get(9).sellerId()).isEqualTo("v03");
    }

    @Test
    @DisplayName("HU23-CP13: la media de la plataforma es la media de las medias de los vendedores")
    void shouldAverageTheSellerAveragesForThePlatform() {
        // Vendedor v1: (5 + 3) / 2 = 4. Vendedor v2: 5. Plataforma: (4 + 5) / 2 = 4,5.
        // La media de los tres productos sería 4,33
        product("p1", "v1", 5.0, 10);
        product("p2", "v1", 3.0, 10);
        product("p3", "v2", 5.0, 10);

        assertThat(repository.findPlatformAverageRating()).isCloseTo(4.5, within(0.0001));
    }

    @Test
    @DisplayName("HU23-CP14: sin productos valorados no hay ranking ni media de la plataforma")
    void shouldReturnNothingWhenNoProductIsRated() {
        product("p1", "v1", 5.0, 0);

        assertThat(repository.findTopRatedSellers(10)).isEmpty();
        assertThat(repository.findPlatformAverageRating()).isNull();
    }

    @Test
    @DisplayName("HU23-CP15: la nota guardada como entero se lee igual que la decimal")
    void shouldReadRatingsStoredAsIntegers() {
        mongoTemplate.getCollection("products").insertOne(new Document("_id", "p1")
                .append("sellerId", "v1").append("rating", 5).append("reviewCount", 2));
        product("p2", "v1", 4.0, 2);

        assertThat(repository.findTopRatedSellers(10).get(0).averageRating()).isCloseTo(4.5, within(0.0001));
    }

    @Test
    @DisplayName("HU25-CP01: los productos valorados del vendedor son solo los suyos con alguna valoración")
    void shouldReturnOnlyTheRatedProductsOfTheSeller() {
        product("p1", "v1", 4.5, 3);
        product("p2", "v1", 2.0, 1);
        product("p3", "v1", 5.0, 0);
        product("p4", "v2", 5.0, 9);
        mongoTemplate.getCollection("products").insertOne(
                new Document("_id", "p5").append("sellerId", "v1").append("rating", 5));

        List<ProductRatingRow> products = repository.findSellerRatedProducts("v1");

        assertThat(products).extracting(ProductRatingRow::productId).containsExactlyInAnyOrder("p1", "p2");
        assertThat(products).allSatisfy(product -> assertThat(product.sellerId()).isEqualTo("v1"));
    }

    @Test
    @DisplayName("HU25-CP02: la valoración media del vendedor es la misma que la de su fila en el ranking del administrador")
    void shouldGiveTheSellerTheSameAverageAsTheAdminRanking() {
        product("p1", "v1", 5.0, 100);
        product("p2", "v1", 3.0, 1);
        product("p3", "v1", 4.0, 0);
        product("p4", "v2", 1.0, 5);

        SellerRatingRow own = repository.findSellerRating("v1");
        SellerRatingRow inAdminRanking = repository.findTopRatedSellers(10).stream()
                .filter(row -> row.sellerId().equals("v1"))
                .findFirst()
                .orElseThrow();

        assertThat(own).isEqualTo(inAdminRanking);
        assertThat(own.averageRating()).isCloseTo(4.0, within(0.0001));
        assertThat(own.ratedProducts()).isEqualTo(2);
    }

    @Test
    @DisplayName("HU25-CP03: un vendedor sin productos valorados no tiene valoración media")
    void shouldReturnNoAverageWhenTheSellerHasNoRatedProducts() {
        product("p1", "v1", 5.0, 0);
        product("p2", "v2", 4.0, 2);

        assertThat(repository.findSellerRating("v1")).isNull();
        assertThat(repository.findSellerRatedProducts("v1")).isEmpty();
    }

    private void product(String id, String sellerId, double rating, int reviewCount) {
        // Un vendedor null se guarda como null explícito; el campo ausente se prueba aparte
        mongoTemplate.getCollection("products").insertOne(new Document("_id", id)
                .append("sellerId", sellerId)
                .append("rating", rating)
                .append("reviewCount", reviewCount));
    }
}
