package es.uclm.esibuy.products.repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.bson.Document;
import org.bson.types.Decimal128;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;

import es.uclm.esibuy.products.dto.CatalogSearch;
import es.uclm.esibuy.products.model.Product;

@Repository
public class ProductCatalogRepository {

    private static final String COLLECTION = "products";
    private static final String PRICE_VALUE = "priceValue";
    private static final String DISCOUNT_VALUE = "discountValue";
    private static final String PREMIUM_DISCOUNT_VALUE = "premiumDiscountValue";
    private static final String RATING_MISSING = "ratingMissing";
    private static final String RATING_SORT_VALUE = "ratingSortValue";
    private static final String RATING = "rating";
    private static final String REVIEW_COUNT = "reviewCount";
    private static final String ID = "_id";
    private static final String IF_NULL = "$ifNull";

    private final MongoTemplate mongoTemplate;

    public ProductCatalogRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public record CatalogResult(List<Product> products, long total) {
    }

    public CatalogResult search(CatalogSearch search) {
        List<AggregationOperation> filters = filters(search);

        List<AggregationOperation> countPipeline = new ArrayList<>(filters);
        countPipeline.add(Aggregation.count().as("total"));
        Document count = mongoTemplate
                .aggregate(Aggregation.newAggregation(countPipeline), COLLECTION, Document.class)
                .getUniqueMappedResult();
        long total = count == null ? 0 : ((Number) count.get("total")).longValue();

        List<AggregationOperation> pagePipeline = new ArrayList<>(filters);
        pagePipeline.add(Aggregation.sort(sort(search.sort())));
        pagePipeline.add(Aggregation.skip((long) search.page() * search.size()));
        pagePipeline.add(Aggregation.limit(search.size()));
        List<Product> products = mongoTemplate
                .aggregate(Aggregation.newAggregation(pagePipeline), COLLECTION, Product.class)
                .getMappedResults();

        return new CatalogResult(products, total);
    }

    public BigDecimal findMaxAvailablePrice() {
        Document result = mongoTemplate.aggregate(Aggregation.newAggregation(
                Aggregation.match(Criteria.where("available").is(true)),
                numericFields(),
                Aggregation.group().max(PRICE_VALUE).as("maxPrice")),
                COLLECTION, Document.class).getUniqueMappedResult();

        if (result == null || !(result.get("maxPrice") instanceof Decimal128 maxPrice)) {
            return BigDecimal.ZERO;
        }
        return maxPrice.bigDecimalValue();
    }

    private List<AggregationOperation> filters(CatalogSearch search) {
        List<Criteria> stored = new ArrayList<>();
        stored.add(Criteria.where("available").is(true));

        if (hasText(search.query())) {
            String text = Pattern.quote(search.query().trim());
            stored.add(new Criteria().orOperator(
                    Criteria.where("name").regex(text, "i"),
                    Criteria.where("description").regex(text, "i")));
        }
        if (hasText(search.categoryId())) {
            stored.add(Criteria.where("categoryIds").is(search.categoryId().trim()));
        }
        if (search.minRating() != null) {
            // Sin valoraciones no hay nota, aunque el producto guarde una (Product.getRating())
            stored.add(Criteria.where(REVIEW_COUNT).gt(0));
            stored.add(Criteria.where(RATING).gte(search.minRating()));
        }
        if (Boolean.TRUE.equals(search.inStock())) {
            stored.add(Criteria.where("stock").gt(0));
        }

        // El precio y los descuentos pueden estar guardados como texto o como decimal,
        // así que se comparan después de convertirlos a número
        List<Criteria> numeric = new ArrayList<>();
        if (search.minPrice() != null) {
            numeric.add(Criteria.where(PRICE_VALUE).gte(new Decimal128(search.minPrice())));
        }
        if (search.maxPrice() != null) {
            numeric.add(Criteria.where(PRICE_VALUE).lte(new Decimal128(search.maxPrice())));
        }
        if (Boolean.TRUE.equals(search.onlyOffers())) {
            Decimal128 zero = new Decimal128(BigDecimal.ZERO);
            numeric.add(new Criteria().orOperator(
                    Criteria.where(DISCOUNT_VALUE).gt(zero),
                    Criteria.where(PREMIUM_DISCOUNT_VALUE).gt(zero)));
        }

        List<AggregationOperation> operations = new ArrayList<>();
        operations.add(Aggregation.match(new Criteria().andOperator(stored)));
        operations.add(numericFields());
        if (!numeric.isEmpty()) {
            operations.add(Aggregation.match(new Criteria().andOperator(numeric)));
        }
        return operations;
    }

    private AggregationOperation numericFields() {
        return context -> new Document("$addFields", new Document()
                .append(PRICE_VALUE, toDecimal("$price"))
                .append(DISCOUNT_VALUE, toDecimal("$discount"))
                .append(PREMIUM_DISCOUNT_VALUE, toDecimal("$premiumDiscount"))
                .append(RATING_MISSING, new Document("$cond", Arrays.asList(hasRating(), 0, 1)))
                .append(RATING_SORT_VALUE, new Document("$cond", Arrays.asList(hasRating(), "$rating", null))));
    }

    // Solo cuenta la nota de un producto con valoraciones: sin ellas no tiene nota (Product.getRating())
    private Document hasRating() {
        return new Document("$and", Arrays.asList(
                new Document("$gt", Arrays.asList(
                        new Document(IF_NULL, Arrays.asList("$" + REVIEW_COUNT, 0)), 0)),
                new Document("$ne", Arrays.asList(
                        new Document(IF_NULL, Arrays.asList("$" + RATING, null)), null))));
    }

    private Document toDecimal(String field) {
        return new Document("$toDecimal", new Document(IF_NULL, Arrays.asList(field, 0)));
    }

    private Sort sort(String sort) {
        Sort byId = Sort.by(ID);
        if (sort == null) {
            return byId;
        }
        return switch (sort) {
            case "price-asc" -> Sort.by(Sort.Direction.ASC, PRICE_VALUE).and(byId);
            case "price-desc" -> Sort.by(Sort.Direction.DESC, PRICE_VALUE).and(byId);
            case "rating-desc" -> Sort.by(RATING_MISSING).and(Sort.by(Sort.Direction.DESC, RATING_SORT_VALUE)).and(byId);
            case "rating-asc" -> Sort.by(RATING_MISSING).and(Sort.by(Sort.Direction.ASC, RATING_SORT_VALUE)).and(byId);
            default -> byId;
        };
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}