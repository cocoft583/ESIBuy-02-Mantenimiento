package es.uclm.esibuy.orders.hdu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import es.uclm.esibuy.common.exception.PedidoNotFoundException;
import es.uclm.esibuy.cart.repository.CartRepository;
import es.uclm.esibuy.configuration.service.GeneralConfigurationService;
import es.uclm.esibuy.orders.model.Order;
import es.uclm.esibuy.orders.model.OrderItem;
import es.uclm.esibuy.orders.model.OrderPaymentStatus;
import es.uclm.esibuy.orders.model.OrderStatus;
import es.uclm.esibuy.orders.repository.OrderRepository;
import es.uclm.esibuy.orders.service.OrderService;
import es.uclm.esibuy.orders.service.PaymentGateway;
import es.uclm.esibuy.products.model.Product;
import es.uclm.esibuy.products.repository.ProductRepository;
import es.uclm.esibuy.users.model.ClientProfile;
import es.uclm.esibuy.users.model.CustomerType;
import es.uclm.esibuy.users.model.User;
import es.uclm.esibuy.users.model.UserRole;
import es.uclm.esibuy.users.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class CancelarPedidoTest {

    private static final String CUSTOMER_ID = "cliente-1";
    private static final String ORDER_ID = "pedido-123";

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

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private Order order;

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

        when(userRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(createCustomer()));
        when(orderRepository.findByIdAndCustomerId(ORDER_ID, CUSTOMER_ID))
                .thenReturn(Optional.of(order));
    }

    private User createCustomer() {
        User customer = new User(
                "cliente@example.com",
                "Ana",
                "López",
                UserRole.CLIENT);
        customer.setClientProfile(new ClientProfile(null, CustomerType.STANDARD));
        return customer;
    }

    @Test
    void unClientePuedeCancelarSuPedidoPendientePagado() {
        // Arrange
        when(order.getStatus()).thenReturn(OrderStatus.PENDIENTE);
        when(order.getPaymentStatus()).thenReturn(OrderPaymentStatus.PAGADO);

        // Act
        orderService.cancelCustomerOrder(CUSTOMER_ID, ORDER_ID);

        // Assert
        verify(order).setStatus(OrderStatus.CANCELADO);
        verify(orderRepository).save(order);
    }

    @Test
    void noSePuedeCancelarUnPedidoEnviado() {
        // Arrange
        when(order.getStatus()).thenReturn(OrderStatus.ENVIADO);

        // Act and Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.cancelCustomerOrder(CUSTOMER_ID, ORDER_ID));

        // Assert
        verify(orderRepository, never()).save(order);
        assertEquals(
                "El pedido ya no se puede cancelar en su estado actual",
                exception.getMessage());
    }

    @Test
    void alCancelarSeReponeElStockDeLosProductos() {
        // Arrange
        when(order.getItems()).thenReturn(List.of(createOrderItem()));
        when(order.getStatus()).thenReturn(OrderStatus.PENDIENTE);
        when(order.getPaymentStatus()).thenReturn(OrderPaymentStatus.PAGADO);

        when(mongoTemplate.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(Product.class)))
                .thenReturn(mock(Product.class));

        // Act
        orderService.cancelCustomerOrder(CUSTOMER_ID, ORDER_ID);

        // Assert: la reposición suma las unidades con un $inc atómico, sin leer ni guardar el producto
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(
                any(Query.class),
                update.capture(),
                any(FindAndModifyOptions.class),
                eq(Product.class));
        assertEquals(
                new Document("$inc", new Document("stock", 2)),
                update.getValue().getUpdateObject());
        verify(productRepository, never()).save(any());
    }

    @Test
    void siNoSePuedeReponerElStockNoSeCancelaElPedido() {
        // Arrange
        when(order.getItems()).thenReturn(List.of(createOrderItem()));
        when(order.getStatus()).thenReturn(OrderStatus.PENDIENTE);
        when(order.getPaymentStatus()).thenReturn(OrderPaymentStatus.PAGADO);
        when(mongoTemplate.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(Product.class)))
                .thenReturn(null);

        // Act and Assert
        assertThrows(
                IllegalStateException.class,
                () -> orderService.cancelCustomerOrder(CUSTOMER_ID, ORDER_ID));

        // Assert
        verify(orderRepository, never()).save(order);
        verify(order, never()).setStatus(OrderStatus.CANCELADO);
    }

    @Test
    void unClienteNoPuedeCancelarUnPedidoDeOtroCliente() {
        // Arrange
        when(orderRepository.findByIdAndCustomerId(ORDER_ID, CUSTOMER_ID))
                .thenReturn(Optional.empty());

        // Act and Assert
        assertThrows(
                PedidoNotFoundException.class,
                () -> orderService.cancelCustomerOrder(CUSTOMER_ID, ORDER_ID));

        // Assert
        verify(orderRepository, never()).save(order);
        verify(order, never()).setStatus(OrderStatus.CANCELADO);
    }

    @Test
    void noSePuedeCancelarUnPedidoInexistente() {
        // Arrange
        when(orderRepository.findByIdAndCustomerId(ORDER_ID, CUSTOMER_ID))
                .thenReturn(Optional.empty());

        // Act and Assert
        assertThrows(
                PedidoNotFoundException.class,
                () -> orderService.cancelCustomerOrder(CUSTOMER_ID, ORDER_ID));

        // Assert
        verify(orderRepository, never()).save(order);
        verify(order, never()).setStatus(OrderStatus.CANCELADO);
    }

    @Test
    void noSePuedeCancelarUnPedidoConPagoPendiente() {
        // Arrange
        when(order.getStatus()).thenReturn(OrderStatus.PENDIENTE);
        when(order.getPaymentStatus()).thenReturn(OrderPaymentStatus.PENDIENTE);

        // Act and Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.cancelCustomerOrder(CUSTOMER_ID, ORDER_ID));

        // Assert
        assertEquals(
                "El pedido ya no se puede cancelar en su estado actual",
                exception.getMessage());
        verify(orderRepository, never()).save(order);
        verify(order, never()).setStatus(OrderStatus.CANCELADO);
    }

    @Test
    @DisplayName("HU14-CP01: dos cancelaciones del mismo producto reponen cada una sus unidades con un $inc")
    void twoCancellationsOfTheSameProductEachRestoreTheirOwnUnits() {
        // Arrange
        when(order.getItems()).thenReturn(List.of(createOrderItem()));
        when(order.getStatus()).thenReturn(OrderStatus.PENDIENTE);
        when(order.getPaymentStatus()).thenReturn(OrderPaymentStatus.PAGADO);

        Order otherOrder = mock(Order.class);
        when(orderRepository.findByIdAndCustomerId("pedido-456", CUSTOMER_ID))
                .thenReturn(Optional.of(otherOrder));
        when(otherOrder.getItems()).thenReturn(List.of(new OrderItem(
                "producto-1",
                "Producto de prueba",
                3,
                BigDecimal.TEN,
                BigDecimal.valueOf(30))));
        when(otherOrder.getStatus()).thenReturn(OrderStatus.PENDIENTE);
        when(otherOrder.getPaymentStatus()).thenReturn(OrderPaymentStatus.PAGADO);

        when(mongoTemplate.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(Product.class)))
                .thenReturn(mock(Product.class));

        // Act
        orderService.cancelCustomerOrder(CUSTOMER_ID, ORDER_ID);
        orderService.cancelCustomerOrder(CUSTOMER_ID, "pedido-456");

        // Assert: cada cancelación suma lo suyo sin partir del stock leído por la otra
        ArgumentCaptor<Update> updates = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, times(2)).findAndModify(
                any(Query.class),
                updates.capture(),
                any(FindAndModifyOptions.class),
                eq(Product.class));
        assertEquals(
                List.of(
                        new Document("$inc", new Document("stock", 2)),
                        new Document("$inc", new Document("stock", 3))),
                updates.getAllValues().stream().map(Update::getUpdateObject).toList());
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("HU14-CP02: un pedido con varias líneas repone cada producto con su propio $inc")
    void anOrderWithSeveralLinesRestoresEachProductWithItsOwnIncrement() {
        // Arrange
        when(order.getItems()).thenReturn(List.of(
                createOrderItem(),
                new OrderItem(
                        "producto-2",
                        "Otro producto",
                        4,
                        BigDecimal.TEN,
                        BigDecimal.valueOf(40))));
        when(order.getStatus()).thenReturn(OrderStatus.PENDIENTE);
        when(order.getPaymentStatus()).thenReturn(OrderPaymentStatus.PAGADO);
        when(mongoTemplate.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(Product.class)))
                .thenReturn(mock(Product.class));

        // Act
        orderService.cancelCustomerOrder(CUSTOMER_ID, ORDER_ID);

        // Assert
        ArgumentCaptor<Query> queries = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updates = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, times(2)).findAndModify(
                queries.capture(),
                updates.capture(),
                any(FindAndModifyOptions.class),
                eq(Product.class));
        assertEquals(
                List.of(new Document("_id", "producto-1"), new Document("_id", "producto-2")),
                queries.getAllValues().stream().map(Query::getQueryObject).toList());
        assertEquals(
                List.of(
                        new Document("$inc", new Document("stock", 2)),
                        new Document("$inc", new Document("stock", 4))),
                updates.getAllValues().stream().map(Update::getUpdateObject).toList());
        verify(order).setStatus(OrderStatus.CANCELADO);
    }

    private OrderItem createOrderItem() {
        return new OrderItem(
                "producto-1",
                "Producto de prueba",
                2,
                BigDecimal.TEN,
                BigDecimal.valueOf(20));
    }
}
