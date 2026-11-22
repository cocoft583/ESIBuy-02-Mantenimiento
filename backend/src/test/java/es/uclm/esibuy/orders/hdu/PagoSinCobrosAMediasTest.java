package es.uclm.esibuy.orders.hdu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.util.ReflectionTestUtils;

import com.mongodb.client.result.UpdateResult;

import es.uclm.esibuy.cart.model.Cart;
import es.uclm.esibuy.cart.repository.CartRepository;
import es.uclm.esibuy.common.exception.PagoFallidoException;
import es.uclm.esibuy.common.exception.PedidoNoCompletadoException;
import es.uclm.esibuy.configuration.model.GeneralConfiguration;
import es.uclm.esibuy.configuration.service.GeneralConfigurationService;
import es.uclm.esibuy.orders.dto.CreateOrderDTO;
import es.uclm.esibuy.orders.dto.CustomerOrderDTO;
import es.uclm.esibuy.orders.model.Order;
import es.uclm.esibuy.orders.model.OrderPaymentMethod;
import es.uclm.esibuy.orders.model.OrderPaymentStatus;
import es.uclm.esibuy.orders.repository.OrderRepository;
import es.uclm.esibuy.orders.service.OrderService;
import es.uclm.esibuy.orders.service.PaymentGateway;
import es.uclm.esibuy.products.model.Product;
import es.uclm.esibuy.products.model.ProductDetails;
import es.uclm.esibuy.products.repository.ProductRepository;
import es.uclm.esibuy.users.model.ClientProfile;
import es.uclm.esibuy.users.model.CustomerType;
import es.uclm.esibuy.users.model.PaymentMethod;
import es.uclm.esibuy.users.model.ShippingAddress;
import es.uclm.esibuy.users.model.User;
import es.uclm.esibuy.users.model.UserRole;
import es.uclm.esibuy.users.repository.UserRepository;

/**
 * HU11 - Pago sin cobros a medias. Comprueba los criterios de aceptación por el estado final
 * (lo cobrado, los pedidos guardados, el stock y el carrito) y no por las llamadas internas.
 */
@ExtendWith(MockitoExtension.class)
class PagoSinCobrosAMediasTest {

