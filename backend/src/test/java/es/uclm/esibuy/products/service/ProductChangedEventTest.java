package es.uclm.esibuy.products.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import org.springframework.data.mongodb.gridfs.GridFsTemplate;

import es.uclm.esibuy.categories.model.Category;
import es.uclm.esibuy.categories.repository.CategoryRepository;
import es.uclm.esibuy.products.dto.ProductCreateRequest;
import es.uclm.esibuy.products.event.ProductChangedEvent;
import es.uclm.esibuy.products.model.Product;
import es.uclm.esibuy.products.model.ProductDetails;
import es.uclm.esibuy.products.repository.ProductCatalogRepository;
import es.uclm.esibuy.products.repository.ProductRepository;
import es.uclm.esibuy.users.model.User;
import es.uclm.esibuy.users.model.UserRole;
import es.uclm.esibuy.users.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class ProductChangedEventTest {

    private static final String SELLER_ID = "seller-id";
    private static final String SELLER_EMAIL = "seller@example.com";
    private static final String CATEGORY_ID = "category-id";
    private static final String PRODUCT_ID = "product-id";

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GridFsTemplate gridFsTemplate;

    @Mock
    private ProductCatalogRepository productCatalogRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ProductService productService;
    private Product product;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, categoryRepository, userRepository,
                gridFsTemplate, productCatalogRepository, eventPublisher);

        product = new Product(new ProductDetails(
                "Auriculares", List.of(CATEGORY_ID), 5, null,
                new BigDecimal("25.41"), new BigDecimal("10"), null, null, true, SELLER_ID));
        product.setId(PRODUCT_ID);
    }

    private void stubSeller() {
        User seller = mock(User.class);
        when(seller.getId()).thenReturn(SELLER_ID);
        when(seller.getRole()).thenReturn(UserRole.SELLER);
        when(userRepository.findByEmailIgnoreCase(SELLER_EMAIL)).thenReturn(Optional.of(seller));
    }

    private ProductCreateRequest request(String price, int stock, boolean available) {
        ProductCreateRequest request = new ProductCreateRequest();
        request.setName("Auriculares");
        request.setCategoryIds(List.of(CATEGORY_ID));
        request.setPrice(new BigDecimal(price));
        request.setDiscount(new BigDecimal("10"));
        request.setStock(stock);
        request.setAvailable(available);
        return request;
    }

    private ProductChangedEvent publishedEvent() {
        ArgumentCaptor<ProductChangedEvent> captor = ArgumentCaptor.forClass(ProductChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("HU27-CP01: editar un producto publica un evento con cómo era antes y cómo queda")
    void updatingAProductPublishesTheBeforeAndAfter() {
        stubSeller();
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(categoryRepository.findAllById(List.of(CATEGORY_ID)))
                .thenReturn(List.of(new Category(CATEGORY_ID, "Electrónica")));
        when(productRepository.save(product)).thenReturn(product);

        productService.updateProduct(PRODUCT_ID, request("19.99", 0, false), SELLER_EMAIL);

        ProductChangedEvent event = publishedEvent();
        assertEquals(PRODUCT_ID, event.before().id());
        assertEquals(new BigDecimal("25.41"), event.before().price());
        assertEquals(5, event.before().stock());
        assertTrue(event.before().available());
        assertEquals(new BigDecimal("19.99"), event.after().price());
        assertEquals(0, event.after().stock());
        assertFalse(event.after().available());
    }

    @Test
    @DisplayName("HU27-CP02: la foto de antes no cambia aunque el producto se modifique después")
    void theBeforeSnapshotIsNotAffectedByTheEdit() {
        stubSeller();
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(categoryRepository.findAllById(List.of(CATEGORY_ID)))
                .thenReturn(List.of(new Category(CATEGORY_ID, "Electrónica")));
        when(productRepository.save(product)).thenReturn(product);

        productService.updateProduct(PRODUCT_ID, request("10.00", 9, true), SELLER_EMAIL);

        // El producto ya vale 10.00, pero el evento recuerda lo que valía antes de editarlo
        assertEquals(new BigDecimal("10.00"), product.getPrice());
        assertEquals(new BigDecimal("25.41"), publishedEvent().before().price());
    }

    @Test
    @DisplayName("HU27-CP03: activar o desactivar un producto publica el evento con la disponibilidad de antes y de después")
    void changingTheStatusPublishesTheBeforeAndAfter() {
        product.setAvailable(false);
        User seller = mock(User.class);
        when(seller.getId()).thenReturn(SELLER_ID);
        when(seller.getRole()).thenReturn(UserRole.SELLER);
        when(userRepository.findByEmailIgnoreCase(SELLER_EMAIL)).thenReturn(Optional.of(seller));
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);

        productService.updateProductStatus(PRODUCT_ID, true, SELLER_EMAIL);

        ProductChangedEvent event = publishedEvent();
        assertFalse(event.before().available());
        assertTrue(event.after().available());
    }

    @Test
    @DisplayName("HU27-CP04: si el guardado falla no se publica ningún evento")
    void noEventIsPublishedWhenSavingFails() {
        stubSeller();
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(categoryRepository.findAllById(List.of(CATEGORY_ID)))
                .thenReturn(List.of(new Category(CATEGORY_ID, "Electrónica")));
        when(productRepository.save(product)).thenThrow(new IllegalStateException("Mongo caído"));

        assertThrows(IllegalStateException.class,
                () -> productService.updateProduct(PRODUCT_ID, request("19.99", 5, true), SELLER_EMAIL));

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("HU27-CP05: borrar un producto no publica ningún evento (RN-16)")
    void deletingAProductPublishesNothing() {
        stubSeller();
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));

        productService.deleteProduct(PRODUCT_ID, SELLER_EMAIL);

        verify(productRepository).delete(product);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("HU27-CP06: crear un producto no publica ningún evento")
    void creatingAProductPublishesNothing() {
        stubSeller();
        when(categoryRepository.findAllById(List.of(CATEGORY_ID)))
                .thenReturn(List.of(new Category(CATEGORY_ID, "Electrónica")));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        productService.createProduct(request("19.99", 5, true), SELLER_EMAIL);

        verify(productRepository).save(any(Product.class));
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }
}
