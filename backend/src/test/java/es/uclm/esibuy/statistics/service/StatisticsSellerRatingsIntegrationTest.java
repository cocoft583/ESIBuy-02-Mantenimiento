package es.uclm.esibuy.statistics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

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
import es.uclm.esibuy.statistics.dto.ProductRatingDTO;
import es.uclm.esibuy.statistics.dto.RatingStatisticsDTO;
import es.uclm.esibuy.statistics.dto.SellerRatingDTO;
import es.uclm.esibuy.statistics.dto.SellerRatingStatisticsDTO;
import es.uclm.esibuy.statistics.repository.MongoStatisticsRepository;

/**
 * Valoraciones del vendedor de punta a punta: servicio y agregación reales sobre Mongo, con las
 * valoraciones puestas como las pone la aplicación. Comprueba que la media que ve el vendedor es
 * la misma que ve el administrador y que nunca ve productos de otro vendedor (T25.4).
 */
// Necesita Docker: sin Docker estos tests se saltan en lugar de fallar
@DataMongoTest
@Testcontainers(disabledWithoutDocker = true)
@Import({ MongoStatisticsRepository.class, StatisticsService.class })
class StatisticsSellerRatingsIntegrationTest {

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
        mongoTemplate.dropCollection("products");
        mongoTemplate.dropCollection("users");
        when(configurationService.getMinimumReviewsForRanking()).thenReturn(1);

        // Ana: teclado (5, 5, 4 → 4,67), ratón (2 → 2) y una alfombrilla sin valoraciones.
        // La media de las medias es (4,67 + 2) / 2 = 3,33; la de sus 4 reseñas sería 4
        product("ana-teclado", "Teclado", ANA, 5, 5, 4);
        product("ana-raton", "Ratón", ANA, 2);
        product("ana-alfombrilla", "Alfombrilla", ANA);
        // Bruno: altavoz (5) y cascos (3, 4 → 3,5)
        product("bruno-altavoz", "Altavoz", BRUNO, 5);
        product("bruno-cascos", "Cascos", BRUNO, 3, 4);
    }

    @Test
    @DisplayName("HU25-CP14: la valoración media del vendedor es la misma que ve el administrador")
    void shouldGiveEachSellerTheSameAverageTheAdminSees() {
        RatingStatisticsDTO admin = statisticsService.getRatingStatistics();

        for (String sellerId : List.of(ANA, BRUNO)) {
            SellerRatingStatisticsDTO own = statisticsService.getSellerRatings(sellerId);
            SellerRatingDTO adminRow = admin.topSellers().stream()
                    .filter(seller -> seller.sellerId().equals(sellerId))
                    .findFirst()
                    .orElseThrow();

            assertThat(own.averageRating()).isEqualTo(adminRow.averageRating());
            assertThat(own.ratedProducts()).isEqualTo(adminRow.ratedProducts());
            assertThat(own.platformAverage()).isEqualTo(admin.platformAverage());
        }

        assertThat(statisticsService.getSellerRatings(ANA).averageRating()).isEqualTo(3.33);
    }

    @Test
    @DisplayName("HU25-CP15: el vendedor solo ve sus productos valorados, de mejor a peor y de peor a mejor")
    void shouldShowOnlyTheSellerRatedProductsInBothOrders() {
        SellerRatingStatisticsDTO ana = statisticsService.getSellerRatings(ANA);

        assertThat(ana.topRated()).extracting(ProductRatingDTO::productId)
                .containsExactly("ana-teclado", "ana-raton");
        assertThat(ana.worstRated()).extracting(ProductRatingDTO::productId)
                .containsExactly("ana-raton", "ana-teclado");
        assertThat(ana.topRated()).extracting(ProductRatingDTO::sellerId).containsOnly(ANA);
        assertThat(ana.topRated().get(0).rating()).isEqualTo(4.67);
        assertThat(ana.topRated().get(0).reviewCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("HU25-CP16: un vendedor sin valoraciones no tiene media, pero ve la de la plataforma")
    void shouldShowThePlatformAverageToASellerWithoutRatings() {
        product("carla-lampara", "Lámpara", "vendedor-carla");

        SellerRatingStatisticsDTO carla = statisticsService.getSellerRatings("vendedor-carla");

        assertThat(carla.topRated()).isEmpty();
        assertThat(carla.worstRated()).isEmpty();
        assertThat(carla.averageRating()).isNull();
        assertThat(carla.ratedProducts()).isZero();
        assertThat(carla.platformAverage()).isEqualTo(statisticsService.getRatingStatistics().platformAverage());
    }

    private void product(String id, String name, String sellerId, int... ratings) {
        Product product = new Product(new ProductDetails(
                name, List.of("categoria-1"), 5, "Descripción", new BigDecimal("10.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, null, true, sellerId));
        ReflectionTestUtils.setField(product, "id", id);
        for (int rating : ratings) {
            product.addRating(rating);
        }
        productRepository.save(product);
    }
}
