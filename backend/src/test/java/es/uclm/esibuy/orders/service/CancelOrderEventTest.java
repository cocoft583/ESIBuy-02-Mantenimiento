package es.uclm.esibuy.orders.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

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

import es.uclm.esibuy.cart.repository.CartRepository;
import es.uclm.esibuy.configuration.service.GeneralConfigurationService;
import es.uclm.esibuy.orders.model.Order;
import es.uclm.esibuy.orders.model.OrderItem;
import es.uclm.esibuy.orders.model.OrderPaymentStatus;
import es.uclm.esibuy.orders.model.OrderStatus;
import es.uclm.esibuy.orders.repository.OrderRepository;
import es.uclm.esibuy.products.event.ProductChangedEvent;
import es.uclm.esibuy.products.model.Product;
import es.uclm.esibuy.products.model.ProductDetails;
import es.uclm.esibuy.products.repository.ProductRepository;
import es.uclm.esibuy.users.model.ClientProfile;
import es.uclm.esibuy.users.model.CustomerType;
import es.uclm.esibuy.users.model.User;
import es.uclm.esibuy.users.model.UserRole;
import es.uclm.esibuy.users.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class CancelOrderEventTest {

    private static final String CUSTOMER_ID = "cliente-1";
    private static final String ORDER_ID = "pedido-1";

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
        orderService = new OrderService(userRepository, cartRepository, productRepository, orderRepository,
                mongoTemplate, paymentGateway, generalConfigurationService, eventPublisher);

        User customer = new User("cliente@example.com", "Ana", "López", UserRole.CLIENT);
        customer.setClientProfile(new ClientProfile(null, CustomerType.STANDARD));
        when(userRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
        when(orderRepository.findByIdAndCustomerId(ORDER_ID, CUSTOMER_ID)).thenReturn(Optional.of(order));
        when(order.getStatus()).thenReturn(OrderStatus.PENDIENTE);
        when(order.getPaymentStatus()).thenReturn(OrderPaymentStatus.PAGADO);
    }

    private OrderItem item(String productId, int quantity) {
        return new OrderItem(productId, "Producto " + productId, quantity, BigDecimal.TEN,
                BigDecimal.TEN.multiply(BigDecimal.valueOf(quantity)));
    }

    private Product restoredProduct(String id, int stockAfterRestoring, boolean available) {
        Product product = new Product(new ProductDetails(
                "Producto " + id, List.of("cat"), stockAfterRestoring, null,
                BigDecimal.TEN, null, null, null, available, "vendedor-1"));
        product.setId(id);
        return product;
    }

    private void stubRestore(Product... restored) {
        org.mockito.stubbing.OngoingStubbing<Product> stubbing = when(mongoTemplate.findAndModify(
                any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Product.class)));
        for (Product product : restored) {
            stubbing = stubbing.thenReturn(product);
        }
    }

    @Test
    @DisplayName("HU27-CP07: cancelar un pedido publica un evento con el stock de antes y el de después")
    void cancellingAnOrderPublishesTheStockBeforeAndAfter() {
        when(order.getItems()).thenReturn(List.of(item("p1", 2)));
        stubRestore(restoredProduct("p1", 2, true));

        orderService.cancelCustomerOrder(CUSTOMER_ID, ORDER_ID);

        ArgumentCaptor<ProductChangedEvent> captor = ArgumentCaptor.forClass(ProductChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        ProductChangedEvent event = captor.getValue();
        assertEquals("p1", event.after().id());
        assertEquals(0, event.before().stock());
        assertFalse(event.before().isPurchasable());
        assertEquals(2, event.after().stock());
        assertTrue(event.after().isPurchasable());
    }

    @Test
    @DisplayName("HU27-CP08: un pedido con dos productos publica un evento por cada uno")
    void anOrderWithTwoProductsPublishesOneEventForEach() {
        when(order.getItems()).thenReturn(List.of(item("p1", 1), item("p2", 3)));
        stubRestore(restoredProduct("p1", 6, true), restoredProduct("p2", 3, true));

        orderService.cancelCustomerOrder(CUSTOMER_ID, ORDER_ID);

        ArgumentCaptor<ProductChangedEvent> captor = ArgumentCaptor.forClass(ProductChangedEvent.class);
        verify(eventPublisher, times(2)).publishEvent(captor.capture());
        assertEquals(List.of("p1", "p2"), captor.getAllValues().stream().map(e -> e.after().id()).toList());
        assertEquals(5, captor.getAllValues().get(0).before().stock());
        assertEquals(0, captor.getAllValues().get(1).before().stock());
    }

    @Test
    @DisplayName("HU27-CP09: si no se puede reponer el stock no se cancela ni se publica nada")
    void nothingIsPublishedWhenRestoringTheStockFails() {
        when(order.getItems()).thenReturn(List.of(item("p1", 2)));
        stubRestore((Product) null);

        assertThrows(IllegalStateException.class, () -> orderService.cancelCustomerOrder(CUSTOMER_ID, ORDER_ID));

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("HU27-CP10: los eventos se publican después de guardar el pedido cancelado")
    void eventsArePublishedAfterTheOrderIsSaved() {
        when(order.getItems()).thenReturn(List.of(item("p1", 2)));
        stubRestore(restoredProduct("p1", 2, true));

        orderService.cancelCustomerOrder(CUSTOMER_ID, ORDER_ID);

        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(orderRepository, eventPublisher);
        inOrder.verify(orderRepository).save(order);
        inOrder.verify(eventPublisher).publishEvent(any(ProductChangedEvent.class));
    }
}
