package es.uclm.esibuy.statistics.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import es.uclm.esibuy.products.model.Product;
import es.uclm.esibuy.products.model.ProductDetails;
import es.uclm.esibuy.products.repository.ProductRepository;
import es.uclm.esibuy.statistics.dto.ProductSalesDTO;
import es.uclm.esibuy.statistics.repository.ProductSalesRow;
import es.uclm.esibuy.statistics.repository.StatisticsRepository;
import es.uclm.esibuy.users.model.SellerProfile;
import es.uclm.esibuy.users.model.User;
import es.uclm.esibuy.users.model.UserRole;
import es.uclm.esibuy.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceTest {

    @Mock
    private StatisticsRepository statisticsRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    private StatisticsService service;

    @BeforeEach
    void setUp() {
        service = new StatisticsService(statisticsRepository, productRepository, userRepository);
    }

    private ProductSalesRow row(String productId, String sellerId, long units, String revenue) {
        return new ProductSalesRow(productId, "Producto " + productId, sellerId, units, new BigDecimal(revenue));
    }

    private User seller(String firstName, String lastName, String businessName) {
        User seller = new User("vendedor@example.com", firstName, lastName, UserRole.SELLER);
        if (businessName != null) {
            seller.setSellerProfile(new SellerProfile(businessName, null));
        }
        return seller;
    }

    private Product productOf(String sellerId) {
        return new Product(new ProductDetails(
                "Producto", List.of("categoria-1"), 5, "Descripción",
                new BigDecimal("10.00"), null, null, null, true, sellerId));
    }

    @Test
    @DisplayName("HU22-CP08: el ranking pide siempre los 10 primeros")
    void shouldAskTheRepositoryForTheTopTen() {
        when(statisticsRepository.findTopSellingProducts(10)).thenReturn(List.of());

        assertTrue(service.getTopSellingProducts().isEmpty());

        verify(statisticsRepository).findTopSellingProducts(10);
        verifyNoInteractions(productRepository, userRepository);
    }

    @Test
    @DisplayName("HU22-CP09: cada fila lleva sus unidades, sus ingresos y el nombre comercial del vendedor")
    void shouldShowUnitsRevenueAndTheBusinessNameOfTheSeller() {
        when(statisticsRepository.findTopSellingProducts(10)).thenReturn(List.of(row("p1", "v1", 7, "140.50")));
        when(userRepository.findById("v1")).thenReturn(Optional.of(seller("Ana", "López", "Sonido SL")));

        ProductSalesDTO result = service.getTopSellingProducts().get(0);

        assertEquals("p1", result.productId());
        assertEquals("Producto p1", result.productName());
        assertEquals("v1", result.sellerId());
        assertEquals("Sonido SL", result.sellerName());
        assertEquals(7, result.unitsSold());
        assertEquals(new BigDecimal("140.50"), result.revenue());
    }

    @Test
    @DisplayName("HU22-CP10: sin nombre comercial se muestra el nombre y los apellidos del vendedor")
    void shouldFallBackToTheFullNameWhenThereIsNoBusinessName() {
        when(statisticsRepository.findTopSellingProducts(10)).thenReturn(List.of(row("p1", "v1", 1, "10.00")));
        when(userRepository.findById("v1")).thenReturn(Optional.of(seller("Ana", "López", null)));

        assertEquals("Ana López", service.getTopSellingProducts().get(0).sellerName());
    }

    @Test
    @DisplayName("HU22-CP11: si el vendedor ya no existe se muestra su identificador")
    void shouldShowTheSellerIdWhenTheSellerNoLongerExists() {
        when(statisticsRepository.findTopSellingProducts(10)).thenReturn(List.of(row("p1", "v1", 1, "10.00")));
        when(userRepository.findById("v1")).thenReturn(Optional.empty());

        ProductSalesDTO result = service.getTopSellingProducts().get(0);

        assertEquals("v1", result.sellerId());
        assertEquals("Vendedor v1", result.sellerName());
    }

    @Test
    @DisplayName("HU22-CP12: si la línea del pedido no guarda el vendedor se toma el del producto")
    void shouldTakeTheSellerFromTheProductWhenTheOrderLineHasNone() {
        when(statisticsRepository.findTopSellingProducts(10)).thenReturn(List.of(row("p1", null, 3, "30.00")));
        when(productRepository.findById("p1")).thenReturn(Optional.of(productOf("v9")));
        when(userRepository.findById("v9")).thenReturn(Optional.of(seller("Luis", "Martín", "Hogar SA")));

        ProductSalesDTO result = service.getTopSellingProducts().get(0);

        assertEquals("v9", result.sellerId());
        assertEquals("Hogar SA", result.sellerName());
    }

    @Test
    @DisplayName("HU22-CP13: si tampoco queda el producto, el vendedor queda sin informar")
    void shouldLeaveTheSellerEmptyWhenNeitherTheLineNorTheProductKnowIt() {
        when(statisticsRepository.findTopSellingProducts(10)).thenReturn(List.of(
                row("p1", null, 3, "30.00"),
                row("p2", "  ", 2, "20.00")));
        when(productRepository.findById("p1")).thenReturn(Optional.empty());
        when(productRepository.findById("p2")).thenReturn(Optional.of(productOf(null)));

        List<ProductSalesDTO> result = service.getTopSellingProducts();

        assertNull(result.get(0).sellerId());
        assertNull(result.get(0).sellerName());
        assertNull(result.get(1).sellerId());
        assertNull(result.get(1).sellerName());
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("HU22-CP14: el nombre de un vendedor con varios productos se busca una sola vez")
    void shouldLookUpEachSellerOnlyOnce() {
        when(statisticsRepository.findTopSellingProducts(10)).thenReturn(List.of(
                row("p1", "v1", 5, "50.00"),
                row("p2", "v1", 4, "40.00"),
                row("p3", "v2", 3, "30.00")));
        when(userRepository.findById("v1")).thenReturn(Optional.of(seller("Ana", "López", "Sonido SL")));
        when(userRepository.findById("v2")).thenReturn(Optional.of(seller("Luis", "Martín", "Hogar SA")));

        List<ProductSalesDTO> result = service.getTopSellingProducts();

        assertEquals(List.of("p1", "p2", "p3"), result.stream().map(ProductSalesDTO::productId).toList());
        assertEquals(List.of("Sonido SL", "Sonido SL", "Hogar SA"),
                result.stream().map(ProductSalesDTO::sellerName).toList());
        verify(userRepository, times(1)).findById("v1");
        verify(userRepository, times(1)).findById("v2");
    }
}
