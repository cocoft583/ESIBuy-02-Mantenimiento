package es.uclm.esibuy.statistics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import es.uclm.esibuy.configuration.service.GeneralConfigurationService;
import es.uclm.esibuy.products.repository.ProductRepository;
import es.uclm.esibuy.statistics.dto.ProductRatingDTO;
import es.uclm.esibuy.statistics.dto.RatingStatisticsDTO;
import es.uclm.esibuy.statistics.dto.SellerRatingDTO;
import es.uclm.esibuy.statistics.repository.ProductRatingRow;
import es.uclm.esibuy.statistics.repository.SellerRatingRow;
import es.uclm.esibuy.statistics.repository.StatisticsRepository;
import es.uclm.esibuy.users.model.SellerProfile;
import es.uclm.esibuy.users.model.User;
import es.uclm.esibuy.users.model.UserRole;
import es.uclm.esibuy.users.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class StatisticsRatingsServiceTest {

    private static final int MINIMUM_REVIEWS = 5;
    private static final String SELLER_ONE = "v1";
    private static final String SELLER_TWO = "v2";

    @Mock
    private StatisticsRepository statisticsRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GeneralConfigurationService configurationService;

    private StatisticsService service;

    @BeforeEach
    void setUp() {
        service = new StatisticsService(statisticsRepository, productRepository, userRepository, configurationService);
        when(configurationService.getMinimumReviewsForRanking()).thenReturn(MINIMUM_REVIEWS);
    }

    private ProductRatingRow productRow(String id, String sellerId, double rating, int reviews) {
        return new ProductRatingRow(id, "Producto " + id, sellerId, rating, reviews);
    }

    private User seller(String firstName, String lastName, String businessName) {
        User seller = new User("vendedor@example.com", firstName, lastName, UserRole.SELLER);
        if (businessName != null) {
            seller.setSellerProfile(new SellerProfile(businessName, null));
        }
        return seller;
    }

    private void stubRankings(List<ProductRatingRow> products, List<SellerRatingRow> sellers, Double platform) {
        when(statisticsRepository.findTopRatedProducts(MINIMUM_REVIEWS, 10)).thenReturn(products);
        when(statisticsRepository.findTopRatedSellers(10)).thenReturn(sellers);
        when(statisticsRepository.findPlatformAverageRating()).thenReturn(platform);
    }

    @Test
    @DisplayName("HU23-CP20: pide los 10 primeros con el mínimo de valoraciones de la configuración y lo devuelve")
    void shouldAskForTheTopTenWithTheConfiguredMinimum() {
        stubRankings(List.of(), List.of(), null);

        RatingStatisticsDTO result = service.getRatingStatistics();

        verify(statisticsRepository).findTopRatedProducts(MINIMUM_REVIEWS, 10);
        verify(statisticsRepository).findTopRatedSellers(10);
        assertThat(result.minimumReviews()).isEqualTo(MINIMUM_REVIEWS);
    }

    @Test
    @DisplayName("HU23-CP21: cada fila lleva su nota, sus valoraciones y el nombre del vendedor, en el orden del repositorio")
    void shouldKeepTheOrderAndShowTheSellerOfEachProduct() {
        stubRankings(
                List.of(productRow("p1", SELLER_ONE, 4.9, 12), productRow("p2", SELLER_TWO, 4.5, 6)),
                List.of(),
                null);
        when(userRepository.findById(SELLER_ONE)).thenReturn(Optional.of(seller("Ana", "López", "Sonido SL")));
        when(userRepository.findById(SELLER_TWO)).thenReturn(Optional.of(seller("Luis", "Pérez", null)));

        List<ProductRatingDTO> products = service.getRatingStatistics().topProducts();

        assertThat(products).extracting(ProductRatingDTO::productId).containsExactly("p1", "p2");
        assertThat(products.get(0)).isEqualTo(new ProductRatingDTO("p1", "Producto p1", SELLER_ONE, "Sonido SL", 4.9, 12));
        assertThat(products.get(1).sellerName()).isEqualTo("Luis Pérez");
    }

    @Test
    @DisplayName("HU23-CP22: las notas se redondean a dos decimales")
    void shouldRoundRatingsToTwoDecimals() {
        stubRankings(
                List.of(productRow("p1", null, 4.666666, 9), productRow("p2", null, 4.125, 9)),
                List.of(new SellerRatingRow(SELLER_ONE, 3.3333333, 3)),
                4.0049);
        when(userRepository.findById(SELLER_ONE)).thenReturn(Optional.of(seller("Ana", "López", "Sonido SL")));

        RatingStatisticsDTO result = service.getRatingStatistics();

        assertThat(result.topProducts()).extracting(ProductRatingDTO::rating).containsExactly(4.67, 4.13);
        assertThat(result.topSellers().get(0).averageRating()).isEqualTo(3.33);
        assertThat(result.platformAverage()).isEqualTo(4.0);
    }

    @Test
    @DisplayName("HU23-CP23: el vendedor del ranking lleva su valoración media y los productos que la forman")
    void shouldShowTheSellerAverageAndItsRatedProducts() {
        stubRankings(List.of(), List.of(new SellerRatingRow(SELLER_ONE, 4.5, 3), new SellerRatingRow(SELLER_TWO, 4.0, 1)), 4.25);
        when(userRepository.findById(SELLER_ONE)).thenReturn(Optional.of(seller("Ana", "López", "Sonido SL")));
        when(userRepository.findById(SELLER_TWO)).thenReturn(Optional.of(seller("Luis", "Pérez", null)));

        RatingStatisticsDTO result = service.getRatingStatistics();

        assertThat(result.topSellers()).containsExactly(
                new SellerRatingDTO(SELLER_ONE, "Sonido SL", 4.5, 3),
                new SellerRatingDTO(SELLER_TWO, "Luis Pérez", 4.0, 1));
        assertThat(result.platformAverage()).isEqualTo(4.25);
    }

    @Test
    @DisplayName("HU23-CP24: un vendedor que sale en los dos rankings se busca una sola vez")
    void shouldLookUpASellerThatAppearsInBothRankingsOnlyOnce() {
        stubRankings(
                List.of(productRow("p1", SELLER_ONE, 4.9, 12), productRow("p2", SELLER_ONE, 4.5, 6)),
                List.of(new SellerRatingRow(SELLER_ONE, 4.7, 2)),
                4.7);
        when(userRepository.findById(SELLER_ONE)).thenReturn(Optional.of(seller("Ana", "López", "Sonido SL")));

        service.getRatingStatistics();

        verify(userRepository, times(1)).findById(SELLER_ONE);
    }

    @Test
    @DisplayName("HU23-CP25: sin nombre comercial sale el nombre completo, y si el vendedor ya no existe, su identificador")
    void shouldFallBackToTheFullNameAndThenToTheId() {
        stubRankings(
                List.of(),
                List.of(new SellerRatingRow(SELLER_ONE, 4.5, 2), new SellerRatingRow("borrado", 4.0, 1)),
                4.25);
        when(userRepository.findById(SELLER_ONE)).thenReturn(Optional.of(seller("Ana", "López", null)));
        when(userRepository.findById("borrado")).thenReturn(Optional.empty());

        List<SellerRatingDTO> sellers = service.getRatingStatistics().topSellers();

        assertThat(sellers).extracting(SellerRatingDTO::sellerName).containsExactly("Ana López", "Vendedor borrado");
    }

    @Test
    @DisplayName("HU23-CP26: un producto sin vendedor sale sin nombre y no se consulta a ningún usuario")
    void shouldShowAProductWithoutSellerWithoutLookingUpUsers() {
        stubRankings(List.of(productRow("p1", null, 4.9, 12)), List.of(), null);

        ProductRatingDTO product = service.getRatingStatistics().topProducts().get(0);

        assertThat(product.sellerId()).isNull();
        assertThat(product.sellerName()).isNull();
        verify(userRepository, never()).findById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("HU23-CP27: sin valoraciones vienen listas vacías, la media de la plataforma a null y no se consulta a nadie")
    void shouldReturnEmptyRankingsAndNoPlatformAverageWhenNothingIsRated() {
        stubRankings(List.of(), List.of(), null);

        RatingStatisticsDTO result = service.getRatingStatistics();

        assertThat(result.topProducts()).isEmpty();
        assertThat(result.topSellers()).isEmpty();
        assertThat(result.platformAverage()).isNull();
        assertThat(result.minimumReviews()).isEqualTo(MINIMUM_REVIEWS);
        verifyNoInteractions(userRepository, productRepository);
    }
}
