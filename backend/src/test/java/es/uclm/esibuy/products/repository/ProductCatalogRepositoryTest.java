package es.uclm.esibuy.products.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import es.uclm.esibuy.products.dto.CatalogSearch;
import es.uclm.esibuy.products.model.Product;
import es.uclm.esibuy.products.repository.ProductCatalogRepository.CatalogResult;

// Necesita Docker: sin Docker estos tests se saltan en lugar de fallar
@DataMongoTest
@Testcontainers(disabledWithoutDocker = true)
@Import(ProductCatalogRepository.class)
class ProductCatalogRepositoryTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private ProductCatalogRepository catalogRepository;

    @BeforeEach
    void setUp() {
        mongoTemplate.dropCollection("products");
        // Como en la base real, unos productos guardan el precio como texto y otros como decimal
        insert("p01", "Auriculares Bluetooth", "Sonido sin cables", "cat-audio", "29.99", null, 4.8, 10, true);
        insert("p02", "Altavoz", "Sonido potente", "cat-audio", decimal("120.00"), "10", 4.2, 0, true);
        insert("p03", "Cafetera Express", "Café rápido", "cat-hogar", "89.99", null, null, 5, true);
        insert("p04", "Tostadora", "Pan crujiente", "cat-hogar", decimal("9.50"), null, 3.1, 3, true);
        insert("p05", "Lámpara", "Luz cálida", "cat-hogar", "200", null, 4.9, 1, true);
        insert("p06", "Producto retirado", "No se vende", "cat-audio", "15", null, 5.0, 4, false);
    }

    @Test
    void returnsOnlyTheRequestedPageAndTheTotal() {
        CatalogResult first = catalogRepository.search(page(0, 2));
        CatalogResult last = catalogRepository.search(page(2, 2));

        assertThat(ids(first)).containsExactly("p01", "p02");
        assertThat(first.total()).isEqualTo(5);
        assertThat(ids(last)).containsExactly("p05");
    }

    @Test
    void aPageAfterTheLastOneIsEmpty() {
        CatalogResult result = catalogRepository.search(page(9, 2));

        assertThat(result.products()).isEmpty();
        assertThat(result.total()).isEqualTo(5);
    }

    @Test
    void combinesTextCategoryPriceRatingAndStockFilters() {
        CatalogResult anyStock = catalogRepository.search(new CatalogSearch(
                "SONIDO", "cat-audio", new BigDecimal("20"), new BigDecimal("150"), 4.0, null, null, null, 0, 12));
        CatalogResult inStock = catalogRepository.search(new CatalogSearch(
                "sonido", "cat-audio", new BigDecimal("20"), new BigDecimal("150"), 4.0, true, null, null, 0, 12));

        assertThat(ids(anyStock)).containsExactly("p01", "p02");
        assertThat(ids(inStock)).containsExactly("p01");
    }

    @Test
    void comparesPricesAsNumbersAlthoughSomeAreStoredAsText() {
        CatalogResult result = catalogRepository.search(new CatalogSearch(
                null, null, new BigDecimal("90"), null, null, null, null, null, 0, 12));

        assertThat(ids(result)).containsExactly("p02", "p05");
    }

    @Test
    void searchTextIsNotReadAsARegularExpression() {
        CatalogResult result = catalogRepository.search(new CatalogSearch(
                ".*", null, null, null, null, null, null, null, 0, 12));

        assertThat(result.products()).isEmpty();
    }

    @Test
    void filtersProductsOnOffer() {
        CatalogResult result = catalogRepository.search(new CatalogSearch(
                null, null, null, null, null, null, true, null, 0, 12));

        assertThat(ids(result)).containsExactly("p02");
    }

    @Test
    void sortsByPriceAsNumbers() {
        assertThat(ids(catalogRepository.search(sorted("price-asc"))))
                .containsExactly("p04", "p01", "p03", "p02", "p05");
        assertThat(ids(catalogRepository.search(sorted("price-desc"))))
                .containsExactly("p05", "p02", "p03", "p01", "p04");
    }

    @Test
    void keepsUnratedProductsLastWhenSortingByRating() {
        assertThat(ids(catalogRepository.search(sorted("rating-desc"))))
                .containsExactly("p05", "p01", "p02", "p04", "p03");
        assertThat(ids(catalogRepository.search(sorted("rating-asc"))))
                .containsExactly("p04", "p02", "p01", "p05", "p03");
    }

    @Test
    void returnsTheHighestPriceOfTheAvailableProducts() {
        assertThat(catalogRepository.findMaxAvailablePrice()).isEqualByComparingTo("200");
    }

    private CatalogSearch page(int page, int size) {
        return new CatalogSearch(null, null, null, null, null, null, null, null, page, size);
    }

    private CatalogSearch sorted(String sort) {
        return new CatalogSearch(null, null, null, null, null, null, null, sort, 0, 12);
    }

    private List<String> ids(CatalogResult result) {
        return result.products().stream().map(Product::getId).toList();
    }

    private Decimal128 decimal(String value) {
        return new Decimal128(new BigDecimal(value));
    }

    private void insert(String id, String name, String description, String categoryId, Object price,
            String discount, Double rating, int stock, boolean available) {
        Document product = new Document("_id", id)
                .append("name", name)
                .append("description", description)
                .append("categoryIds", List.of(categoryId))
                .append("price", price)
                .append("stock", stock)
                .append("available", available)
                .append("sellerId", "seller-" + id);
        if (discount != null) {
            product.append("discount", discount);
        }
        if (rating != null) {
            product.append("rating", rating);
        }
        mongoTemplate.getCollection("products").insertOne(product);
    }
}