    private static final String CUSTOMER_ID = "cliente-1";
    private static final String PRODUCT_ID = "producto-1";
    private static final int STOCK = 5;
    private static final int UNITS = 2;
    private static final BigDecimal TOTAL = new BigDecimal("39.98");

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
    private GeneralConfigurationService generalConfigurationService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final FakePaymentGateway paymentGateway = new FakePaymentGateway();
    private final List<Order> storedOrders = new ArrayList<>();
    private int saveCalls;
    private int failingSave;
    private int stock = STOCK;
    private Cart cart;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                userRepository,
                cartRepository,
                productRepository,
                orderRepository,
                mongoTemplate,
                paymentGateway,
                generalConfigurationService,
                eventPublisher);

        cart = new Cart(CUSTOMER_ID);
        cart.addOrIncrementItem(PRODUCT_ID, "Auriculares", new BigDecimal("19.99"), UNITS);

        lenient().when(userRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer()));
        lenient().when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(cart));
        lenient().when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product()));
        lenient().when(generalConfigurationService.getCurrent()).thenReturn(new GeneralConfiguration(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                true));

        simulateStockInMongo();
        simulateOrdersInMongo();
    }

    @Test
    @DisplayName("HU11-CP01: si se cobra, el pedido pagado aparece en el historial y el carrito se vacía")
    void chargedOrderShowsUpInTheHistory() {
        orderService.createOrder(CUSTOMER_ID, new CreateOrderDTO("direccion-1", "pago-1"));

        List<CustomerOrderDTO> history = orderService.getCustomerOrders(CUSTOMER_ID);
        assertEquals(1, history.size());
        assertEquals(OrderPaymentStatus.PAGADO, history.get(0).paymentStatus());
        assertEquals(money(TOTAL), money(paymentGateway.netCharged()));
        assertEquals(STOCK - UNITS, stock);
        assertTrue(cart.getItems().isEmpty());
        assertNoChargeWithoutOrder();
    }

    @Test
    @DisplayName("HU11-CP02: si el pago se rechaza, no se cobra, no queda pedido y las unidades vuelven")
    void rejectedPaymentLeavesNothingBehind() {
        paymentGateway.rejectCharges();

        assertThrows(PagoFallidoException.class, this::placeOrder);

        assertNothingChargedAndNothingChanged();
    }

    @Test
    @DisplayName("HU11-CP03: si la pasarela falla, no se cobra, no queda pedido y las unidades vuelven")
    void gatewayFailureLeavesNothingBehind() {
        paymentGateway.failOnCharge();

        assertThrows(IllegalStateException.class, this::placeOrder);

        assertNothingChargedAndNothingChanged();
    }

    @Test
    @DisplayName("HU11-CP04: si no se puede guardar el pedido, no se cobra y el aviso dice que no se ha cobrado")
    void orderThatCannotBeSavedIsNotCharged() {
        failingSave = 1;

        PedidoNoCompletadoException exception = assertThrows(PedidoNoCompletadoException.class, this::placeOrder);

        assertTrue(exception.getMessage().contains("No se te ha cobrado nada"));
        assertNothingChargedAndNothingChanged();
    }

    @Test
    @DisplayName("HU11-CP05: si falla después de cobrar, se devuelve el cobro y no queda pedido")
    void failureAfterChargingIsRefunded() {
        failingSave = 2;

        PedidoNoCompletadoException exception = assertThrows(PedidoNoCompletadoException.class, this::placeOrder);

        assertTrue(exception.getMessage().contains("No se te ha cobrado nada"));
        assertEquals(money(TOTAL), money(paymentGateway.charged), "se llegó a cobrar");
        assertEquals(money(TOTAL), money(paymentGateway.refunded), "se devolvió lo cobrado");
        assertNothingChargedAndNothingChanged();
    }

    private void placeOrder() {
        orderService.createOrder(CUSTOMER_ID, new CreateOrderDTO("direccion-1", "pago-1"));
    }

    private void assertNothingChargedAndNothingChanged() {
        assertEquals("0.00", money(paymentGateway.netCharged()), "no queda nada cobrado");
        assertTrue(orderService.getCustomerOrders(CUSTOMER_ID).isEmpty(), "no queda ningún pedido");
        assertEquals(STOCK, stock, "las unidades vuelven a estar disponibles");
        assertEquals(UNITS, cart.getItems().get(0).getQuantity(), "el carrito no cambia");
        verify(cartRepository, never()).save(any(Cart.class));
        assertNoChargeWithoutOrder();
    }

    // La invariante de la HU: lo cobrado coincide siempre con lo que suman los pedidos pagados
    private void assertNoChargeWithoutOrder() {
        BigDecimal paidOrders = storedOrders.stream()
                .filter(order -> order.getPaymentStatus() == OrderPaymentStatus.PAGADO)
                .map(Order::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(money(paidOrders), money(paymentGateway.netCharged()), "hay un cobro sin pedido");
    }

    private static String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private void simulateStockInMongo() {
        lenient().when(mongoTemplate.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(Product.class)))
                .thenAnswer(invocation -> {
                    stock -= UNITS;
                    return product();
                });

        lenient().when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Product.class)))
                .thenAnswer(invocation -> {
                    stock += UNITS;
                    return UpdateResult.acknowledged(1, 1L, null);
                });
    }

    private void simulateOrdersInMongo() {
        lenient().when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            saveCalls++;
            if (saveCalls == failingSave) {
                throw new DataAccessResourceFailureException("MongoDB no responde");
            }
            Order order = invocation.getArgument(0);
            if (!storedOrders.contains(order)) {
                storedOrders.add(order);
            }
            return order;
        });

        lenient().doAnswer(invocation -> storedOrders.remove(invocation.<Order>getArgument(0)))
                .when(orderRepository).delete(any(Order.class));

        lenient().when(orderRepository.findAllByCustomerIdOrderByCreatedAtDesc(CUSTOMER_ID))
                .thenAnswer(invocation -> List.copyOf(storedOrders));
    }

    private User customer() {
        User customer = new User("cliente@example.com", "Ana", "López", UserRole.CLIENT);
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
        return customer;
    }

    private Product product() {
        Product product = new Product(new ProductDetails(
                "Auriculares",
                List.of("categoria-1"),
                stock,
                "Audio",
                new BigDecimal("19.99"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                true,
                "vendedor-1"));
        ReflectionTestUtils.setField(product, "id", PRODUCT_ID);
        return product;
    }

    /** Pasarela de pago que lleva la cuenta de lo cobrado y lo devuelto. */
    private static final class FakePaymentGateway implements PaymentGateway {

        private BigDecimal charged = BigDecimal.ZERO;
        private BigDecimal refunded = BigDecimal.ZERO;
        private boolean reject;
        private boolean fail;

        void rejectCharges() {
            reject = true;
        }

        void failOnCharge() {
            fail = true;
        }

        BigDecimal netCharged() {
            return charged.subtract(refunded);
        }

        @Override
        public boolean charge(OrderPaymentMethod paymentMethod, BigDecimal amount) {
            if (fail) {
                throw new IllegalStateException("La pasarela no responde");
            }
            if (reject) {
                return false;
            }
            charged = charged.add(amount);
            return true;
        }

        @Override
        public void refund(OrderPaymentMethod paymentMethod, BigDecimal amount) {
            refunded = refunded.add(amount);
        }
    }
}
