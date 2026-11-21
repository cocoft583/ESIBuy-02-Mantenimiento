
package es.uclm.esibuy.orders.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mongodb.client.result.UpdateResult;
import es.uclm.esibuy.cart.model.Cart;
import es.uclm.esibuy.cart.repository.CartRepository;
import es.uclm.esibuy.configuration.model.GeneralConfiguration;
import es.uclm.esibuy.configuration.service.GeneralConfigurationService;
import es.uclm.esibuy.common.exception.PagoFallidoException;
import es.uclm.esibuy.common.exception.PedidoNoCompletadoException;
import es.uclm.esibuy.common.exception.PedidoNotFoundException;
import es.uclm.esibuy.common.exception.RecursoCheckoutNoEncontradoException;
import es.uclm.esibuy.common.exception.StockInsuficienteException;
import es.uclm.esibuy.orders.dto.CheckoutSummaryDTO;
import es.uclm.esibuy.orders.dto.CreateOrderDTO;
import es.uclm.esibuy.orders.dto.CustomerOrderDTO;
import es.uclm.esibuy.orders.dto.OrderConfirmationDTO;
import es.uclm.esibuy.orders.dto.OrderStatusHistoryDTO;
import es.uclm.esibuy.orders.dto.SellerOrderDTO;
import es.uclm.esibuy.orders.dto.ShippingTrackingDTO;
import es.uclm.esibuy.orders.model.Order;
import es.uclm.esibuy.orders.model.OrderItem;
import es.uclm.esibuy.orders.model.OrderPaymentMethod;
import es.uclm.esibuy.orders.model.OrderPaymentStatus;
import es.uclm.esibuy.orders.model.OrderShippingAddress;
import es.uclm.esibuy.orders.model.OrderStatus;
import es.uclm.esibuy.orders.repository.OrderRepository;
import es.uclm.esibuy.products.model.Product;
import es.uclm.esibuy.products.repository.ProductRepository;
import es.uclm.esibuy.users.model.ClientProfile;
import es.uclm.esibuy.users.model.CustomerType;
import es.uclm.esibuy.users.model.PaymentMethod;
import es.uclm.esibuy.users.model.ShippingAddress;
import es.uclm.esibuy.users.model.User;
import es.uclm.esibuy.users.model.UserRole;
import es.uclm.esibuy.users.repository.UserRepository;
import es.uclm.esibuy.products.model.ProductDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

        private static final String CUSTOMER_ID = "cliente-1";
        private static final String PRODUCT_ID = "producto-1";
        private static final BigDecimal PRICE = new BigDecimal("19.99");

        @Mock
        private UserRepository userRepository;

        @Mock
        private CartRepository cartRepository;

        @Mock
        private ProductRepository productRepository;

        @Mock
        private OrderRepository orderRepository;

        @Mock
        private MongoTemplate mongoTemplate;

        @Mock
        private PaymentGateway paymentGateway;

        @Mock
        private GeneralConfigurationService generalConfigurationService;

        private OrderService orderService;
        private Cart cart;

        @BeforeEach
        void setUp() {
                orderService = new OrderService(
                                userRepository,
                                cartRepository,
                                productRepository,
                                orderRepository,
                                mongoTemplate,
                                paymentGateway,
                                generalConfigurationService);

                lenient().when(generalConfigurationService.getCurrent())
                                .thenReturn(new GeneralConfiguration(
                                                BigDecimal.ZERO,
                                                BigDecimal.ZERO,
                                                BigDecimal.ZERO,
                                                true));

                User customer = new User(
                                "cliente@example.com",
                                "Ana",
                                "López",
                                UserRole.CLIENT);

                ClientProfile profile = new ClientProfile(null, CustomerType.STANDARD);

                profile.setAddresses(List.of(new ShippingAddress(
                                "direccion-1",
                                "Ana López",
                                "Calle Mayor 1",
                                null,
                                "13001",
                                "Ciudad Real",
                                "Ciudad Real",
                                "600123456",
                                true)));

                profile.setPaymentMethods(List.of(PaymentMethod.card(
                                "pago-1",
                                "Visa",
                                "Ana López",
                                "VISA",
                                "1234",
                                12,
                                2030,
                                true)));

                customer.setClientProfile(profile);

                lenient().when(userRepository.findById(CUSTOMER_ID))
                                .thenReturn(Optional.of(customer));

                cart = new Cart(CUSTOMER_ID);
                cart.addOrIncrementItem(PRODUCT_ID, "Auriculares", PRICE, 2);
        }

        @Test
        @DisplayName("HU18-CP12: un cliente solo lista los pedidos asignados a su propio identificador")
        void shouldListOnlyTheOrdersOfTheRequestingCustomer() {
                when(orderRepository.findAllByCustomerIdOrderByCreatedAtDesc(CUSTOMER_ID))
                                .thenReturn(List.of());

                List<CustomerOrderDTO> result = orderService.getCustomerOrders(CUSTOMER_ID);

                assertTrue(result.isEmpty());
                verify(orderRepository).findAllByCustomerIdOrderByCreatedAtDesc(CUSTOMER_ID);
                verifyNoMoreInteractions(orderRepository);
        }

        @Test
        void shouldChargeCreatePaidPendingOrderDeductStockAndEmptyCart() {
                prepareCartAndProduct(productWithStock(5));
                enableStockReservation();

                when(paymentGateway.charge(any(), any())).thenReturn(true);

                Order savedOrder = org.mockito.Mockito.mock(Order.class);

                when(savedOrder.getId()).thenReturn("pedido-123");
                when(savedOrder.getTotalPrice()).thenReturn(new BigDecimal("39.98"));
                when(savedOrder.getStatus()).thenReturn(OrderStatus.PENDIENTE);
                when(savedOrder.getPaymentStatus()).thenReturn(OrderPaymentStatus.PAGADO);
                when(savedOrder.getCreatedAt()).thenReturn(java.time.LocalDateTime.now());

                when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

                OrderConfirmationDTO result = orderService.createOrder(
                                CUSTOMER_ID,
                                new CreateOrderDTO("direccion-1", "pago-1"));

                assertEquals("pedido-123", result.orderId());
                assertEquals(new BigDecimal("39.98"), result.totalPrice());
                assertEquals(OrderStatus.PENDIENTE, result.status());
                assertEquals(OrderPaymentStatus.PAGADO, result.paymentStatus());

                verify(mongoTemplate).findAndModify(
                                any(Query.class),
                                any(Update.class),
                                any(FindAndModifyOptions.class),
                                eq(Product.class));

                ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);

                verify(orderRepository, times(2)).save(orderCaptor.capture());

                assertEquals(OrderStatus.PENDIENTE, orderCaptor.getValue().getStatus());
                assertEquals(OrderPaymentStatus.PAGADO, orderCaptor.getValue().getPaymentStatus());

                verify(cartRepository).save(cart);

                assertEquals(List.of(), cart.getItems());
                assertEquals(BigDecimal.ZERO, cart.getTotalPrice());
        }

        @Test
        @DisplayName("HU11: el pedido se guarda con el pago pendiente antes de cobrar y se marca pagado después")
        void savesThePendingOrderBeforeChargingAndMarksItPaidAfterwards() {
                prepareCartAndProduct(productWithStock(5));
                enableStockReservation();
                when(paymentGateway.charge(any(), any())).thenReturn(true);

                List<OrderPaymentStatus> savedPaymentStatuses = new ArrayList<>();
                when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
                        Order order = invocation.getArgument(0);
                        savedPaymentStatuses.add(order.getPaymentStatus());
                        return order;
                });

                OrderConfirmationDTO result = orderService.createOrder(
                                CUSTOMER_ID,
                                new CreateOrderDTO("direccion-1", "pago-1"));

                InOrder inOrder = inOrder(orderRepository, paymentGateway);
                inOrder.verify(orderRepository).save(any(Order.class));
                inOrder.verify(paymentGateway).charge(any(), eq(new BigDecimal("39.98")));
                inOrder.verify(orderRepository).save(any(Order.class));

                assertEquals(
                                List.of(OrderPaymentStatus.PENDIENTE, OrderPaymentStatus.PAGADO),
                                savedPaymentStatuses);
                assertEquals(OrderPaymentStatus.PAGADO, result.paymentStatus());
        }

        @Test
        void shouldUseCurrentVatAndShippingSettingsWhenChargingOrder() {
                prepareCartAndProduct(productWithStock(5));
                enableStockReservation();
                when(generalConfigurationService.getCurrent())
                                .thenReturn(new GeneralConfiguration(
                                                new BigDecimal("21"),
                                                new BigDecimal("4.99"),
                                                new BigDecimal("50"),
                                                true));
                when(paymentGateway.charge(any(), any())).thenReturn(true);
                when(orderRepository.save(any(Order.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                orderService.createOrder(CUSTOMER_ID, new CreateOrderDTO("direccion-1", "pago-1"));

                ArgumentCaptor<BigDecimal> chargedAmount = ArgumentCaptor.forClass(BigDecimal.class);
                verify(paymentGateway).charge(any(), chargedAmount.capture());
                assertEquals(new BigDecimal("44.97"), chargedAmount.getValue());

                ArgumentCaptor<Order> savedOrder = ArgumentCaptor.forClass(Order.class);
                verify(orderRepository, times(2)).save(savedOrder.capture());
                assertEquals(new BigDecimal("44.97"), savedOrder.getValue().getTotalPrice());
        }

        @Test
        void shouldChargeTheCurrentDiscountedPriceWhenCreatingOrder() {
                Cart cartWithDiscount = new Cart(CUSTOMER_ID);
                cartWithDiscount.addOrIncrementItem(PRODUCT_ID, "Auriculares", new BigDecimal("85.00"), 1);

                // El producto cuesta 100 con un 15 % de descuento: el precio actual es 85
                Product catalogProduct = productWithStock(5);
                catalogProduct.setPrice(new BigDecimal("100.00"));
                catalogProduct.setDiscount(new BigDecimal("15"));

                when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(cartWithDiscount));
                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(catalogProduct));
                enableStockReservation();
                when(paymentGateway.charge(any(), any())).thenReturn(true);
                when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

                orderService.createOrder(CUSTOMER_ID, new CreateOrderDTO("direccion-1", "pago-1"));

                ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
                verify(orderRepository, times(2)).save(orderCaptor.capture());

                Order capturedOrder = orderCaptor.getValue();
                assertEquals(1, capturedOrder.getItems().size());
                assertEquals(new BigDecimal("85.00"), capturedOrder.getItems().get(0).unitPrice());
                assertEquals(new BigDecimal("85.00"), capturedOrder.getItems().get(0).subtotal());
        }

        private Product productAtCurrentPrice(String price, String discount, String premiumDiscount) {
                Product product = new Product(new ProductDetails(
                                "Auriculares",
                                List.of("categoria-1"),
                                5,
                                "Audio",
                                price != null ? new BigDecimal(price) : null,
                                discount != null ? new BigDecimal(discount) : null,
                                premiumDiscount != null ? new BigDecimal(premiumDiscount) : null,
                                null,
                                true,
                                "vendedor-1"));

                org.springframework.test.util.ReflectionTestUtils.setField(
                                product, "id", PRODUCT_ID);

                return product;
        }

        private void makeTheCustomerPremium() {
                userRepository.findById(CUSTOMER_ID)
                                .orElseThrow()
                                .getClientProfile()
                                .setCustomerType(CustomerType.PREMIUM);
        }

        private void storeInTheCartAtPrice(String storedPrice) {
                cart = new Cart(CUSTOMER_ID);
                cart.addOrIncrementItem(PRODUCT_ID, "Auriculares", new BigDecimal(storedPrice), 2);
        }

        private Order placeOrderWith(Product product) {
                prepareCartAndProduct(product);
                enableStockReservation();
                when(paymentGateway.charge(any(), any())).thenReturn(true);
                when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

                orderService.createOrder(CUSTOMER_ID, new CreateOrderDTO("direccion-1", "pago-1"));

                ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
                verify(orderRepository, times(2)).save(orderCaptor.capture());

                return orderCaptor.getValue();
        }

        private void assertChargedAndSaved(Order order, String unitPrice, String total) {
                verify(paymentGateway).charge(any(), eq(new BigDecimal(total)));
                assertEquals(new BigDecimal(total), order.getTotalPrice());
                assertEquals(new BigDecimal(unitPrice), order.getItems().get(0).unitPrice());
                assertEquals(new BigDecimal(total), order.getItems().get(0).subtotal());
        }

        @Test
        @DisplayName("HU12-CP18: si el precio sube tras añadirlo al carrito, se cobra el precio nuevo")
        void shouldChargeTheNewPriceWhenTheProductGotMoreExpensive() {
                Order order = placeOrderWith(productAtCurrentPrice("25.00", null, null));

                assertChargedAndSaved(order, "25.00", "50.00");
        }

        @Test
        @DisplayName("HU12-CP19: si el precio baja tras añadirlo al carrito, se cobra el precio nuevo")
        void shouldChargeTheNewPriceWhenTheProductGotCheaper() {
                Order order = placeOrderWith(productAtCurrentPrice("10.00", null, null));

                assertChargedAndSaved(order, "10.00", "20.00");
        }

        @Test
        @DisplayName("HU12-CP20: si el vendedor cambia el descuento, se cobra con el descuento actual")
        void shouldChargeWithTheCurrentDiscount() {
                storeInTheCartAtPrice("90.00");

                Order order = placeOrderWith(productAtCurrentPrice("100.00", "20", null));

                assertChargedAndSaved(order, "80.00", "160.00");
        }

        @Test
        @DisplayName("HU12-CP21: si el cliente pasa a premium, se cobra con el descuento premium")
        void shouldChargeThePremiumPriceWhenTheCustomerBecamePremium() {
                storeInTheCartAtPrice("100.00");
                makeTheCustomerPremium();

                Order order = placeOrderWith(productAtCurrentPrice("100.00", "10", "5"));

                assertChargedAndSaved(order, "85.00", "170.00");
        }

        @Test
        @DisplayName("HU12-CP22: si el cliente deja de ser premium, se cobra el precio estándar")
        void shouldChargeTheStandardPriceWhenTheCustomerIsNoLongerPremium() {
                storeInTheCartAtPrice("85.00");

                Order order = placeOrderWith(productAtCurrentPrice("100.00", "10", "5"));

                assertChargedAndSaved(order, "90.00", "180.00");
        }

        @Test
        @DisplayName("HU12-CP23: si el producto no tiene precio, se cobra el guardado en el carrito")
        void shouldChargeTheStoredPriceWhenTheProductHasNoPrice() {
                Order order = placeOrderWith(productAtCurrentPrice(null, null, null));

                assertChargedAndSaved(order, "19.99", "39.98");
        }

        @Test
        @DisplayName("HU12-CP24: el resumen del checkout muestra el precio actual y no el guardado")
        void shouldShowTheCurrentPriceInTheCheckoutSummary() {
                prepareCartAndProduct(productAtCurrentPrice("25.00", null, null));

                CheckoutSummaryDTO summary = orderService.getCheckoutSummary(CUSTOMER_ID);

                assertEquals(new BigDecimal("25.00"), summary.items().get(0).unitPrice());
                assertEquals(new BigDecimal("50.00"), summary.items().get(0).subtotal());
                assertEquals(new BigDecimal("50.00"), summary.subtotal());
                assertEquals(new BigDecimal("50.00"), summary.totalPrice());
        }

        @Test
        @DisplayName("HU12-CP25: el resumen del checkout aplica el descuento premium a un cliente premium")
        void shouldShowThePremiumPriceInTheCheckoutSummary() {
                storeInTheCartAtPrice("100.00");
                makeTheCustomerPremium();
                prepareCartAndProduct(productAtCurrentPrice("100.00", "10", "5"));

                CheckoutSummaryDTO summary = orderService.getCheckoutSummary(CUSTOMER_ID);

                assertEquals(new BigDecimal("85.00"), summary.items().get(0).unitPrice());
                assertEquals(new BigDecimal("170.00"), summary.totalPrice());
        }

        @Test
        @DisplayName("HU12-CP26: el total del resumen coincide con el importe que se cobra al confirmar")
        void shouldChargeExactlyTheTotalShownInTheCheckoutSummary() {
                storeInTheCartAtPrice("90.00");
                Product product = productAtCurrentPrice("100.00", "20", null);
                prepareCartAndProduct(product);
                enableStockReservation();
                when(paymentGateway.charge(any(), any())).thenReturn(true);
                when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

                CheckoutSummaryDTO summary = orderService.getCheckoutSummary(CUSTOMER_ID);
                orderService.createOrder(CUSTOMER_ID, new CreateOrderDTO("direccion-1", "pago-1"));

                verify(paymentGateway).charge(any(), eq(summary.totalPrice()));
        }

        @Test
        @DisplayName("HU12-CP27: si el producto no tiene precio, el resumen muestra el guardado en el carrito")
        void shouldShowTheStoredPriceInTheSummaryWhenTheProductHasNoPrice() {
                prepareCartAndProduct(productAtCurrentPrice(null, null, null));

                CheckoutSummaryDTO summary = orderService.getCheckoutSummary(CUSTOMER_ID);

                assertEquals(new BigDecimal("19.99"), summary.items().get(0).unitPrice());
                assertEquals(new BigDecimal("39.98"), summary.items().get(0).subtotal());
        }

        @Test
        void shouldAllowReturningFromShippedToPreparingWithSelectedReason() {
                Product product = productWithStock(4);

                org.springframework.test.util.ReflectionTestUtils.setField(
                                product, "id", PRODUCT_ID);

                org.springframework.test.util.ReflectionTestUtils.setField(
                                product, "sellerId", "seller-1");

                Order order = org.mockito.Mockito.mock(Order.class);

                when(order.getItems()).thenReturn(List.of(new OrderItem(
                                PRODUCT_ID,
                                "Auriculares",
                                1,
                                PRICE,
                                PRICE,
                                null)));

                when(orderRepository.findById("pedido-123"))
                                .thenReturn(Optional.of(order));

                when(productRepository.findAllBySellerId("seller-1"))
                                .thenReturn(List.of(product));

                orderService.updateOrderStatus(
                                "seller-1",
                                "pedido-123",
                                OrderStatus.PREPARANDO,
                                "Incidencia durante la preparación");

                verify(order).setStatus(OrderStatus.PREPARANDO);
                verify(orderRepository).save(order);
        }

        @Test
        void shouldReturnChronologicalTrackingHistoryOnlyForTheOwningClient() {
                Order order = new Order(
                                CUSTOMER_ID,
                                List.of(),
                                BigDecimal.ZERO,
                                new OrderShippingAddress(
                                                "direccion-1",
                                                "Ana López",
                                                "Calle Mayor 1",
                                                null,
                                                "13001",
                                                "Ciudad Real",
                                                "Ciudad Real",
                                                "600123456"),
                                new OrderPaymentMethod("pago-1", null, "1234"),
                                OrderStatus.PAGADO);

                order.setStatus(OrderStatus.PREPARANDO);
                order.setStatus(OrderStatus.ENVIADO);
                order.setStatus(OrderStatus.ENVIADO);

                when(orderRepository.findByIdAndCustomerId("pedido-123", CUSTOMER_ID))
                                .thenReturn(Optional.of(order));

                ShippingTrackingDTO tracking = orderService.getShippingStatusHistory(
                                CUSTOMER_ID,
                                "pedido-123");

                assertEquals(
                                List.of(
                                                OrderStatus.PAGADO,
                                                OrderStatus.PREPARANDO,
                                                OrderStatus.ENVIADO),
                                tracking.statusHistory().stream()
                                                .map(OrderStatusHistoryDTO::status)
                                                .toList());

                assertEquals(order.getEstimatedDeliveryDate(), tracking.estimatedDeliveryDate());

                verify(orderRepository).findByIdAndCustomerId("pedido-123", CUSTOMER_ID);
        }

        @Test
        void shouldClearEstimatedDeliveryDateWhenShipmentReturnsToPreparing() {
                Order order = new Order(
                                CUSTOMER_ID,
                                List.of(),
                                BigDecimal.ZERO,
                                new OrderShippingAddress(
                                                "direccion-1",
                                                "Ana López",
                                                "Calle Mayor 1",
                                                null,
                                                "13001",
                                                "Ciudad Real",
                                                "Ciudad Real",
                                                "600123456"),
                                new OrderPaymentMethod("pago-1", null, "1234"),
                                OrderStatus.PREPARANDO);

                order.setStatus(OrderStatus.ENVIADO);

                assertNotNull(order.getEstimatedDeliveryDate());

                order.setStatus(OrderStatus.PREPARANDO);

                assertNull(order.getEstimatedDeliveryDate());
        }

        @Test
        void shouldHideTrackingHistoryWhenOrderDoesNotBelongToTheClient() {
                when(orderRepository.findByIdAndCustomerId("pedido-123", CUSTOMER_ID))
                                .thenReturn(Optional.empty());

                assertThrows(
                                PedidoNotFoundException.class,
                                () -> orderService.getShippingStatusHistory(CUSTOMER_ID, "pedido-123"));
        }

        @Test
        void shouldRejectReturningToPreviousStatusWithoutJustification() {
                Product product = productWithStock(4);

                org.springframework.test.util.ReflectionTestUtils.setField(
                                product, "id", PRODUCT_ID);

                org.springframework.test.util.ReflectionTestUtils.setField(
                                product, "sellerId", "seller-1");

                Order order = org.mockito.Mockito.mock(Order.class);

                when(order.getStatus()).thenReturn(OrderStatus.ENVIADO);

                when(orderRepository.findById("pedido-123"))
                                .thenReturn(Optional.of(order));

                assertThrows(
                                IllegalArgumentException.class,
                                () -> orderService.updateOrderStatus(
                                                "seller-1",
                                                "pedido-123",
                                                OrderStatus.PREPARANDO,
                                                null));

                verify(orderRepository, never()).save(any(Order.class));
        }

        @Test
        void shouldRejectSellerSettingDeliveredStatus() {
                Order order = new Order(
                                CUSTOMER_ID,
                                List.of(),
                                PRICE,
                                new OrderShippingAddress(
                                                "direccion-1",
                                                "Ana López",
                                                "Calle Mayor 1",
                                                null,
                                                "13001",
                                                "Ciudad Real",
                                                "Ciudad Real",
                                                "600123456"),
                                new OrderPaymentMethod("pago-1", null, "1234"),
                                OrderStatus.ENVIADO);

                when(orderRepository.findById("pedido-123")).thenReturn(Optional.of(order));

                assertThrows(
                                IllegalArgumentException.class,
                                () -> orderService.updateOrderStatus(
                                                "seller-1",
                                                "pedido-123",
                                                OrderStatus.ENTREGADO,
                                                null));

                verify(orderRepository, never()).save(any(Order.class));
        }

        @Test
        void shouldRejectStockChangedAfterCheckoutSummaryWithoutChargingOrSavingOrder() {
                Product firstProduct = productWithStock(5);
                Product secondProduct = productWithStock(5);

                org.springframework.test.util.ReflectionTestUtils.setField(
                                secondProduct, "id", "producto-2");

                prepareCartAndProduct(firstProduct);

                cart.addOrIncrementItem("producto-2", "Ratón", PRICE, 1);

                when(productRepository.findById("producto-2"))
                                .thenReturn(Optional.of(secondProduct));

                when(mongoTemplate.findAndModify(
                                any(Query.class),
                                any(Update.class),
                                any(FindAndModifyOptions.class),
                                eq(Product.class)))
                                .thenReturn(firstProduct)
                                .thenReturn(null);

                enableStockRelease();

                assertThrows(
                                StockInsuficienteException.class,
                                () -> orderService.createOrder(
                                                CUSTOMER_ID,
                                                new CreateOrderDTO("direccion-1", "pago-1")));

                verify(mongoTemplate).updateFirst(
                                any(Query.class),
                                any(Update.class),
                                eq(Product.class));

                verify(paymentGateway, never()).charge(any(), any());
                verify(orderRepository, never()).save(any(Order.class));

                assertEquals(2, cart.getItems().get(0).getQuantity());
                assertEquals(2, cart.getItems().size());
        }

        @Test
        void shouldRestoreReservedStockAndKeepCartWhenPaymentFails() {
                prepareCartAndProduct(productWithStock(5));

                enableStockReservation();
                enableStockRelease();

                when(orderRepository.save(any(Order.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));
                when(paymentGateway.charge(any(), any())).thenReturn(false);

                assertThrows(
                                PagoFallidoException.class,
                                () -> orderService.createOrder(
                                                CUSTOMER_ID,
                                                new CreateOrderDTO("direccion-1", "pago-1")));

                ArgumentCaptor<Order> pendingOrder = ArgumentCaptor.forClass(Order.class);
                verify(orderRepository).save(pendingOrder.capture());
                assertEquals(OrderPaymentStatus.PENDIENTE, pendingOrder.getValue().getPaymentStatus());
                verify(orderRepository).delete(pendingOrder.getValue());
                verify(cartRepository, never()).save(cart);

                verify(mongoTemplate).updateFirst(
                                any(Query.class),
                                any(Update.class),
                                eq(Product.class));

                assertEquals(2, cart.getItems().get(0).getQuantity());
        }

        @Test
        @DisplayName("HU11: si no se puede guardar el pedido, no se cobra y se libera el stock")
        void doesNotChargeAndReleasesStockWhenThePendingOrderCannotBeSaved() {
                prepareCartAndProduct(productWithStock(5));
                enableStockReservation();
                enableStockRelease();

                when(orderRepository.save(any(Order.class)))
                                .thenThrow(new DataAccessResourceFailureException("MongoDB no responde"));

                assertThrows(
                                PedidoNoCompletadoException.class,
                                () -> orderService.createOrder(
                                                CUSTOMER_ID,
                                                new CreateOrderDTO("direccion-1", "pago-1")));

                verify(paymentGateway, never()).charge(any(), any());
                verify(mongoTemplate).updateFirst(
                                any(Query.class),
                                any(Update.class),
                                eq(Product.class));
                verify(cartRepository, never()).save(cart);

                assertEquals(2, cart.getItems().get(0).getQuantity());
        }

        @Test
        @DisplayName("HU11: si falla tras cobrar, se devuelve el cobro, se borra el pedido y se libera el stock")
        void refundsAndReleasesStockWhenThePaidOrderCannotBeSaved() {
                prepareCartAndProduct(productWithStock(5));
                enableStockReservation();
                enableStockRelease();

                when(paymentGateway.charge(any(), any())).thenReturn(true);
                when(orderRepository.save(any(Order.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0))
                                .thenThrow(new DataAccessResourceFailureException("MongoDB no responde"));

                assertThrows(
                                PedidoNoCompletadoException.class,
                                () -> orderService.createOrder(
                                                CUSTOMER_ID,
                                                new CreateOrderDTO("direccion-1", "pago-1")));

                ArgumentCaptor<OrderPaymentMethod> chargedMethod = ArgumentCaptor.forClass(OrderPaymentMethod.class);
                verify(paymentGateway).charge(chargedMethod.capture(), eq(new BigDecimal("39.98")));
                verify(paymentGateway).refund(chargedMethod.getValue(), new BigDecimal("39.98"));

                ArgumentCaptor<Order> pendingOrder = ArgumentCaptor.forClass(Order.class);
                verify(orderRepository, times(2)).save(pendingOrder.capture());
                verify(orderRepository).delete(pendingOrder.getValue());

                verify(mongoTemplate).updateFirst(
                                any(Query.class),
                                any(Update.class),
                                eq(Product.class));
                verify(cartRepository, never()).save(cart);

                assertEquals(2, cart.getItems().get(0).getQuantity());
        }

        @Test
        void shouldRejectInitiallyInsufficientStockWithoutReservingOrCharging() {
                prepareCartAndProduct(productWithStock(1));

                assertThrows(
                                StockInsuficienteException.class,
                                () -> orderService.createOrder(
                                                CUSTOMER_ID,
                                                new CreateOrderDTO("direccion-1", "pago-1")));

                verify(mongoTemplate, never()).findAndModify(
                                any(Query.class),
                                any(Update.class),
                                any(FindAndModifyOptions.class),
                                eq(Product.class));

                verify(paymentGateway, never()).charge(any(), any());
        }

        @Test
        void shouldRejectOrderWhenCartContainsProductsFromMultipleSellers() {
                cart.addOrIncrementItem(
                                "producto-2",
                                "Teclado",
                                new BigDecimal("49.99"),
                                1);

                Product firstProduct = productWithStock(5);

                Product secondProduct = new Product(new ProductDetails(
                                "Teclado",
                                List.of("categoria-1"),
                                5,
                                "Periférico",
                                new BigDecimal("49.99"),
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                null,
                                true,
                                "vendedor-2"));

                org.springframework.test.util.ReflectionTestUtils.setField(
                                secondProduct, "id", "producto-2");

                when(cartRepository.findByCustomerId(CUSTOMER_ID))
                                .thenReturn(Optional.of(cart));

                when(productRepository.findById(PRODUCT_ID))
                                .thenReturn(Optional.of(firstProduct));

                when(productRepository.findById("producto-2"))
                                .thenReturn(Optional.of(secondProduct));

                IllegalArgumentException exception = assertThrows(
                                IllegalArgumentException.class,
                                () -> orderService.createOrder(
                                                CUSTOMER_ID,
                                                new CreateOrderDTO("direccion-1", "pago-1")));

                assertEquals(
                                "No se puede realizar un pedido con productos de diferentes vendedores. "
                                                + "Debes realizar un pedido independiente por cada vendedor.",
                                exception.getMessage());

                verify(orderRepository, never()).save(any(Order.class));
                verify(paymentGateway, never()).charge(any(), any());
        }

        @Test
        void shouldRejectAddressOrPaymentMethodNotOwnedByClient() {
                assertThrows(
                                RecursoCheckoutNoEncontradoException.class,
                                () -> orderService.createOrder(
                                                CUSTOMER_ID,
                                                new CreateOrderDTO("direccion-ajena", "pago-1")));

                verify(paymentGateway, never()).charge(any(), any());
        }

        private void enableStockReservation() {
                Product product = productWithStock(5);

                when(mongoTemplate.findAndModify(
                                any(Query.class),
                                any(Update.class),
                                any(FindAndModifyOptions.class),
                                eq(Product.class)))
                                .thenReturn(product);
        }

        private void prepareCartAndProduct(Product product) {
                when(cartRepository.findByCustomerId(CUSTOMER_ID))
                                .thenReturn(Optional.of(cart));

                when(productRepository.findById(PRODUCT_ID))
                                .thenReturn(Optional.of(product));
        }

        private void enableStockRelease() {
                UpdateResult result = org.mockito.Mockito.mock(UpdateResult.class);

                when(result.wasAcknowledged()).thenReturn(true);
                when(result.getMatchedCount()).thenReturn(1L);

                when(mongoTemplate.updateFirst(
                                any(Query.class),
                                any(Update.class),
                                eq(Product.class)))
                                .thenReturn(result);
        }

        private Product productWithStock(int stock) {
                Product product = new Product(new ProductDetails(
                                "Auriculares",
                                List.of("categoria-1"),
                                stock,
                                "Audio",
                                PRICE,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                null,
                                true,
                                "vendedor-1"));

                org.springframework.test.util.ReflectionTestUtils.setField(
                                product, "id", PRODUCT_ID);

                return product;
        }

        @Test
        void shouldKeepSellerOrderHistoryAfterSellerProductsAreDeleted() {
                String sellerId = "seller-1";

                OrderItem historicalItem = new OrderItem(
                                PRODUCT_ID,
                                "Auriculares",
                                2,
                                PRICE,
                                new BigDecimal("39.98"),
                                "https://example.com/auriculares.jpg",
                                sellerId);

                Order order = new Order(
                                CUSTOMER_ID,
                                List.of(historicalItem),
                                new BigDecimal("39.98"),
                                new OrderShippingAddress(
                                                "direccion-1",
                                                "Ana López",
                                                "Calle Mayor 1",
                                                null,
                                                "13001",
                                                "Ciudad Real",
                                                "Ciudad Real",
                                                "600123456"),
                                new OrderPaymentMethod("pago-1", null, "1234"),
                                OrderStatus.ENVIADO);

                org.springframework.test.util.ReflectionTestUtils.setField(
                                order, "id", "pedido-historico-1");

                when(productRepository.findAllBySellerId(sellerId))
                                .thenReturn(List.of());

                when(orderRepository.findAllByItemsSellerIdOrItemsProductIdInOrderByCreatedAtDesc(
                                sellerId, java.util.Set.of()))
                                .thenReturn(List.of(order));

                List<SellerOrderDTO> result = orderService.getSellerOrders(sellerId);

                assertEquals(1, result.size());
                assertEquals("pedido-historico-1", result.get(0).orderId());
                assertEquals(OrderStatus.ENVIADO, result.get(0).status());

                assertEquals(1, result.get(0).items().size());
                assertEquals(PRODUCT_ID, result.get(0).items().get(0).productId());
                assertEquals("Auriculares", result.get(0).items().get(0).name());

                assertEquals(new BigDecimal("39.98"), result.get(0).total());

                verify(productRepository).findAllBySellerId(sellerId);
                verify(orderRepository)
                                .findAllByItemsSellerIdOrItemsProductIdInOrderByCreatedAtDesc(
                                                sellerId, java.util.Set.of());
        }

        @Test
        void shouldReturnSellerOrdersSortedByCreatedAtDescending() {
                String sellerId = "seller-1";
                OrderItem item1 = new OrderItem(
                                PRODUCT_ID, "Auriculares", 1, PRICE, PRICE, "img.jpg", sellerId);
                Order olderOrder = new Order(
                                CUSTOMER_ID,
                                List.of(item1),
                                PRICE,
                                new OrderShippingAddress("dir-1", "Ana", "Calle 1", null, "13001", "CR", "CR", "600000000"),
                                new OrderPaymentMethod("p-1", null, "1234"),
                                OrderStatus.ENVIADO);
                org.springframework.test.util.ReflectionTestUtils.setField(
                                olderOrder, "id", "pedido-antiguo");
                org.springframework.test.util.ReflectionTestUtils.setField(
                                olderOrder, "createdAt", java.time.LocalDateTime.now().minusDays(3));

                Order newerOrder = new Order(
                                CUSTOMER_ID,
                                List.of(item1),
                                PRICE,
                                new OrderShippingAddress("dir-1", "Ana", "Calle 1", null, "13001", "CR", "CR", "600000000"),
                                new OrderPaymentMethod("p-1", null, "1234"),
                                OrderStatus.PENDIENTE);
                org.springframework.test.util.ReflectionTestUtils.setField(
                                newerOrder, "id", "pedido-reciente");
                org.springframework.test.util.ReflectionTestUtils.setField(
                                newerOrder, "createdAt", java.time.LocalDateTime.now());

                when(productRepository.findAllBySellerId(sellerId)).thenReturn(List.of());
                when(orderRepository.findAllByItemsSellerIdOrItemsProductIdInOrderByCreatedAtDesc(
                                sellerId, java.util.Set.of()))
                                .thenReturn(List.of(olderOrder, newerOrder));

                List<SellerOrderDTO> result = orderService.getSellerOrders(sellerId);

                assertEquals(2, result.size());
                assertEquals("pedido-reciente", result.get(0).orderId());
                assertEquals("pedido-antiguo", result.get(1).orderId());
        }

        @Test
        void shouldAllowSellerToUpdateHistoricalOrderAfterProductsAreDeleted() {
                String sellerId = "seller-1";

                OrderItem historicalItem = new OrderItem(
                                PRODUCT_ID,
                                "Auriculares",
                                2,
                                PRICE,
                                new BigDecimal("39.98"),
                                "https://example.com/auriculares.jpg",
                                sellerId);

                Order order = new Order(
                                CUSTOMER_ID,
                                List.of(historicalItem),
                                new BigDecimal("39.98"),
                                new OrderShippingAddress(
                                                "direccion-1",
                                                "Ana López",
                                                "Calle Mayor 1",
                                                null,
                                                "13001",
                                                "Ciudad Real",
                                                "Ciudad Real",
                                                "600123456"),
                                new OrderPaymentMethod("pago-1", null, "1234"),
                                OrderStatus.PREPARANDO);

                when(orderRepository.findById("pedido-historico-1"))
                                .thenReturn(Optional.of(order));

                // El vendedor ya no tiene productos asociados.
                when(productRepository.findAllBySellerId(sellerId))
                                .thenReturn(List.of());

                orderService.updateOrderStatus(
                                sellerId,
                                "pedido-historico-1",
                                OrderStatus.ENVIADO,
                                null);

                assertEquals(OrderStatus.ENVIADO, order.getStatus());

                verify(orderRepository).save(order);
        }

        @Test
        void shouldHideDeletedCustomerEmailInSellerOrderHistory() {
                String sellerId = "seller-1";

                User deletedCustomer = new User(
                                "cliente.eliminado@esibuy.local",
                                "Cliente",
                                "eliminado",
                                UserRole.CLIENT);
                org.springframework.test.util.ReflectionTestUtils.setField(
                                deletedCustomer, "id", CUSTOMER_ID);

                OrderItem historicalItem = new OrderItem(
                                PRODUCT_ID,
                                "Auriculares",
                                1,
                                PRICE,
                                PRICE,
                                "https://example.com/auriculares.jpg",
                                sellerId);

                Order order = new Order(
                                CUSTOMER_ID,
                                List.of(historicalItem),
                                PRICE,
                                new OrderShippingAddress(
                                                "direccion-1",
                                                "Ana López",
                                                "Calle Mayor 1",
                                                null,
                                                "13001",
                                                "Ciudad Real",
                                                "Ciudad Real",
                                                "600123456"),
                                new OrderPaymentMethod("pago-1", null, "1234"),
                                OrderStatus.ENTREGADO);

                // Simula los datos que quedan después de eliminar la cuenta del cliente.
                order.anonymizeCustomerData();

                org.springframework.test.util.ReflectionTestUtils.setField(
                                order, "id", "pedido-historico-2");
                when(productRepository.findAllBySellerId(sellerId))
                                .thenReturn(List.of());

                when(orderRepository.findAllByItemsSellerIdOrItemsProductIdInOrderByCreatedAtDesc(
                                sellerId, java.util.Set.of()))
                                .thenReturn(List.of(order));

                when(userRepository.findAllById(any()))
                                .thenReturn(List.of(deletedCustomer));

                List<SellerOrderDTO> result = orderService.getSellerOrders(sellerId);

                assertEquals(1, result.size());
                assertEquals("Cliente eliminado", result.get(0).customerName());
                assertNull(result.get(0).customerEmail());

                assertEquals(1, result.get(0).items().size());
                assertEquals(PRICE, result.get(0).total());
        }

        @Test
        void shouldConfirmReceiptOnlyForCustomerOrderInShippedState() {
                Order order = new Order(
                                CUSTOMER_ID,
                                List.of(new OrderItem(
                                                PRODUCT_ID,
                                                "Auriculares",
                                                1,
                                                PRICE,
                                                PRICE,
                                                null,
                                                "seller-1")),
                                PRICE,
                                new OrderShippingAddress(
                                                "direccion-1",
                                                "Ana López",
                                                "Calle Mayor 1",
                                                null,
                                                "13001",
                                                "Ciudad Real",
                                                "Ciudad Real",
                                                "600123456"),
                                new OrderPaymentMethod("pago-1", null, "1234"),
                                OrderStatus.ENVIADO);

                when(orderRepository.findByIdAndCustomerId("pedido-123", CUSTOMER_ID))
                                .thenReturn(Optional.of(order));

                orderService.confirmOrderReceipt(CUSTOMER_ID, "pedido-123");

                assertEquals(OrderStatus.ENTREGADO, order.getStatus());
                verify(orderRepository).save(order);
        }

        @Test
        void shouldRejectReceiptConfirmationWhenOrderIsNotShipped() {
                Order order = new Order(
                                CUSTOMER_ID,
                                List.of(),
                                PRICE,
                                new OrderShippingAddress(
                                                "direccion-1",
                                                "Ana López",
                                                "Calle Mayor 1",
                                                null,
                                                "13001",
                                                "Ciudad Real",
                                                "Ciudad Real",
                                                "600123456"),
                                new OrderPaymentMethod("pago-1", null, "1234"),
                                OrderStatus.PREPARANDO);

                when(orderRepository.findByIdAndCustomerId("pedido-123", CUSTOMER_ID))
                                .thenReturn(Optional.of(order));

                assertThrows(IllegalArgumentException.class,
                                () -> orderService.confirmOrderReceipt(CUSTOMER_ID, "pedido-123"));

                verify(orderRepository, never()).save(any());
        }

}
