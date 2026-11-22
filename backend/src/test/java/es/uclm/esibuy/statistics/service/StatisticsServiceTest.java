package es.uclm.esibuy.statistics.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import es.uclm.esibuy.configuration.service.GeneralConfigurationService;
import es.uclm.esibuy.products.model.Product;
import es.uclm.esibuy.products.model.ProductDetails;
import es.uclm.esibuy.products.repository.ProductRepository;
import es.uclm.esibuy.statistics.dto.ProductSalesDTO;
import es.uclm.esibuy.statistics.dto.SalesStatisticsDTO;
import es.uclm.esibuy.statistics.model.StatisticsScope;
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
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
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

    @Mock
    private GeneralConfigurationService configurationService;

    private StatisticsService service;

    @BeforeEach
    void setUp() {
        service = new StatisticsService(statisticsRepository, productRepository, userRepository, configurationService);
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

    private Product productOf(String id, String name, String sellerId) {
        Product product = new Product(new ProductDetails(
                name, List.of("categoria-1"), 5, "Descripción",
                new BigDecimal("10.00"), null, null, null, true, sellerId));
        ReflectionTestUtils.setField(product, "id", id);
        return product;
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
        when(productRepository.findAllById(List.of("p1"))).thenReturn(List.of(productOf("p1", "Producto", "v9")));
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
        // El p1 ya no existe y el p2 existe pero sin vendedor
        when(productRepository.findAllById(List.of("p1", "p2"))).thenReturn(List.of(productOf("p2", "Producto", null)));

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

    @Test
    @DisplayName("HU22-CP22: si el producto se renombró, el ranking muestra su nombre actual")
    void shouldShowTheCurrentNameWhenTheProductWasRenamed() {
        when(statisticsRepository.findTopSellingProducts(10)).thenReturn(List.of(
                new ProductSalesRow("p1", "Auriculares antiguos", "v1", 5, new BigDecimal("50.00"))));
        when(productRepository.findAllById(List.of("p1")))
                .thenReturn(List.of(productOf("p1", "Auriculares Pro", "v1")));
        when(userRepository.findById("v1")).thenReturn(Optional.of(seller("Ana", "López", "Sonido SL")));

        assertEquals("Auriculares Pro", service.getTopSellingProducts().get(0).productName());
    }

    @Test
    @DisplayName("HU22-CP23: si el producto ya no existe, se muestra el nombre guardado en los pedidos")
    void shouldKeepTheNameStoredInTheOrdersWhenTheProductNoLongerExists() {
        when(statisticsRepository.findTopSellingProducts(10)).thenReturn(List.of(
                new ProductSalesRow("p1", "Auriculares", "v1", 5, new BigDecimal("50.00"))));
        when(productRepository.findAllById(List.of("p1"))).thenReturn(List.of());
        when(userRepository.findById("v1")).thenReturn(Optional.empty());

        assertEquals("Auriculares", service.getTopSellingProducts().get(0).productName());
    }

    @Test
    @DisplayName("HU22-CP24: los productos del ranking se piden todos de una vez")
    void shouldLoadTheProductsOfTheRankingInASingleQuery() {
        when(statisticsRepository.findTopSellingProducts(10)).thenReturn(List.of(
                row("p1", "v1", 5, "50.00"),
                row("p2", "v1", 4, "40.00"),
                row("p3", "v1", 3, "30.00")));
        when(userRepository.findById("v1")).thenReturn(Optional.of(seller("Ana", "López", "Sonido SL")));

        service.getTopSellingProducts();

        verify(productRepository, times(1)).findAllById(List.of("p1", "p2", "p3"));
        verify(productRepository, never()).findById(any());
    }

    // ---- HU24: estadísticas de ventas del vendedor ----

    private static final String SELLER_ID = "vendedor-1";

    private List<String> ids(List<ProductSalesDTO> sales) {
        return sales.stream().map(ProductSalesDTO::productId).toList();
    }

    private void sellerHas(List<Product> products, List<ProductSalesRow> rows) {
        when(productRepository.findAllBySellerId(SELLER_ID)).thenReturn(products);
        when(statisticsRepository.findSellerProductSales(eq(SELLER_ID), anyCollection())).thenReturn(rows);
        when(userRepository.findById(SELLER_ID)).thenReturn(Optional.of(seller("Ana", "López", "Tienda Ana")));
    }

    @Test
    @DisplayName("HU24-CP01: el ámbito vendedor exige el vendedor de la sesión")
    void shouldRequireTheSellerForTheSellerScope() {
        assertThrows(IllegalArgumentException.class, () -> service.getSales(StatisticsScope.VENDEDOR, null));
        assertThrows(IllegalArgumentException.class, () -> service.getSellerSales(" "));
        verifyNoInteractions(statisticsRepository);
    }

    @Test
    @DisplayName("HU24-CP02: los productos que no se han vendido nunca salen entre los menos vendidos")
    void shouldIncludeProductsNeverSoldInTheLeastSold() {
        sellerHas(
                List.of(productOf("p1", "Teclado", SELLER_ID), productOf("p2", "Ratón", SELLER_ID)),
                List.of(row("p1", SELLER_ID, 3, "30.00")));

        SalesStatisticsDTO result = service.getSellerSales(SELLER_ID);

        assertEquals(StatisticsScope.VENDEDOR, result.scope());
        assertEquals(List.of("p1"), ids(result.topSold()));
        assertEquals(List.of("p2", "p1"), ids(result.leastSold()));
        assertEquals(0, result.leastSold().get(0).unitsSold());
        assertEquals(new BigDecimal("0.00"), result.leastSold().get(0).revenue());
        assertEquals("Ratón", result.leastSold().get(0).productName());
    }

    @Test
    @DisplayName("HU24-CP03: el ranking del vendedor solo pide sus productos y siempre a su nombre")
    void shouldOnlyAskForTheProductsOfTheSessionSeller() {
        sellerHas(List.of(productOf("p1", "Teclado", SELLER_ID)), List.of(row("p1", null, 2, "20.00")));

        SalesStatisticsDTO result = service.getSellerSales(SELLER_ID);

        verify(statisticsRepository).findSellerProductSales(SELLER_ID, java.util.Set.of("p1"));
        verify(statisticsRepository, never()).findTopSellingProducts(org.mockito.ArgumentMatchers.anyInt());
        assertTrue(result.topSold().stream().allMatch(sale -> SELLER_ID.equals(sale.sellerId())));
        assertEquals("Tienda Ana", result.topSold().get(0).sellerName());
    }

    @Test
    @DisplayName("HU24-CP04: más vendidos de más a menos y menos vendidos de menos a más")
    void shouldSortTopSoldAndLeastSoldInOppositeOrder() {
        sellerHas(
                List.of(productOf("p1", "A", SELLER_ID), productOf("p2", "B", SELLER_ID), productOf("p3", "C", SELLER_ID)),
                List.of(row("p2", SELLER_ID, 5, "50.00"), row("p3", SELLER_ID, 2, "40.00"), row("p1", SELLER_ID, 2, "20.00")));

        SalesStatisticsDTO result = service.getSellerSales(SELLER_ID);

        assertEquals(List.of("p2", "p3", "p1"), ids(result.topSold()));
        assertEquals(List.of("p1", "p3", "p2"), ids(result.leastSold()));
    }

    @Test
    @DisplayName("HU24-CP05: con empates cada ranking enseña solo 10, siempre los mismos")
    void shouldLimitBothRankingsToTenEvenWithTies() {
        List<Product> products = new ArrayList<>();
        List<ProductSalesRow> rows = new ArrayList<>();
        for (int i = 10; i < 22; i++) {
            products.add(productOf("p" + i, "Producto " + i, SELLER_ID));
            rows.add(row("p" + i, SELLER_ID, 1, "10.00"));
        }
        sellerHas(products, rows);

        SalesStatisticsDTO result = service.getSellerSales(SELLER_ID);

        assertEquals(StatisticsService.RANKING_SIZE, result.topSold().size());
        assertEquals(StatisticsService.RANKING_SIZE, result.leastSold().size());
        assertEquals("p10", result.topSold().get(0).productId());
        assertEquals("p10", result.leastSold().get(0).productId());
    }

    @Test
    @DisplayName("HU24-CP06: un producto eliminado cuenta en los más vendidos pero no en los menos vendidos")
    void shouldKeepDeletedProductsOutOfTheLeastSold() {
        sellerHas(
                List.of(productOf("p1", "Teclado", SELLER_ID)),
                List.of(row("borrado", SELLER_ID, 4, "40.00"), row("p1", SELLER_ID, 1, "10.00")));

        SalesStatisticsDTO result = service.getSellerSales(SELLER_ID);

        assertEquals(List.of("borrado", "p1"), ids(result.topSold()));
        assertEquals("Producto borrado", result.topSold().get(0).productName());
        assertEquals(List.of("p1"), ids(result.leastSold()));
    }

    @Test
    @DisplayName("HU24-CP07: un vendedor sin productos ni ventas recibe los rankings vacíos")
    void shouldReturnEmptyRankingsForASellerWithoutProducts() {
        when(productRepository.findAllBySellerId(SELLER_ID)).thenReturn(List.of());
        when(statisticsRepository.findSellerProductSales(eq(SELLER_ID), anyCollection())).thenReturn(List.of());

        SalesStatisticsDTO result = service.getSellerSales(SELLER_ID);

        assertTrue(result.topSold().isEmpty());
        assertTrue(result.leastSold().isEmpty());
    }

    @Test
    @DisplayName("HU24-CP08: el ámbito plataforma devuelve el ranking del administrador sin menos vendidos")
    void shouldReuseThePlatformRankingForThePlatformScope() {
        when(statisticsRepository.findTopSellingProducts(10)).thenReturn(List.of(row("p1", "v1", 3, "30.00")));
        when(productRepository.findAllById(any())).thenReturn(List.of());
        when(userRepository.findById("v1")).thenReturn(Optional.empty());

        SalesStatisticsDTO result = service.getSales(StatisticsScope.PLATAFORMA, null);

        assertEquals(StatisticsScope.PLATAFORMA, result.scope());
        assertEquals(List.of("p1"), ids(result.topSold()));
        assertTrue(result.leastSold().isEmpty());
        verify(productRepository, never()).findAllBySellerId(any());
    }
}
