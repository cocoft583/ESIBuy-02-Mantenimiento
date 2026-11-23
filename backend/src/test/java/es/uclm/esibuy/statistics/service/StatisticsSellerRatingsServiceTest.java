package es.uclm.esibuy.statistics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
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
import es.uclm.esibuy.statistics.dto.SellerRatingStatisticsDTO;
import es.uclm.esibuy.statistics.repository.ProductRatingRow;
import es.uclm.esibuy.statistics.repository.SellerRatingRow;
import es.uclm.esibuy.statistics.repository.StatisticsRepository;
import es.uclm.esibuy.users.model.User;
import es.uclm.esibuy.users.model.UserRole;
import es.uclm.esibuy.users.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class StatisticsSellerRatingsServiceTest {

    private static final String SELLER_ID = "vendedor-1";

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
    }

    @Test
    @DisplayName("HU25-CP04: los mejor valorados van de más a menos nota, con más valoraciones primero si empatan, y son 10 como mucho")
    void shouldSortTheBestRatedAndKeepTenOfThem() {
        List<ProductRatingRow> rows = new ArrayList<>();
        // Notas múltiplo de 0,25 para que no haya errores de redondeo: de 1,25 (p01) a 3,75 (p11)
        for (int i = 1; i <= 11; i++) {
            rows.add(row("p" + String.format("%02d", i), 1.0 + i * 0.25, 2));
        }
        rows.add(row("empate-pocas", 3.75, 1));
        rows.add(row("empate-muchas", 3.75, 9));
        stubSeller(rows, null, null);

        SellerRatingStatisticsDTO statistics = service.getSellerRatings(SELLER_ID);

        assertThat(statistics.topRated()).hasSize(10);
        assertThat(statistics.topRated()).extracting(ProductRatingDTO::productId)
                .startsWith("empate-muchas", "p11", "empate-pocas", "p10");
    }

    @Test
    @DisplayName("HU25-CP05: los peor valorados van de menos a más nota, con más valoraciones primero si empatan")
    void shouldSortTheWorstRatedFromTheLowestRating() {
        stubSeller(List.of(
                row("bueno", 4.8, 5),
                row("malo-pocas", 1.5, 1),
                row("malo-muchas", 1.5, 7),
                row("regular", 3.0, 2)), null, null);

        SellerRatingStatisticsDTO statistics = service.getSellerRatings(SELLER_ID);

        assertThat(statistics.worstRated()).extracting(ProductRatingDTO::productId)
                .containsExactly("malo-muchas", "malo-pocas", "regular", "bueno");
        assertThat(statistics.topRated()).extracting(ProductRatingDTO::productId)
                .containsExactly("bueno", "regular", "malo-muchas", "malo-pocas");
    }

    @Test
    @DisplayName("HU25-CP06: la media del vendedor y la de la plataforma se redondean a dos decimales")
    void shouldRoundTheSellerAndPlatformAverages() {
        stubSeller(List.of(row("p1", 4.666666, 3)), new SellerRatingRow(SELLER_ID, 4.666666, 1), 3.876543);

        SellerRatingStatisticsDTO statistics = service.getSellerRatings(SELLER_ID);

        assertThat(statistics.averageRating()).isEqualTo(4.67);
        assertThat(statistics.ratedProducts()).isEqualTo(1);
        assertThat(statistics.platformAverage()).isEqualTo(3.88);
        assertThat(statistics.topRated().get(0).rating()).isEqualTo(4.67);
    }

    @Test
    @DisplayName("HU25-CP07: cada producto lleva el nombre del vendedor, que se busca una sola vez")
    void shouldShowTheSellerNameLookingItUpOnce() {
        User seller = new User("ana@example.com", "Ana", "López", UserRole.SELLER);
        when(userRepository.findById(SELLER_ID)).thenReturn(Optional.of(seller));
        stubSeller(List.of(row("p1", 4.0, 2), row("p2", 3.0, 2)), new SellerRatingRow(SELLER_ID, 3.5, 2), 3.5);

        SellerRatingStatisticsDTO statistics = service.getSellerRatings(SELLER_ID);

        assertThat(statistics.topRated()).extracting(ProductRatingDTO::sellerName).containsOnly("Ana López");
        verify(userRepository, times(1)).findById(SELLER_ID);
    }

    @Test
    @DisplayName("HU25-CP08: un vendedor sin valoraciones recibe los rankings vacíos y sin media")
    void shouldReturnEmptyRankingsWithoutRatings() {
        stubSeller(List.of(), null, 4.2);

        SellerRatingStatisticsDTO statistics = service.getSellerRatings(SELLER_ID);

        assertThat(statistics.topRated()).isEmpty();
        assertThat(statistics.worstRated()).isEmpty();
        assertThat(statistics.averageRating()).isNull();
        assertThat(statistics.ratedProducts()).isZero();
        assertThat(statistics.platformAverage()).isEqualTo(4.2);
    }

    @Test
    @DisplayName("HU25-CP09: sin identificador de vendedor no se calcula nada")
    void shouldRejectAMissingSeller() {
        assertThatThrownBy(() -> service.getSellerRatings(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.getSellerRatings(null))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(statisticsRepository);
    }

    private void stubSeller(List<ProductRatingRow> products, SellerRatingRow average, Double platformAverage) {
        when(statisticsRepository.findSellerRatedProducts(SELLER_ID)).thenReturn(products);
        when(statisticsRepository.findSellerRating(SELLER_ID)).thenReturn(average);
        when(statisticsRepository.findPlatformAverageRating()).thenReturn(platformAverage);
    }

    private ProductRatingRow row(String productId, double rating, int reviewCount) {
        return new ProductRatingRow(productId, "Producto " + productId, SELLER_ID, rating, reviewCount);
    }
}
