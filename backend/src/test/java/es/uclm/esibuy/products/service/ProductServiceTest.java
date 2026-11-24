package es.uclm.esibuy.products.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import javax.imageio.ImageIO;
import java.util.List;
import java.util.Optional;

import es.uclm.esibuy.categories.model.Category;
import es.uclm.esibuy.categories.repository.CategoryRepository;
import es.uclm.esibuy.common.exception.CategoryNotFoundException;
import es.uclm.esibuy.common.exception.ProductNotFoundException;
import es.uclm.esibuy.products.dto.CatalogPageResponse;
import es.uclm.esibuy.products.dto.CatalogSearch;
import es.uclm.esibuy.products.dto.ProductCreateRequest;
import es.uclm.esibuy.products.dto.ProductResponse;
import es.uclm.esibuy.products.dto.ProductSummaryResponse;
import es.uclm.esibuy.products.model.Product;
import es.uclm.esibuy.products.repository.ProductCatalogRepository;
import es.uclm.esibuy.products.repository.ProductCatalogRepository.CatalogResult;
import es.uclm.esibuy.products.repository.ProductRepository;
import es.uclm.esibuy.users.model.SellerProfile;
import es.uclm.esibuy.users.model.User;
import es.uclm.esibuy.users.model.UserRole;
import es.uclm.esibuy.users.repository.UserRepository;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.bson.types.ObjectId;
import org.springframework.web.server.ResponseStatusException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import es.uclm.esibuy.users.model.ClientProfile;
import es.uclm.esibuy.users.model.CustomerType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import es.uclm.esibuy.products.event.ProductCreatedEvent;
import es.uclm.esibuy.products.event.ProductSnapshot;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import es.uclm.esibuy.products.model.ProductDetails;
import es.uclm.esibuy.products.dto.ProductSummaryDTO;
import es.uclm.esibuy.products.dto.ProductDetailDTO;
import es.uclm.esibuy.common.exception.ProductoDesactivadoException;
import es.uclm.esibuy.security.AuthenticatedUserPrincipal;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

        private static final String SELLER_ID = "seller-id";
        private static final String SELLER_EMAIL = "seller@example.com";
        private static final String CLIENT_EMAIL = "client@example.com";
        private static final String CATEGORY_ID = "category-id";
        private static final String PRODUCT_NAME = "Auriculares";
        private static final String PRODUCT_PRICE = "19.99";
        private static final String OTHER_SELLER_ID = "other-seller-id";
        private static final String ADMIN_EMAIL = "admin@example.com";
        private static final String PRODUCT_ID = "product-id";
        private static final String CATEGORY_NAME_ELECTRONICS = "Electrónica";
        private static final String UNKNOWN_ID = "unknown";
        private static final String DESC_TEXT = "Descripción";

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

        @BeforeEach
        void setUp() {
                productService = new ProductService(productRepository, categoryRepository, userRepository,
                                gridFsTemplate, productCatalogRepository, eventPublisher);
        }

        @Test
        void shouldRejectImageWhenMimeDoesNotMatchBinarySignature() {
                stubSellerAndOwnedProduct();
                MockMultipartFile image = new MockMultipartFile(
                                "image", "photo.png", "image/png", "not an image".getBytes());

                assertThrows(ResponseStatusException.class,
                                () -> productService.uploadProductImage("product-id", image, SELLER_EMAIL));
                verify(productRepository, never()).save(any(Product.class));
        }

        @Test
        void shouldRejectImagesLargerThanFiveMegabytes() {
                stubSellerAndOwnedProduct();
                MockMultipartFile image = new MockMultipartFile(
                                "image", "photo.png", "image/png", new byte[5 * 1024 * 1024 + 1]);

                assertThrows(ResponseStatusException.class,
                                () -> productService.uploadProductImage("product-id", image, SELLER_EMAIL));
                verify(productRepository, never()).save(any(Product.class));
        }

        @Test
        void shouldNormalizeAndStoreValidPngImage() throws IOException {
                stubSellerAndOwnedProduct();
                BufferedImage source = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ImageIO.write(source, "png", bytes);
                MockMultipartFile image = new MockMultipartFile(
                                "image", "photo.png", "image/png", bytes.toByteArray());
                when(gridFsTemplate.store(any(java.io.InputStream.class), any(String.class), any(String.class)))
                                .thenReturn(new ObjectId("507f1f77bcf86cd799439011"));
                when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

                Product result = productService.uploadProductImage("product-id", image, SELLER_EMAIL);

                assertEquals("/api/products/product-id/image", result.getImageUrl());
                assertEquals("image/png", result.getImageContentType());
                org.junit.jupiter.api.Assertions.assertNotNull(result.getImageFileId());
                verify(productRepository).save(any(Product.class));
        }

        private void stubSellerAndOwnedProduct() {
                User seller = org.mockito.Mockito.mock(User.class);
                when(seller.getId()).thenReturn(SELLER_ID);
                when(seller.getRole()).thenReturn(UserRole.SELLER);
                when(userRepository.findByEmailIgnoreCase(SELLER_EMAIL)).thenReturn(Optional.of(seller));
                Product product = new Product(new ProductDetails(
                                PRODUCT_NAME,
                                List.of(CATEGORY_ID),
                                1,
                                null,
                                new BigDecimal(PRODUCT_PRICE),
                                null,
                                null,
                                null,
                                true,
                                SELLER_ID));
                product.setId("product-id");
                when(productRepository.findById("product-id")).thenReturn(Optional.of(product));
        }

        @Test
        void shouldCreateProductForAuthenticatedSellerAndDefaultStockToZero() {
                ProductCreateRequest request = validRequest();
                User seller = org.mockito.Mockito.mock(User.class);
                when(seller.getId()).thenReturn(SELLER_ID);
                when(seller.getRole()).thenReturn(UserRole.SELLER);
                when(userRepository.findByEmailIgnoreCase(SELLER_EMAIL)).thenReturn(Optional.of(seller));
                when(categoryRepository.findAllById(List.of(CATEGORY_ID)))
                                .thenReturn(List.of(new Category(CATEGORY_ID, "Electr├│nica")));
                when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

                Product result = productService.createProduct(request, SELLER_EMAIL);

                assertEquals(SELLER_ID, result.getSellerId());
                assertEquals(List.of(CATEGORY_ID), result.getCategoryIds());
                assertEquals(0, result.getStock());
                assertEquals(new BigDecimal(PRODUCT_PRICE), result.getPrice());
                verify(productRepository).save(any(Product.class));
        }

        @Test
        void shouldListOnlyAvailableProductsWithStockForCustomerCatalog() {
                Product product = new Product(new ProductDetails(
                                PRODUCT_NAME,
                                List.of(CATEGORY_ID),
                                3,
                                null,
                                new BigDecimal(PRODUCT_PRICE),
                                null,
                                null,
                                null,
                                true,
                                SELLER_ID));
                when(productRepository.findAllByAvailableTrueAndStockGreaterThan(0)).thenReturn(List.of(product));

                List<ProductResponse> result = productService.getAvailableProducts();

                assertEquals(1, result.size());
                assertEquals(PRODUCT_NAME, result.get(0).name());
                assertEquals(3, result.get(0).stock());
                verify(productRepository).findAllByAvailableTrueAndStockGreaterThan(0);
        }

        @Test
        void shouldPreserveProvidedStockAndDiscounts() {
                ProductCreateRequest request = validRequest();
                request.setStock(7);
                request.setDiscount(new BigDecimal("10"));
                request.setPremiumDiscount(new BigDecimal("20"));
                User seller = org.mockito.Mockito.mock(User.class);
                when(seller.getId()).thenReturn(SELLER_ID);
                when(seller.getRole()).thenReturn(UserRole.SELLER);
                when(userRepository.findByEmailIgnoreCase(SELLER_EMAIL)).thenReturn(Optional.of(seller));
                when(categoryRepository.findAllById(List.of(CATEGORY_ID)))
                                .thenReturn(List.of(new Category(CATEGORY_ID, "Electr├│nica")));
                when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

                Product result = productService.createProduct(request, SELLER_EMAIL);

                assertEquals(7, result.getStock());
                assertEquals(new BigDecimal("10"), result.getDiscount());
                assertEquals(new BigDecimal("20"), result.getPremiumDiscount());
        }

        @Test
        void shouldRejectUnknownCategoriesWithoutSavingProduct() {
                ProductCreateRequest request = validRequest();
                User seller = org.mockito.Mockito.mock(User.class);
                when(seller.getRole()).thenReturn(UserRole.SELLER);
                when(userRepository.findByEmailIgnoreCase(SELLER_EMAIL)).thenReturn(Optional.of(seller));
                when(categoryRepository.findAllById(List.of(CATEGORY_ID))).thenReturn(List.of());

                assertThrows(CategoryNotFoundException.class,
                                () -> productService.createProduct(request, SELLER_EMAIL));
                verify(productRepository, never()).save(any(Product.class));
        }

        @Test
        void shouldRejectNonSellerAndNotSaveProduct() {
                ProductCreateRequest request = validRequest();
                User client = org.mockito.Mockito.mock(User.class);
                when(client.getRole()).thenReturn(UserRole.CLIENT);
                when(userRepository.findByEmailIgnoreCase(CLIENT_EMAIL)).thenReturn(Optional.of(client));

                assertThrows(org.springframework.security.access.AccessDeniedException.class,
                                () -> productService.createProduct(request, CLIENT_EMAIL));
                verify(categoryRepository, never()).findAllById(any());
                verify(productRepository, never()).save(any(Product.class));
        }

        @Test
        void shouldRejectMissingAuthenticatedSellerWithoutSelectingAnySeller() {
                ProductCreateRequest request = validRequest();

                assertThrows(org.springframework.security.access.AccessDeniedException.class,
                                () -> productService.createProduct(request, null));
                verify(userRepository, never()).findAll();
                verify(userRepository, never()).findByEmailIgnoreCase(any());
                verify(categoryRepository, never()).findAllById(any());
                verify(productRepository, never()).save(any(Product.class));
        }

        @Test
        void shouldReturnSummaryForAuthenticatedSeller() {
                User seller = org.mockito.Mockito.mock(User.class);
                when(seller.getId()).thenReturn(SELLER_ID);
                when(seller.getRole()).thenReturn(UserRole.SELLER);
                when(userRepository.findByEmailIgnoreCase(SELLER_EMAIL)).thenReturn(Optional.of(seller));
                when(productRepository.countBySellerId(SELLER_ID)).thenReturn(12L);
                when(productRepository.countBySellerIdAndStock(SELLER_ID, 0)).thenReturn(2L);
                when(productRepository.countBySellerIdAndStockGreaterThanAndStockLessThanEqual(
                                SELLER_ID, 0, 5)).thenReturn(3L);

                ProductSummaryResponse result = productService.getSummary(SELLER_EMAIL);

                assertEquals(12L, result.totalProducts());
                assertEquals(2L, result.outOfStockProducts());
                assertEquals(3L, result.lowStockProducts());
                assertEquals(5, result.lowStockThreshold());
        }

        @Test
        void shouldReturnOnlyProductsBelongingToAuthenticatedSeller() {
                User seller = org.mockito.Mockito.mock(User.class);
                when(seller.getId()).thenReturn(SELLER_ID);
                when(seller.getRole()).thenReturn(UserRole.SELLER);
                when(userRepository.findByEmailIgnoreCase(SELLER_EMAIL)).thenReturn(Optional.of(seller));
                when(productRepository.findAllBySellerId(SELLER_ID)).thenReturn(List.of(new Product(new ProductDetails(
                                PRODUCT_NAME,
                                List.of(CATEGORY_ID),
                                3,
                                null,
                                new BigDecimal(PRODUCT_PRICE),
                                null,
                                null,
                                null,
                                true,
                                SELLER_ID))));

                List<ProductResponse> result = productService.getSellerProducts(SELLER_EMAIL);

                assertEquals(1, result.size());
                assertEquals(PRODUCT_NAME, result.get(0).name());
                assertEquals(3, result.get(0).stock());
                verify(productRepository).findAllBySellerId(SELLER_ID);
        }

        @Test
        void shouldUpdateOwnedProductAndPreserveItsSeller() {
                User seller = org.mockito.Mockito.mock(User.class);
                when(seller.getId()).thenReturn(SELLER_ID);
                when(seller.getRole()).thenReturn(UserRole.SELLER);
                when(userRepository.findByEmailIgnoreCase(SELLER_EMAIL)).thenReturn(Optional.of(seller));
                Product product = new Product(new ProductDetails(
                                PRODUCT_NAME,
                                List.of(CATEGORY_ID),
                                1,
                                null,
                                new BigDecimal(PRODUCT_PRICE),
                                null,
                                null,
                                null,
                                true,
                                SELLER_ID));
                when(productRepository.findById("product-id")).thenReturn(Optional.of(product));
                when(categoryRepository.findAllById(List.of(CATEGORY_ID)))
                                .thenReturn(List.of(new Category(CATEGORY_ID, "Electr├│nica")));
                when(productRepository.save(product)).thenReturn(product);

                ProductCreateRequest request = validRequest();
                request.setName("  Nuevo nombre  ");
                request.setStock(8);
                request.setDescription("Nueva descripci├│n");
                request.setPrice(new BigDecimal("25.50"));
                request.setAvailable(false);

                Product result = productService.updateProduct("product-id", request, SELLER_EMAIL);

                assertEquals("Nuevo nombre", result.getName());
                assertEquals(8, result.getStock());
                assertEquals("Nueva descripci├│n", result.getDescription());
                assertEquals(new BigDecimal("25.50"), result.getPrice());
                assertEquals(false, result.isAvailable());
                assertEquals(SELLER_ID, result.getSellerId());
                verify(productRepository).save(product);
        }

        @Test
        void shouldHideProductsOwnedByAnotherSeller() {
                User seller = org.mockito.Mockito.mock(User.class);
                when(seller.getId()).thenReturn(SELLER_ID);
                when(seller.getRole()).thenReturn(UserRole.SELLER);
                when(userRepository.findByEmailIgnoreCase(SELLER_EMAIL)).thenReturn(Optional.of(seller));
                when(productRepository.findById("product-id")).thenReturn(Optional.of(new Product(new ProductDetails(
                                PRODUCT_NAME,
                                List.of(CATEGORY_ID),
                                1,
                                null,
                                new BigDecimal(PRODUCT_PRICE),
                                null,
                                null,
                                null,
                                true,
                                OTHER_SELLER_ID))));

                assertThrows(ProductNotFoundException.class,
                                () -> productService.getProduct("product-id", SELLER_EMAIL));
                verify(categoryRepository, never()).findAllById(any());
                verify(productRepository, never()).save(any(Product.class));
        }

        @Test
        void shouldRejectUpdatingUnknownProduct() {
                User seller = org.mockito.Mockito.mock(User.class);
                when(seller.getRole()).thenReturn(UserRole.SELLER);
                when(userRepository.findByEmailIgnoreCase(SELLER_EMAIL)).thenReturn(Optional.of(seller));
                when(productRepository.findById("unknown")).thenReturn(Optional.empty());

                assertThrows(ProductNotFoundException.class,
                                () -> productService.updateProduct("unknown", validRequest(), SELLER_EMAIL));
                verify(categoryRepository, never()).findAllById(any());
                verify(productRepository, never()).save(any(Product.class));
        }

        @Test
        void shouldRejectNonSellerProductListRequest() {
                User client = org.mockito.Mockito.mock(User.class);
                when(client.getRole()).thenReturn(UserRole.CLIENT);
                when(userRepository.findByEmailIgnoreCase(CLIENT_EMAIL)).thenReturn(Optional.of(client));

                assertThrows(org.springframework.security.access.AccessDeniedException.class,
                                () -> productService.getSellerProducts(CLIENT_EMAIL));
                verify(productRepository, never()).findAllBySellerId(any());
        }

        @Test
        void shouldRejectNonSellerSummaryRequest() {
                User client = org.mockito.Mockito.mock(User.class);
                when(client.getRole()).thenReturn(UserRole.CLIENT);
                when(userRepository.findByEmailIgnoreCase(CLIENT_EMAIL)).thenReturn(Optional.of(client));

                assertThrows(org.springframework.security.access.AccessDeniedException.class,
                                () -> productService.getSummary(CLIENT_EMAIL));
                verify(productRepository, never()).countBySellerId(any());
        }

        // HU15: el vendedor marca un producto como exclusivo Premium al crearlo o editarlo.

        private void stubSellerWithCategory() {
                User seller = org.mockito.Mockito.mock(User.class);
                when(seller.getId()).thenReturn(SELLER_ID);
                when(seller.getRole()).thenReturn(UserRole.SELLER);
                when(userRepository.findByEmailIgnoreCase(SELLER_EMAIL)).thenReturn(Optional.of(seller));
                when(categoryRepository.findAllById(List.of(CATEGORY_ID)))
                                .thenReturn(List.of(new Category(CATEGORY_ID, CATEGORY_NAME_ELECTRONICS)));
        }

        @Test
        void returnsACatalogPageAndResolvesItsSellersInOneQuery() {
                CatalogSearch search = new CatalogSearch(null, null, null, null, null, null, null, null, 1, 2);
                when(productCatalogRepository.search(search)).thenReturn(new CatalogResult(
                                List.of(catalogProduct("p3", "seller-1"), catalogProduct("p4", "seller-2")), 5));
                when(productCatalogRepository.findMaxAvailablePrice()).thenReturn(new BigDecimal("200"));
                List<User> sellers = List.of(seller("seller-1", "Tienda Uno"), seller("seller-2", null));
                when(userRepository.findAllById(any())).thenReturn(sellers);

                CatalogPageResponse page = productService.getPublicCatalog(search);

                assertEquals(List.of("p3", "p4"), page.content().stream().map(ProductResponse::id).toList());
                assertEquals(List.of("Tienda Uno", "Ana López"),
                                page.content().stream().map(ProductResponse::sellerName).toList());
                assertEquals(1, page.page());
                assertEquals(2, page.size());
                assertEquals(5, page.totalElements());
                assertEquals(3, page.totalPages());
                assertEquals(new BigDecimal("200"), page.maxPrice());
                verify(userRepository).findAllById(any());
                verify(userRepository, never()).findById(anyString());
        }

        private Product catalogProduct(String id, String sellerId) {
                Product product = new Product(new ProductDetails(
                                PRODUCT_NAME, List.of(CATEGORY_ID), 1, null,
                                new BigDecimal(PRODUCT_PRICE), null, null, null, true, sellerId));
                product.setId(id);
                return product;
        }

        private User seller(String id, String businessName) {
                User seller = org.mockito.Mockito.mock(User.class);
                when(seller.getId()).thenReturn(id);
                if (businessName != null) {
                        when(seller.getSellerProfile()).thenReturn(new SellerProfile(businessName, CATEGORY_ID));
                } else {
                        when(seller.getFirstName()).thenReturn("Ana");
                        when(seller.getLastName()).thenReturn("López");
                }
                return seller;
        }


        private Product ownedProduct(boolean premiumOnly) {
                Product product = new Product(new ProductDetails(
                                PRODUCT_NAME, List.of(CATEGORY_ID), 1, null,
                                new BigDecimal(PRODUCT_PRICE), null, null, null, true, SELLER_ID));
                product.setId(PRODUCT_ID);
                product.setPremiumOnly(premiumOnly);
                return product;
        }

        @Test
        void shouldCreatePremiumOnlyProductWhenRequested() {
                stubSellerWithCategory();
                when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
                ProductCreateRequest request = validRequest();
                request.setPremiumOnly(true);

                Product result = productService.createProduct(request, SELLER_EMAIL);

                assertTrue(result.isPremiumOnly());
        }

        @Test
        void shouldCreateRegularProductWhenPremiumOnlyIsNotSent() {
                stubSellerWithCategory();
                when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

                Product result = productService.createProduct(validRequest(), SELLER_EMAIL);

                assertFalse(result.isPremiumOnly());
        }

        @Test
        void shouldCreatePremiumOnlyProductWithImageEndpoint() {
                stubSellerWithCategory();
                when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
                ProductCreateRequest request = validRequest();
                request.setPremiumOnly(true);

                Product result = productService.createProductWithImage(request, null, SELLER_EMAIL);

                assertTrue(result.isPremiumOnly());
        }

        @Test
        void shouldMarkProductAsPremiumOnlyWhenUpdating() {
                stubSellerWithCategory();
                Product product = ownedProduct(false);
                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
                when(productRepository.save(product)).thenReturn(product);
                ProductCreateRequest request = validRequest();
                request.setPremiumOnly(true);

                Product result = productService.updateProduct(PRODUCT_ID, request, SELLER_EMAIL);

                assertTrue(result.isPremiumOnly());
        }

        @Test
        void shouldRemovePremiumOnlyWhenUpdatingWithoutIt() {
                stubSellerWithCategory();
                Product product = ownedProduct(true);
                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
                when(productRepository.save(product)).thenReturn(product);
                ProductCreateRequest request = validRequest();
                request.setPremiumOnly(false);

                Product result = productService.updateProduct(PRODUCT_ID, request, SELLER_EMAIL);

                assertFalse(result.isPremiumOnly());
        }

        @Test
        void shouldMarkProductAsPremiumOnlyWhenUpdatingWithImageEndpoint() {
                stubSellerWithCategory();
                Product product = ownedProduct(false);
                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
                when(productRepository.save(product)).thenReturn(product);
                ProductCreateRequest request = validRequest();
                request.setPremiumOnly(true);

                Product result = productService.updateProductWithImage(PRODUCT_ID, request, null, SELLER_EMAIL);

                assertTrue(result.isPremiumOnly());
        }

        @Test
        void shouldKeepPremiumDiscountWhenProductIsPremiumOnly() {
                stubSellerWithCategory();
                when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
                ProductCreateRequest request = validRequest();
                request.setPremiumDiscount(new BigDecimal("15"));
                request.setPremiumOnly(true);

                Product result = productService.createProduct(request, SELLER_EMAIL);

                assertEquals(new BigDecimal("15"), result.getPremiumDiscount());
                assertTrue(result.isPremiumOnly());
        }

        @Test
        void shouldExposePremiumOnlyInProductResponse() {
                assertTrue(ProductResponse.from(ownedProduct(true)).premiumOnly());
                assertFalse(ProductResponse.from(ownedProduct(false)).premiumOnly());
        }

        private ProductCreateRequest validRequest() {
                ProductCreateRequest request = new ProductCreateRequest();
                request.setName(PRODUCT_NAME);
                request.setCategoryIds(List.of(CATEGORY_ID));
                request.setPrice(new BigDecimal(PRODUCT_PRICE));
                request.setAvailable(true);
                return request;
        }

        @Test
        void shouldAllowAdminToQueryProductsFilteredBySeller() {
                Product product1 = new Product(new ProductDetails(
                                PRODUCT_NAME, List.of(CATEGORY_ID), 10, DESC_TEXT,
                                new BigDecimal(PRODUCT_PRICE), null, null, null, true, SELLER_ID));

                when(productRepository.findAllBySellerId(SELLER_ID)).thenReturn(List.of(product1));
                User seller = org.mockito.Mockito.mock(User.class);
                when(seller.getFirstName()).thenReturn("TechStore");
                when(seller.getLastName()).thenReturn("");
                when(userRepository.findById(SELLER_ID)).thenReturn(Optional.of(seller));
                when(categoryRepository.findById(CATEGORY_ID))
                                .thenReturn(Optional.of(new Category(CATEGORY_ID, CATEGORY_NAME_ELECTRONICS)));

                List<ProductSummaryDTO> results = productService.getProductsForAdmin(SELLER_ID, null, null);

                assertEquals(1, results.size());
                assertEquals(PRODUCT_NAME, results.get(0).name());
                assertEquals("TechStore", results.get(0).sellerName());
                assertEquals(CATEGORY_NAME_ELECTRONICS, results.get(0).category());
        }

        @Test
        void shouldAllowAdminToQueryProductsFilteredByCategory() {
                Product product = new Product(new ProductDetails(
                                PRODUCT_NAME, List.of(CATEGORY_ID), 10, DESC_TEXT,
                                new BigDecimal(PRODUCT_PRICE), null, null, null, true, SELLER_ID));

                when(productRepository.findAll()).thenReturn(List.of(product));
                User seller = org.mockito.Mockito.mock(User.class);
                when(seller.getFirstName()).thenReturn("TechStore");
                when(seller.getLastName()).thenReturn("");
                when(userRepository.findById(SELLER_ID)).thenReturn(Optional.of(seller));
                when(categoryRepository.findById(CATEGORY_ID))
                                .thenReturn(Optional.of(new Category(CATEGORY_ID, CATEGORY_NAME_ELECTRONICS)));

                List<ProductSummaryDTO> results = productService.getProductsForAdmin(null, CATEGORY_ID, null);

                assertEquals(1, results.size());
                assertEquals(PRODUCT_NAME, results.get(0).name());
        }

        @Test
        void shouldAllowAdminToQueryProductsFilteredByStockStatus() {
                Product inStock = new Product(new ProductDetails(
                                PRODUCT_NAME, List.of(CATEGORY_ID), 10, DESC_TEXT,
                                new BigDecimal(PRODUCT_PRICE), null, null, null, true, SELLER_ID));
                Product outOfStock = new Product(new ProductDetails(
                                "Agotado", List.of(CATEGORY_ID), 0, DESC_TEXT,
                                new BigDecimal("9.99"), null, null, null, true, SELLER_ID));

                when(productRepository.findAll()).thenReturn(List.of(inStock, outOfStock));
                User seller = org.mockito.Mockito.mock(User.class);
                when(seller.getFirstName()).thenReturn("TechStore");
                when(seller.getLastName()).thenReturn("");
                when(userRepository.findById(SELLER_ID)).thenReturn(Optional.of(seller));
                when(categoryRepository.findById(CATEGORY_ID))
                                .thenReturn(Optional.of(new Category(CATEGORY_ID, CATEGORY_NAME_ELECTRONICS)));

                List<ProductSummaryDTO> resultsInStock = productService.getProductsForAdmin(null, null, true);
                assertEquals(1, resultsInStock.size());
                assertEquals(PRODUCT_NAME, resultsInStock.get(0).name());

                List<ProductSummaryDTO> resultsOutOfStock = productService.getProductsForAdmin(null, null, false);
                assertEquals(1, resultsOutOfStock.size());
                assertEquals("Agotado", resultsOutOfStock.get(0).name());
        }

        @Test
        void shouldAllowAdminToGetProductDetailById() {
                Product product = new Product(new ProductDetails(
                                PRODUCT_NAME, List.of(CATEGORY_ID), 15, DESC_TEXT,
                                new BigDecimal(PRODUCT_PRICE), null, null, null, true, SELLER_ID));

                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
                User seller = org.mockito.Mockito.mock(User.class);
                when(seller.getFirstName()).thenReturn("TechStore");
                when(seller.getLastName()).thenReturn("");
                when(userRepository.findById(SELLER_ID)).thenReturn(Optional.of(seller));
                when(categoryRepository.findById(CATEGORY_ID))
                                .thenReturn(Optional.of(new Category(CATEGORY_ID, CATEGORY_NAME_ELECTRONICS)));

                ProductDetailDTO detail = productService.getProductDetailForAdmin(PRODUCT_ID);

                assertEquals(PRODUCT_NAME, detail.name());
                assertEquals("TechStore", detail.sellerName());
                assertEquals(CATEGORY_NAME_ELECTRONICS, detail.category());
                assertEquals(15, detail.stock());
                assertEquals(new BigDecimal(PRODUCT_PRICE), detail.price());
        }

        @Test
        void shouldThrowProductNotFoundWhenAdminGetsDetailOfNonExistentProduct() {
                when(productRepository.findById(UNKNOWN_ID)).thenReturn(Optional.empty());

                assertThrows(ProductNotFoundException.class,
                                () -> productService.getProductDetailForAdmin(UNKNOWN_ID));
        }

        @Test
        void shouldRejectAdminAttemptingToCreateProduct() {
                User admin = org.mockito.Mockito.mock(User.class);
                when(admin.getRole()).thenReturn(UserRole.ADMIN);
                when(userRepository.findByEmailIgnoreCase(ADMIN_EMAIL)).thenReturn(Optional.of(admin));

                assertThrows(org.springframework.security.access.AccessDeniedException.class,
                                () -> productService.createProduct(validRequest(), ADMIN_EMAIL));
                verify(productRepository, never()).save(any(Product.class));
        }

        @Test
        void shouldRejectAdminAttemptingToUpdateProduct() {
                User admin = org.mockito.Mockito.mock(User.class);
                when(admin.getRole()).thenReturn(UserRole.ADMIN);
                when(userRepository.findByEmailIgnoreCase(ADMIN_EMAIL)).thenReturn(Optional.of(admin));

                assertThrows(org.springframework.security.access.AccessDeniedException.class,
                                () -> productService.updateProduct(PRODUCT_ID, validRequest(), ADMIN_EMAIL));
                verify(productRepository, never()).save(any(Product.class));
        }

        @Test
        void shouldRejectAdminAttemptingToDeleteProduct() {
                User admin = org.mockito.Mockito.mock(User.class);
                when(admin.getRole()).thenReturn(UserRole.ADMIN);
                when(userRepository.findByEmailIgnoreCase(ADMIN_EMAIL)).thenReturn(Optional.of(admin));

                assertThrows(org.springframework.security.access.AccessDeniedException.class,
                                () -> productService.deleteProduct(PRODUCT_ID, ADMIN_EMAIL));
                verify(productRepository, never()).delete(any(Product.class));
        }

        @Test
        void shouldAllowSellerToDeleteOwnedProduct() {
                User seller = org.mockito.Mockito.mock(User.class);
                when(seller.getId()).thenReturn(SELLER_ID);
                when(seller.getRole()).thenReturn(UserRole.SELLER);
                when(userRepository.findByEmailIgnoreCase(SELLER_EMAIL)).thenReturn(Optional.of(seller));

                Product product = new Product(new ProductDetails(
                                PRODUCT_NAME, List.of(CATEGORY_ID), 5, DESC_TEXT,
                                new BigDecimal(PRODUCT_PRICE), null, null, null, true, SELLER_ID));
                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));

                productService.deleteProduct(PRODUCT_ID, SELLER_EMAIL);

                verify(productRepository).delete(product);
        }

        @Test
        void shouldRejectSellerAttemptingToDeleteProductOwnedByAnotherSeller() {
                User seller = org.mockito.Mockito.mock(User.class);
                when(seller.getId()).thenReturn(SELLER_ID);
                when(seller.getRole()).thenReturn(UserRole.SELLER);
                when(userRepository.findByEmailIgnoreCase(SELLER_EMAIL)).thenReturn(Optional.of(seller));

                Product otherProduct = new Product(new ProductDetails(
                                PRODUCT_NAME, List.of(CATEGORY_ID), 5, DESC_TEXT,
                                new BigDecimal(PRODUCT_PRICE), null, null, null, true, OTHER_SELLER_ID));
                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(otherProduct));

                assertThrows(org.springframework.security.access.AccessDeniedException.class,
                                () -> productService.deleteProduct(PRODUCT_ID, SELLER_EMAIL));
                verify(productRepository, never()).delete(any(Product.class));
        }

        @Test
        void shouldRejectDeletingNonExistentProduct() {
                User seller = org.mockito.Mockito.mock(User.class);
                when(seller.getRole()).thenReturn(UserRole.SELLER);
                when(userRepository.findByEmailIgnoreCase(SELLER_EMAIL)).thenReturn(Optional.of(seller));
                when(productRepository.findById(UNKNOWN_ID)).thenReturn(Optional.empty());

                assertThrows(ProductNotFoundException.class,
                                () -> productService.deleteProduct(UNKNOWN_ID, SELLER_EMAIL));
                verify(productRepository, never()).delete(any(Product.class));
        }

        @Test
        @DisplayName("HU19-CP01: el vendedor dueño ve su producto desactivado")
        void ownerSellerCanSeeDeactivatedProduct() {
                Product product = deactivatedProduct(SELLER_ID);
                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
                AuthenticatedUserPrincipal owner =
                                new AuthenticatedUserPrincipal(SELLER_ID, SELLER_EMAIL, UserRole.SELLER, true);

                ProductResponse response =
                                productService.getProductForClientOrManagement(PRODUCT_ID, owner);

                assertEquals(PRODUCT_ID, response.id());
                assertFalse(response.available());
        }

        @Test
        @DisplayName("HU19-CP02: el administrador ve un producto desactivado")
        void administratorCanSeeDeactivatedProduct() {
                Product product = deactivatedProduct(OTHER_SELLER_ID);
                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
                AuthenticatedUserPrincipal admin =
                                new AuthenticatedUserPrincipal("admin-id", ADMIN_EMAIL, UserRole.ADMIN, true);

                ProductResponse response =
                                productService.getProductForClientOrManagement(PRODUCT_ID, admin);

                assertFalse(response.available());
        }

        @Test
        @DisplayName("HU19-CP03: el cliente recibe 410 para un producto desactivado")
        void clientGetsGoneForDeactivatedProduct() {
                Product product = deactivatedProduct(SELLER_ID);
                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
                AuthenticatedUserPrincipal client =
                                new AuthenticatedUserPrincipal("client-id", CLIENT_EMAIL, UserRole.CLIENT, true);

                ProductoDesactivadoException exception = assertThrows(
                                ProductoDesactivadoException.class,
                                () -> productService.getProductForClientOrManagement(PRODUCT_ID, client));

                assertEquals("Este producto ya no está disponible", exception.getMessage());
                assertEquals("PRODUCT_UNAVAILABLE", ProductoDesactivadoException.CODE);
        }

        @Test
        @DisplayName("HU19-CP04: otro vendedor recibe 410 para un producto desactivado")
        void otherSellerGetsGoneForDeactivatedProduct() {
                Product product = deactivatedProduct(SELLER_ID);
                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
                AuthenticatedUserPrincipal otherSeller =
                                new AuthenticatedUserPrincipal(OTHER_SELLER_ID, "other@example.com",
                                                UserRole.SELLER, true);

                assertThrows(ProductoDesactivadoException.class,
                                () -> productService.getProductForClientOrManagement(PRODUCT_ID, otherSeller));
        }

        @Test
        @DisplayName("HU19-CP05: un usuario anónimo recibe 410 para un producto desactivado")
        void anonymousGetsGoneForDeactivatedProduct() {
                Product product = deactivatedProduct(SELLER_ID);
                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));

                assertThrows(ProductoDesactivadoException.class,
                                () -> productService.getProductForClientOrManagement(PRODUCT_ID, null));
        }

        @Test
        @DisplayName("HU19-CP06: el detalle de un producto activo no cambia")
        void activeProductDetailRemainsAvailable() {
                Product product = new Product(new ProductDetails(
                                PRODUCT_NAME, List.of(CATEGORY_ID), 5, DESC_TEXT,
                                new BigDecimal(PRODUCT_PRICE), null, null, null, true, SELLER_ID));
                product.setId(PRODUCT_ID);
                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));

                AuthenticatedUserPrincipal client =
                                new AuthenticatedUserPrincipal("client-id", CLIENT_EMAIL, UserRole.CLIENT, true);

                ProductResponse response =
                                productService.getProductForClientOrManagement(PRODUCT_ID, client);

                assertEquals(PRODUCT_ID, response.id());
                assertTrue(response.available());
        }

        private Product deactivatedProduct(String sellerId) {
                Product product = new Product(new ProductDetails(
                                PRODUCT_NAME, List.of(CATEGORY_ID), 5, DESC_TEXT,
                                new BigDecimal(PRODUCT_PRICE), null, null, null, false, sellerId));
                product.setId(PRODUCT_ID);
                return product;
        }

        // HU16 (T16.1): el catálogo y el detalle enseñan los exclusivos a todos (pregunta 9: se
        // muestran bloqueados) y dicen con «purchasable» si el cliente de la sesión puede comprarlo.

        private static final String VIEWER_ID = "viewer-id";

        @AfterEach
        void clearSecurityContext() {
                SecurityContextHolder.clearContext();
        }

        private void loginAsClient(CustomerType type) {
                AuthenticatedUserPrincipal principal =
                                new AuthenticatedUserPrincipal(VIEWER_ID, CLIENT_EMAIL, UserRole.CLIENT, true);
                SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
                User viewer = org.mockito.Mockito.mock(User.class);
                org.mockito.Mockito.lenient().when(viewer.getClientProfile()).thenReturn(new ClientProfile(null, type));
                // lenient: el servicio también busca por id el nombre del vendedor
                org.mockito.Mockito.lenient().when(userRepository.findById(VIEWER_ID)).thenReturn(Optional.of(viewer));
        }

        private Product exclusiveCatalogProduct(String id) {
                Product product = catalogProduct(id, SELLER_ID);
                product.setPremiumOnly(true);
                return product;
        }

        private CatalogPageResponse catalogWithExclusiveAndRegular() {
                CatalogSearch search = new CatalogSearch(null, null, null, null, null, null, null, null, 0, 12);
                when(productCatalogRepository.search(search)).thenReturn(new CatalogResult(
                                List.of(exclusiveCatalogProduct("exclusive"), catalogProduct("regular", SELLER_ID)), 2));
                when(productCatalogRepository.findMaxAvailablePrice()).thenReturn(new BigDecimal("100"));
                org.mockito.Mockito.lenient().when(userRepository.findAllById(any())).thenReturn(List.of());
                return productService.getPublicCatalog(search);
        }

        private ProductResponse byId(CatalogPageResponse page, String id) {
                return page.content().stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow();
        }

        @Test
        @DisplayName("HU16: un cliente Estándar ve el exclusivo en el catálogo, pero no se puede comprar")
        void standardClientSeesExclusiveProductAsNotPurchasable() {
                loginAsClient(CustomerType.STANDARD);

                CatalogPageResponse page = catalogWithExclusiveAndRegular();

                assertEquals(2, page.content().size());
                assertTrue(byId(page, "exclusive").premiumOnly());
                assertFalse(byId(page, "exclusive").purchasable());
                assertTrue(byId(page, "regular").purchasable());
        }

        @Test
        @DisplayName("HU16: un cliente Premium puede comprar el exclusivo del catálogo")
        void premiumClientCanPurchaseExclusiveProductFromCatalog() {
                loginAsClient(CustomerType.PREMIUM);

                CatalogPageResponse page = catalogWithExclusiveAndRegular();

                assertTrue(byId(page, "exclusive").purchasable());
                assertTrue(byId(page, "regular").purchasable());
        }

        @Test
        @DisplayName("HU16: sin sesión el exclusivo se ve en el catálogo, pero no se puede comprar")
        void anonymousVisitorSeesExclusiveProductAsNotPurchasable() {
                CatalogPageResponse page = catalogWithExclusiveAndRegular();

                assertEquals(2, page.content().size());
                assertFalse(byId(page, "exclusive").purchasable());
                assertTrue(byId(page, "regular").purchasable());
        }

        @Test
        @DisplayName("HU16: el catálogo de cliente también marca el exclusivo según el tipo de cliente")
        void customerCatalogMarksExclusiveProductForStandardClient() {
                loginAsClient(CustomerType.STANDARD);
                when(productRepository.findAllByAvailableTrueAndStockGreaterThan(0))
                                .thenReturn(List.of(exclusiveCatalogProduct("exclusive")));

                List<ProductResponse> result = productService.getAvailableProducts();

                assertEquals(1, result.size());
                assertFalse(result.get(0).purchasable());
        }

        @Test
        @DisplayName("HU16: el detalle de un exclusivo no se puede comprar para un Estándar y sí para un Premium")
        void productDetailIsPurchasableOnlyForPremiumClients() {
                Product product = exclusiveCatalogProduct(PRODUCT_ID);
                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
                AuthenticatedUserPrincipal viewer =
                                new AuthenticatedUserPrincipal(VIEWER_ID, CLIENT_EMAIL, UserRole.CLIENT, true);

                loginAsClient(CustomerType.STANDARD);
                assertFalse(productService.getProductForClientOrManagement(PRODUCT_ID, viewer).purchasable());

                loginAsClient(CustomerType.PREMIUM);
                assertTrue(productService.getProductForClientOrManagement(PRODUCT_ID, viewer).purchasable());
        }

        @Test
        @DisplayName("HU16: forCustomer solo bloquea los exclusivos a quien no es Premium")
        void forCustomerBlocksOnlyExclusiveProductsForNonPremiumClients() {
                ProductResponse exclusive = ProductResponse.from(exclusiveCatalogProduct("exclusive"));
                ProductResponse regular = ProductResponse.from(catalogProduct("regular", SELLER_ID));

                assertFalse(exclusive.forCustomer(false).purchasable());
                assertTrue(exclusive.forCustomer(true).purchasable());
                assertTrue(regular.forCustomer(false).purchasable());
                assertTrue(exclusive.forCustomer(false).premiumOnly());
        }

        // HU28 (T28.1): al dar de alta un producto se publica ProductCreatedEvent, con la foto del
        // producto guardado y sus categorías. Al editarlo no (RN-17); decidir a quién avisar es del listener.

        private ProductCreatedEvent publishedCreation() {
                ArgumentCaptor<ProductCreatedEvent> captor = ArgumentCaptor.forClass(ProductCreatedEvent.class);
                verify(eventPublisher).publishEvent(captor.capture());
                return captor.getValue();
        }

        @Test
        @DisplayName("HU28: dar de alta un producto publica el evento de producto nuevo con sus categorías")
        void creatingProductPublishesProductCreatedEvent() {
                stubSellerWithCategory();
                when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
                        Product saved = invocation.getArgument(0);
                        saved.setId(PRODUCT_ID);
                        return saved;
                });
                ProductCreateRequest request = validRequest();
                request.setPremiumOnly(true);

                productService.createProduct(request, SELLER_EMAIL);

                ProductCreatedEvent event = publishedCreation();
                assertEquals(PRODUCT_ID, event.product().id());
                assertEquals(PRODUCT_NAME, event.product().name());
                assertTrue(event.product().available());
                assertTrue(event.product().premiumOnly());
                assertEquals(List.of(CATEGORY_ID), event.categoryIds());
        }

        @Test
        @DisplayName("HU28: el alta con imagen también publica el evento de producto nuevo")
        void creatingProductWithImageEndpointPublishesProductCreatedEvent() {
                stubSellerWithCategory();
                when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

                Product created = productService.createProductWithImage(validRequest(), null, SELLER_EMAIL);

                ProductCreatedEvent event = publishedCreation();
                assertEquals(created.getId(), event.product().id());
                assertEquals(List.of(CATEGORY_ID), event.categoryIds());
        }

        @Test
        @DisplayName("HU28: el alta de un producto no disponible también publica el evento, marcado como no disponible")
        void creatingUnavailableProductPublishesEventMarkedUnavailable() {
                stubSellerWithCategory();
                when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
                ProductCreateRequest request = validRequest();
                request.setAvailable(false);

                productService.createProduct(request, SELLER_EMAIL);

                assertFalse(publishedCreation().product().available());
        }

        @Test
        @DisplayName("HU28: si el producto no se llega a guardar, no se publica el evento")
        void failedCreationDoesNotPublishProductCreatedEvent() {
                stubSellerWithCategory();
                when(productRepository.save(any(Product.class))).thenThrow(new IllegalStateException("MongoDB no responde"));

                assertThrows(IllegalStateException.class,
                                () -> productService.createProduct(validRequest(), SELLER_EMAIL));

                verify(eventPublisher, never()).publishEvent(any(ProductCreatedEvent.class));
        }

        @Test
        @DisplayName("HU28: editar un producto no publica el evento de producto nuevo (RN-17)")
        void updatingProductDoesNotPublishProductCreatedEvent() {
                stubSellerWithCategory();
                Product product = ownedProduct(false);
                when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
                when(productRepository.save(product)).thenReturn(product);

                productService.updateProduct(PRODUCT_ID, validRequest(), SELLER_EMAIL);

                verify(eventPublisher, never()).publishEvent(any(ProductCreatedEvent.class));
        }

        @Test
        @DisplayName("HU28: el evento guarda una copia de las categorías")
        void productCreatedEventKeepsItsOwnCopyOfTheCategories() {
                java.util.ArrayList<String> categories = new java.util.ArrayList<>(List.of(CATEGORY_ID));
                ProductCreatedEvent event = new ProductCreatedEvent(
                                ProductSnapshot.of(ownedProduct(false)), categories);

                categories.add("otra-categoria");

                assertEquals(List.of(CATEGORY_ID), event.categoryIds());
        }
}