package es.uclm.esibuy.cart.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import es.uclm.esibuy.cart.dto.AddCartItemDTO;
import es.uclm.esibuy.cart.dto.CartDTO;
import es.uclm.esibuy.cart.dto.UpdateCartItemQuantityDTO;
import es.uclm.esibuy.cart.model.Cart;
import es.uclm.esibuy.cart.repository.CartRepository;
import es.uclm.esibuy.common.exception.ProductNotFoundException;
import es.uclm.esibuy.common.exception.ProductoExclusivoPremiumException;
import es.uclm.esibuy.common.exception.ProductoNoDisponibleException;
import es.uclm.esibuy.common.exception.StockInsuficienteException;
import es.uclm.esibuy.products.model.Product;
import es.uclm.esibuy.products.model.ProductDetails;
import es.uclm.esibuy.products.repository.ProductRepository;
import es.uclm.esibuy.users.model.ClientProfile;
import es.uclm.esibuy.users.model.CustomerType;
import es.uclm.esibuy.users.model.User;
import es.uclm.esibuy.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

        private static final String CUSTOMER_ID = "cliente-1";
        private static final String PRODUCT_ID = "producto-1";
        private static final String PRODUCT_NAME = "Auriculares";
        private static final BigDecimal PRICE = new BigDecimal("19.99");

        @Mock
        private ProductRepository productRepository;

        @Mock
        private CartRepository cartRepository;

        @Mock
        private UserRepository userRepository;

        private CartService cartService;

        @BeforeEach
        void setUp() {
                cartService = new CartService(productRepository, cartRepository, userRepository);
        }

        @Test
        void shouldAddNewProductWhenAvailableStockIsSufficient() {
                Product product = availableProduct(5);

                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
                when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.empty());
                when(cartRepository.save(any(Cart.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                CartDTO result = cartService.addItem(
                                CUSTOMER_ID,
                                new AddCartItemDTO(PRODUCT_ID, 2));

                assertEquals(CUSTOMER_ID, result.customerId());
                assertEquals(1, result.items().size());
                assertEquals(2, result.items().get(0).quantity());
                assertEquals(new BigDecimal("39.98"), result.items().get(0).subtotal());
                assertEquals("https://example.com/auriculares.jpg", result.items().get(0).imageUrl());
                assertEquals(new BigDecimal("39.98"), result.totalPrice());

                verify(cartRepository).save(any(Cart.class));
        }

        @Test
        void shouldAccumulateQuantityForProductAlreadyInCart() {
                Product product = availableProduct(5);
                Cart existingCart = new Cart(CUSTOMER_ID);
                existingCart.addOrIncrementItem(PRODUCT_ID, PRODUCT_NAME, PRICE, 2);

                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
                when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(existingCart));
                when(cartRepository.save(any(Cart.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                CartDTO result = cartService.addItem(
                                CUSTOMER_ID,
                                new AddCartItemDTO(PRODUCT_ID, 3));

                assertEquals(1, result.items().size());
                assertEquals(5, result.items().get(0).quantity());
                assertEquals(new BigDecimal("99.95"), result.items().get(0).subtotal());
                assertEquals(new BigDecimal("99.95"), result.totalPrice());
        }

        @Test
        void shouldRejectAccumulatedQuantityThatExceedsAvailableStock() {
                Cart existingCart = new Cart(CUSTOMER_ID);
                existingCart.addOrIncrementItem(PRODUCT_ID, PRODUCT_NAME, PRICE, 3);

                Product product = mock(Product.class);
                when(product.getId()).thenReturn(PRODUCT_ID);
                when(product.getStock()).thenReturn(5);
                when(product.isAvailable()).thenReturn(true);

                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
                when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(existingCart));

                assertThrows(
                                StockInsuficienteException.class,
                                () -> cartService.addItem(
                                                CUSTOMER_ID,
                                                new AddCartItemDTO(PRODUCT_ID, 3)));
        }

        @Test
        void shouldRejectQuantityLessThanOne() {
                assertThrows(
                                IllegalArgumentException.class,
                                () -> cartService.addItem(
                                                CUSTOMER_ID,
                                                new AddCartItemDTO(PRODUCT_ID, 0)));
        }

        @Test
        void shouldRejectMissingProduct() {
                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.empty());

                assertThrows(
                                ProductNotFoundException.class,
                                () -> cartService.addItem(
                                                CUSTOMER_ID,
                                                new AddCartItemDTO(PRODUCT_ID, 1)));
        }

        @Test
        void shouldRejectInactiveProduct() {
                Product product = mock(Product.class);
                when(product.isAvailable()).thenReturn(false);

                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));

                assertThrows(
                                ProductoNoDisponibleException.class,
                                () -> cartService.addItem(
                                                CUSTOMER_ID,
                                                new AddCartItemDTO(PRODUCT_ID, 1)));
        }

        @Test
        void shouldIncreaseItemQuantityWhenStockIsSufficient() {
                Cart existingCart = new Cart(CUSTOMER_ID);
                existingCart.addOrIncrementItem(PRODUCT_ID, PRODUCT_NAME, PRICE, 2);

                Product product = productWithStock(5);

                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
                when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(existingCart));
                when(cartRepository.save(any(Cart.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                CartDTO result = cartService.updateItemQuantity(
                                CUSTOMER_ID,
                                PRODUCT_ID,
                                new UpdateCartItemQuantityDTO(4));

                assertEquals(4, result.items().get(0).quantity());
                assertEquals(new BigDecimal("79.96"), result.items().get(0).subtotal());
                assertEquals(new BigDecimal("79.96"), result.totalPrice());

                verify(cartRepository).save(existingCart);
        }

        @Test
        void shouldRejectUpdatedQuantityThatExceedsAvailableStock() {
                Cart existingCart = new Cart(CUSTOMER_ID);
                existingCart.addOrIncrementItem(PRODUCT_ID, PRODUCT_NAME, PRICE, 2);

                Product product = productWithStock(4);

                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
                when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(existingCart));

                assertThrows(
                                StockInsuficienteException.class,
                                () -> cartService.updateItemQuantity(
                                                CUSTOMER_ID,
                                                PRODUCT_ID,
                                                new UpdateCartItemQuantityDTO(5)));
        }

        @Test
        @DisplayName("HU13-CP04: se puede subir la cantidad justo hasta el stock disponible")
        void shouldAllowIncreasingTheQuantityUpToTheAvailableStock() {
                Cart existingCart = new Cart(CUSTOMER_ID);
                existingCart.addOrIncrementItem(PRODUCT_ID, PRODUCT_NAME, PRICE, 2);

                Product product = productWithStock(5);

                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
                when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(existingCart));
                when(cartRepository.save(any(Cart.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                CartDTO result = cartService.updateItemQuantity(
                                CUSTOMER_ID,
                                PRODUCT_ID,
                                new UpdateCartItemQuantityDTO(5));

                assertEquals(5, result.items().get(0).quantity());
        }

        @Test
        @DisplayName("HU13-CP05: la primera vez que se añade un producto no se puede superar su stock")
        void shouldRejectAddingMoreUnitsThanTheStockToAnEmptyCart() {
                Product product = productWithStock(3);

                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
                when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.empty());

                AddCartItemDTO request = new AddCartItemDTO(PRODUCT_ID, 4);

                assertThrows(
                                StockInsuficienteException.class,
                                () -> cartService.addItem(CUSTOMER_ID, request));

                verify(cartRepository, never()).save(any(Cart.class));
        }

        @Test
        @DisplayName("HU13-CP06: bajar la cantidad no se comprueba contra el stock aunque este haya bajado")
        void shouldAllowReducingTheQuantityWithoutCheckingTheStock() {
                Cart existingCart = new Cart(CUSTOMER_ID);
                existingCart.addOrIncrementItem(PRODUCT_ID, PRODUCT_NAME, PRICE, 3);

                Product product = mock(Product.class);
                lenient().when(product.getStock()).thenReturn(1);

                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
                when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(existingCart));
                when(cartRepository.save(any(Cart.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                CartDTO result = cartService.updateItemQuantity(
                                CUSTOMER_ID,
                                PRODUCT_ID,
                                new UpdateCartItemQuantityDTO(2));

                assertEquals(2, result.items().get(0).quantity());
        }

        @Test
        void shouldReduceItemQuantityAndRecalculateCartTotal() {
                Cart existingCart = new Cart(CUSTOMER_ID);
                existingCart.addOrIncrementItem(PRODUCT_ID, PRODUCT_NAME, PRICE, 3);
                existingCart.addOrIncrementItem(
                                "producto-2",
                                "Teclado",
                                new BigDecimal("30.00"),
                                1);

                when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(existingCart));
                when(cartRepository.save(any(Cart.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                CartDTO result = cartService.updateItemQuantity(
                                CUSTOMER_ID,
                                PRODUCT_ID,
                                new UpdateCartItemQuantityDTO(1));

                assertEquals(1, result.items().get(0).quantity());
                assertEquals(new BigDecimal("19.99"), result.items().get(0).subtotal());
                assertEquals(new BigDecimal("49.99"), result.totalPrice());
        }

        @Test
        void shouldRemoveItemAndSetTotalToZeroWhenQuantityIsZero() {
                Cart existingCart = new Cart(CUSTOMER_ID);
                existingCart.addOrIncrementItem(PRODUCT_ID, PRODUCT_NAME, PRICE, 2);

                when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(existingCart));
                when(cartRepository.save(any(Cart.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                CartDTO result = cartService.updateItemQuantity(
                                CUSTOMER_ID,
                                PRODUCT_ID,
                                new UpdateCartItemQuantityDTO(0));

                assertEquals(0, result.items().size());
                assertEquals(BigDecimal.ZERO, result.totalPrice());

                verify(cartRepository).save(existingCart);
        }

        @Test
        void shouldDeleteExistingItemAndRecalculateCartTotal() {
                Cart existingCart = new Cart(CUSTOMER_ID);
                existingCart.addOrIncrementItem(PRODUCT_ID, PRODUCT_NAME, PRICE, 2);
                existingCart.addOrIncrementItem(
                                "producto-2",
                                "Teclado",
                                new BigDecimal("30.00"),
                                1);

                when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(existingCart));
                when(cartRepository.save(any(Cart.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                CartDTO result = cartService.removeItem(CUSTOMER_ID, PRODUCT_ID);

                assertEquals(1, result.items().size());
                assertEquals("producto-2", result.items().get(0).productId());
                assertEquals(new BigDecimal("30.00"), result.items().get(0).subtotal());
                assertEquals(new BigDecimal("30.00"), result.totalPrice());
        }

        @Test
        void shouldRejectDeletingItemThatIsNotInCart() {
                Cart existingCart = new Cart(CUSTOMER_ID);
                existingCart.addOrIncrementItem(
                                "producto-2",
                                "Teclado",
                                new BigDecimal("30.00"),
                                1);

                when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(existingCart));

                assertThrows(
                                ProductNotFoundException.class,
                                () -> cartService.removeItem(CUSTOMER_ID, PRODUCT_ID));
        }

        private Product availableProduct(int stock) {
                Product product = mock(Product.class);

                when(product.getId()).thenReturn(PRODUCT_ID);
                when(product.getName()).thenReturn(PRODUCT_NAME);
                when(product.getPrice()).thenReturn(PRICE);
                when(product.getStock()).thenReturn(stock);
                when(product.isAvailable()).thenReturn(true);
                when(product.getImageUrl()).thenReturn("https://example.com/auriculares.jpg");

                return product;
        }

        // HU16: solo los clientes Premium pueden añadir al carrito un producto exclusivo.

        private Product premiumOnlyProduct() {
                Product product = mock(Product.class);
                lenient().when(product.getId()).thenReturn(PRODUCT_ID);
                lenient().when(product.getName()).thenReturn(PRODUCT_NAME);
                lenient().when(product.getPrice()).thenReturn(PRICE);
                lenient().when(product.getStock()).thenReturn(5);
                lenient().when(product.isAvailable()).thenReturn(true);
                lenient().when(product.isPremiumOnly()).thenReturn(true);
                return product;
        }

        @Test
        @DisplayName("HU16: un cliente Estándar no puede añadir un producto exclusivo al carrito")
        void shouldRejectPremiumOnlyProductForStandardCustomer() {
                Product product = premiumOnlyProduct();
                mockCustomerType(CUSTOMER_ID, CustomerType.STANDARD);
                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
                lenient().when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.empty());

                assertThrows(ProductoExclusivoPremiumException.class,
                                () -> cartService.addItem(CUSTOMER_ID, new AddCartItemDTO(PRODUCT_ID, 1)));

                verify(cartRepository, never()).save(any(Cart.class));
        }

        @Test
        @DisplayName("HU16: un cliente sin perfil de cliente se trata como Estándar")
        void shouldRejectPremiumOnlyProductWhenCustomerIsUnknown() {
                Product product = premiumOnlyProduct();
                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
                lenient().when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.empty());

                assertThrows(ProductoExclusivoPremiumException.class,
                                () -> cartService.addItem(CUSTOMER_ID, new AddCartItemDTO(PRODUCT_ID, 1)));

                verify(cartRepository, never()).save(any(Cart.class));
        }

        @Test
        @DisplayName("HU16: un cliente Premium sí puede añadir un producto exclusivo al carrito")
        void shouldAllowPremiumCustomerToAddPremiumOnlyProduct() {
                Product product = premiumOnlyProduct();
                mockCustomerType(CUSTOMER_ID, CustomerType.PREMIUM);
                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
                when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.empty());
                when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

                CartDTO result = cartService.addItem(CUSTOMER_ID, new AddCartItemDTO(PRODUCT_ID, 1));

                assertEquals(1, result.items().size());
                assertEquals(PRODUCT_ID, result.items().get(0).productId());
        }

        private Product productWithStock(int stock) {
                Product product = mock(Product.class);

                when(product.getStock()).thenReturn(stock);
                when(product.isAvailable()).thenReturn(true);

                return product;
        }

        private void mockCustomerType(String customerId, CustomerType type) {
                User user = mock(User.class);
                ClientProfile profile = mock(ClientProfile.class);
                when(profile.getCustomerType()).thenReturn(type);
                when(user.getClientProfile()).thenReturn(profile);
                when(userRepository.findById(customerId)).thenReturn(Optional.of(user));
        }

        private Product productWithDiscounts(BigDecimal basePrice, BigDecimal standardDiscount, BigDecimal premiumDiscount) {
                Product product = mock(Product.class);
                when(product.getId()).thenReturn(PRODUCT_ID);
                when(product.getName()).thenReturn(PRODUCT_NAME);
                when(product.getPrice()).thenReturn(basePrice);
                when(product.getStock()).thenReturn(10);
                when(product.isAvailable()).thenReturn(true);
                when(product.getDiscount()).thenReturn(standardDiscount);
                when(product.getPremiumDiscount()).thenReturn(premiumDiscount);
                return product;
        }

        @Test
        @DisplayName("HU13-CP01: cada línea del carrito informa del stock disponible del producto")
        void shouldExposeTheAvailableStockOfEachCartItem() {
                Cart existingCart = new Cart(CUSTOMER_ID);
                existingCart.addOrIncrementItem(PRODUCT_ID, PRODUCT_NAME, PRICE, 2);

                Product product = mock(Product.class);
                when(product.getStock()).thenReturn(7);

                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
                when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(existingCart));

                CartDTO result = cartService.getCart(CUSTOMER_ID);

                assertEquals(7, result.items().get(0).availableStock());
        }

        @Test
        @DisplayName("HU13-CP02: si el producto ya no existe, el stock disponible de su línea es nulo")
        void shouldNotExposeStockWhenTheProductNoLongerExists() {
                Cart existingCart = new Cart(CUSTOMER_ID);
                existingCart.addOrIncrementItem(PRODUCT_ID, PRODUCT_NAME, PRICE, 2);

                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.empty());
                when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(existingCart));

                CartDTO result = cartService.getCart(CUSTOMER_ID);

                assertNull(result.items().get(0).availableStock());
        }

        private Product currentProduct(String price, String discount, String premiumDiscount) {
                return new Product(new ProductDetails(
                                PRODUCT_NAME,
                                List.of("categoria-1"),
                                10,
                                "Audio",
                                price != null ? new BigDecimal(price) : null,
                                discount != null ? new BigDecimal(discount) : null,
                                premiumDiscount != null ? new BigDecimal(premiumDiscount) : null,
                                null,
                                true,
                                "vendedor-1"));
        }

        private Cart cartWithStoredPrice(String storedPrice, int quantity) {
                Cart existingCart = new Cart(CUSTOMER_ID);
                existingCart.addOrIncrementItem(PRODUCT_ID, PRODUCT_NAME, new BigDecimal(storedPrice), quantity);
                return existingCart;
        }

        @Test
        @DisplayName("HU12-CP09: si el precio del producto sube, el carrito muestra el precio nuevo")
        void shouldShowTheNewPriceWhenTheProductGetsMoreExpensive() {
                when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(cartWithStoredPrice("19.99", 2)));
                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(currentProduct("25.00", null, null)));

                CartDTO result = cartService.getCart(CUSTOMER_ID);

                assertEquals(new BigDecimal("25.00"), result.items().get(0).unitPrice());
                assertEquals(new BigDecimal("50.00"), result.items().get(0).subtotal());
                assertEquals(new BigDecimal("50.00"), result.totalPrice());
        }

        @Test
        @DisplayName("HU12-CP10: si el precio del producto baja, el carrito muestra el precio nuevo")
        void shouldShowTheNewPriceWhenTheProductGetsCheaper() {
                when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(cartWithStoredPrice("19.99", 2)));
                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(currentProduct("10.00", null, null)));

                CartDTO result = cartService.getCart(CUSTOMER_ID);

                assertEquals(new BigDecimal("10.00"), result.items().get(0).unitPrice());
                assertEquals(new BigDecimal("20.00"), result.totalPrice());
                assertEquals(BigDecimal.ZERO, result.totalDiscount());
        }

        @Test
        @DisplayName("HU12-CP11: si el vendedor cambia el descuento, el carrito aplica el descuento actual")
        void shouldApplyTheCurrentDiscountWhenTheSellerChangesIt() {
                // Guardado con un 10 % de descuento (90,00); ahora el descuento es del 20 %
                when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(cartWithStoredPrice("90.00", 1)));
                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(currentProduct("100.00", "20", null)));

                CartDTO result = cartService.getCart(CUSTOMER_ID);

                assertEquals(new BigDecimal("80.00"), result.items().get(0).unitPrice());
                assertEquals(new BigDecimal("20"), result.items().get(0).discount());
                assertEquals(new BigDecimal("80.00"), result.totalPrice());
                assertEquals(new BigDecimal("100.00"), result.totalBeforeDiscount());
                assertEquals(new BigDecimal("20.00"), result.totalDiscount());
        }

        @Test
        @DisplayName("HU12-CP12: si el cliente pasa a premium, el carrito aplica el descuento premium")
        void shouldApplyThePremiumDiscountWhenTheCustomerBecomesPremium() {
                // Guardado cuando era estándar (100,00)
                mockCustomerType(CUSTOMER_ID, CustomerType.PREMIUM);
                when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(cartWithStoredPrice("100.00", 1)));
                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(currentProduct("100.00", "10", "5")));

                CartDTO result = cartService.getCart(CUSTOMER_ID);

                assertEquals(new BigDecimal("85.00"), result.items().get(0).unitPrice());
                assertTrue(result.items().get(0).isPremiumDiscount());
                assertEquals(new BigDecimal("85.00"), result.totalPrice());
        }

        @Test
        @DisplayName("HU12-CP13: si el cliente deja de ser premium, el carrito vuelve al precio estándar")
        void shouldDropThePremiumDiscountWhenTheCustomerIsNoLongerPremium() {
                // Guardado cuando era premium (85,00)
                mockCustomerType(CUSTOMER_ID, CustomerType.STANDARD);
                when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(cartWithStoredPrice("85.00", 1)));
                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(currentProduct("100.00", "10", "5")));

                CartDTO result = cartService.getCart(CUSTOMER_ID);

                assertEquals(new BigDecimal("90.00"), result.items().get(0).unitPrice());
                assertFalse(result.items().get(0).isPremiumDiscount());
                assertEquals(new BigDecimal("90.00"), result.totalPrice());
        }

        @Test
        @DisplayName("HU12-CP14: el total del carrito suma el precio actual de cada línea")
        void shouldAddUpTheCurrentPriceOfEveryLineInTheCartTotal() {
                Cart existingCart = cartWithStoredPrice("10.00", 1);
                existingCart.addOrIncrementItem("producto-2", "Cargador", new BigDecimal("20.00"), 2);

                when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(existingCart));
                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(currentProduct("12.00", null, null)));
                when(productRepository.findById("producto-2")).thenReturn(Optional.of(currentProduct("15.00", null, null)));

                CartDTO result = cartService.getCart(CUSTOMER_ID);

                assertEquals(new BigDecimal("12.00"), result.items().get(0).subtotal());
                assertEquals(new BigDecimal("30.00"), result.items().get(1).subtotal());
                assertEquals(new BigDecimal("42.00"), result.totalPrice());
        }

        @Test
        @DisplayName("HU12-CP15: consultar el carrito no reescribe el precio guardado")
        void shouldNotRewriteTheStoredCartWhenReadingIt() {
                when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(cartWithStoredPrice("19.99", 1)));
                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(currentProduct("25.00", null, null)));

                cartService.getCart(CUSTOMER_ID);

                verify(cartRepository, never()).save(any(Cart.class));
        }

        @Test
        @DisplayName("HU12-CP16: si el producto ya no existe, la línea conserva el precio guardado")
        void shouldKeepTheStoredPriceWhenTheProductNoLongerExists() {
                when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(cartWithStoredPrice("19.99", 2)));
                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.empty());

                CartDTO result = cartService.getCart(CUSTOMER_ID);

                assertEquals(new BigDecimal("19.99"), result.items().get(0).unitPrice());
                assertEquals(new BigDecimal("39.98"), result.totalPrice());
        }

        @Test
        @DisplayName("HU12-CP17: si el producto no tiene precio, la línea conserva el precio guardado")
        void shouldKeepTheStoredPriceWhenTheProductHasNoPrice() {
                when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(cartWithStoredPrice("19.99", 2)));
                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(currentProduct(null, null, null)));

                CartDTO result = cartService.getCart(CUSTOMER_ID);

                assertEquals(new BigDecimal("19.99"), result.items().get(0).unitPrice());
                assertEquals(new BigDecimal("39.98"), result.totalPrice());
        }

        @Test
        void shouldApplyOnlyStandardDiscountForStandardCustomer() {
                Product product = productWithDiscounts(new BigDecimal("100.00"), new BigDecimal("10"), new BigDecimal("5"));
                mockCustomerType(CUSTOMER_ID, CustomerType.STANDARD);

                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
                when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.empty());
                when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

                CartDTO result = cartService.addItem(CUSTOMER_ID, new AddCartItemDTO(PRODUCT_ID, 1));

                assertEquals(new BigDecimal("90.00"), result.items().get(0).unitPrice());
                assertEquals(new BigDecimal("90.00"), result.items().get(0).subtotal());
                assertEquals(new BigDecimal("10"), result.items().get(0).discount());
                assertFalse(result.items().get(0).isPremiumDiscount());
                assertEquals(new BigDecimal("90.00"), result.totalPrice());
                assertEquals(new BigDecimal("10.00"), result.totalDiscount());
        }

        @Test
        void shouldAccumulateStandardAndPremiumDiscountForPremiumCustomer() {
                Product product = productWithDiscounts(new BigDecimal("100.00"), new BigDecimal("10"), new BigDecimal("5"));
                mockCustomerType(CUSTOMER_ID, CustomerType.PREMIUM);

                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
                when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.empty());
                when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

                CartDTO result = cartService.addItem(CUSTOMER_ID, new AddCartItemDTO(PRODUCT_ID, 1));

                assertEquals(new BigDecimal("85.00"), result.items().get(0).unitPrice());
                assertEquals(new BigDecimal("85.00"), result.items().get(0).subtotal());
                assertEquals(new BigDecimal("15"), result.items().get(0).discount());
                assertTrue(result.items().get(0).isPremiumDiscount());
                assertEquals(new BigDecimal("85.00"), result.totalPrice());
                assertEquals(new BigDecimal("15.00"), result.totalDiscount());
        }

        @Test
        void shouldApplyStandardDiscountForPremiumCustomerWhenNoPremiumDiscount() {
                Product product = productWithDiscounts(new BigDecimal("100.00"), new BigDecimal("10"), null);
                mockCustomerType(CUSTOMER_ID, CustomerType.PREMIUM);

                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
                when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.empty());
                when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

                CartDTO result = cartService.addItem(CUSTOMER_ID, new AddCartItemDTO(PRODUCT_ID, 1));

                assertEquals(new BigDecimal("90.00"), result.items().get(0).unitPrice());
                assertEquals(new BigDecimal("10"), result.items().get(0).discount());
                assertFalse(result.items().get(0).isPremiumDiscount());
        }

        @Test
        void shouldApplyPremiumDiscountForPremiumCustomerWhenNoStandardDiscount() {
                Product product = productWithDiscounts(new BigDecimal("100.00"), null, new BigDecimal("5"));
                mockCustomerType(CUSTOMER_ID, CustomerType.PREMIUM);

                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
                when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.empty());
                when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

                CartDTO result = cartService.addItem(CUSTOMER_ID, new AddCartItemDTO(PRODUCT_ID, 1));

                assertEquals(new BigDecimal("95.00"), result.items().get(0).unitPrice());
                assertEquals(new BigDecimal("5"), result.items().get(0).discount());
                assertTrue(result.items().get(0).isPremiumDiscount());
        }

        @Test
        void shouldCapAccumulatedDiscountAt100Percent() {
                Product product = productWithDiscounts(new BigDecimal("100.00"), new BigDecimal("60"), new BigDecimal("50"));
                mockCustomerType(CUSTOMER_ID, CustomerType.PREMIUM);

                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
                when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.empty());
                when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

                CartDTO result = cartService.addItem(CUSTOMER_ID, new AddCartItemDTO(PRODUCT_ID, 1));

                assertEquals(new BigDecimal("0.00"), result.items().get(0).unitPrice());
                assertEquals(new BigDecimal("100"), result.items().get(0).discount());
                assertTrue(result.items().get(0).isPremiumDiscount());
        }
}