package es.uclm.esibuy.statistics.repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bson.Document;
import org.bson.types.Decimal128;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;

import es.uclm.esibuy.orders.model.OrderPaymentStatus;
import es.uclm.esibuy.orders.model.OrderStatus;
import es.uclm.esibuy.products.model.Product;

@Repository
public class MongoStatisticsRepository implements StatisticsRepository {

    private static final String ORDERS_COLLECTION = "pedidos";
    private static final String PRODUCT_ID = "items.productoId";
    private static final String UNITS_SOLD = "unitsSold";
    private static final String REVENUE = "revenue";
    private static final String SUBTOTAL_VALUE = "subtotalValue";
    private static final String PRODUCTS_COLLECTION = "products";
    private static final String RATING = "rating";
    private static final String REVIEW_COUNT = "reviewCount";
    private static final String SELLER_ID = "sellerId";
    private static final String AVERAGE_RATING = "averageRating";
    private static final String RATED_PRODUCTS = "ratedProducts";
    private static final String PLATFORM_AVERAGE = "platformAverage";
    private static final String ID = "_id";

    private final MongoTemplate mongoTemplate;

    public MongoStatisticsRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public List<ProductSalesRow> findTopSellingProducts(int limit) {
        Aggregation aggregation = Aggregation.newAggregation(
                // Solo cuentan las compras pagadas que no se han cancelado (RN-03)
                Aggregation.match(Criteria.where("estadoPago").is(OrderPaymentStatus.PAGADO.name())
                        .and("estado").ne(OrderStatus.CANCELADO.name())),
                Aggregation.unwind("items"),
                // Una línea antigua sin producto no se puede atribuir a ninguno: agruparía en una
                // fila con identificador nulo
                Aggregation.match(Criteria.where(PRODUCT_ID).nin(Arrays.asList(null, ""))),
                subtotalAsNumber(),
                Aggregation.group(PRODUCT_ID)
                        .first("items.nombre").as("productName")
                        // El máximo ignora los nulos: se queda con el vendedor si alguna línea lo guarda
                        .max("items.vendedorId").as("sellerId")
                        .sum("items.cantidad").as(UNITS_SOLD)
                        .sum(SUBTOTAL_VALUE).as(REVENUE),
                // El desempate por identificador hace que el top sea siempre el mismo (RN-01)
                Aggregation.sort(Sort.by(Sort.Direction.DESC, UNITS_SOLD)
                        .and(Sort.by(Sort.Direction.DESC, REVENUE))
                        .and(Sort.by(Sort.Direction.ASC, ID))),
                Aggregation.limit(limit));

        return mongoTemplate.aggregate(aggregation, ORDERS_COLLECTION, Document.class)
                .getMappedResults().stream()
                .map(this::toRow)
                .toList();
    }

    @Override
    public List<ProductRatingRow> findTopRatedProducts(int minimumReviews, int limit) {
        Aggregation aggregation = Aggregation.newAggregation(
                // El número de valoraciones manda: un producto sin ellas puede guardar una nota
                // (rating 5 con reviewCount 0 o sin el campo) y no debe entrar en el ranking.
                // Con rating >= 0 se descartan también las notas que no son un número
                Aggregation.match(Criteria.where(REVIEW_COUNT).gte(Math.max(minimumReviews, 1))
                        .and(RATING).gte(0)),
                // El desempate por número de valoraciones y luego por identificador hace que
                // el top sea siempre el mismo (RN-01)
                Aggregation.sort(Sort.by(Sort.Direction.DESC, RATING)
                        .and(Sort.by(Sort.Direction.DESC, REVIEW_COUNT))
                        .and(Sort.by(Sort.Direction.ASC, ID))),
                Aggregation.limit(limit));

        return mongoTemplate.aggregate(aggregation, PRODUCTS_COLLECTION, Product.class)
                .getMappedResults().stream()
                .map(product -> new ProductRatingRow(
                        product.getId(),
                        product.getName(),
                        product.getSellerId(),
                        product.getRating(),
                        product.getReviewCount()))
                .toList();
    }

    @Override
    public List<SellerRatingRow> findTopRatedSellers(int limit) {
        List<AggregationOperation> operations = new ArrayList<>(sellerAverages());
        // El desempate por productos valorados y luego por identificador hace que el top sea
        // siempre el mismo (RN-01)
        operations.add(Aggregation.sort(Sort.by(Sort.Direction.DESC, AVERAGE_RATING)
                .and(Sort.by(Sort.Direction.DESC, RATED_PRODUCTS))
                .and(Sort.by(Sort.Direction.ASC, ID))));
        operations.add(Aggregation.limit(limit));

        return mongoTemplate.aggregate(Aggregation.newAggregation(operations), PRODUCTS_COLLECTION, Document.class)
                .getMappedResults().stream()
                .map(document -> new SellerRatingRow(
                        document.getString(ID),
                        ((Number) document.get(AVERAGE_RATING)).doubleValue(),
                        ((Number) document.get(RATED_PRODUCTS)).intValue()))
                .toList();
    }

    @Override
    public Double findPlatformAverageRating() {
        // La media de la plataforma sale de las medias de los vendedores, no de todas las valoraciones
        List<AggregationOperation> operations = new ArrayList<>(sellerAverages());
        operations.add(Aggregation.group().avg(AVERAGE_RATING).as(PLATFORM_AVERAGE));

        Document result = mongoTemplate
                .aggregate(Aggregation.newAggregation(operations), PRODUCTS_COLLECTION, Document.class)
                .getUniqueMappedResult();

        return result == null ? null : ((Number) result.get(PLATFORM_AVERAGE)).doubleValue();
    }

    /**
     * Valoración media de cada vendedor: la media de las medias de sus productos con valoraciones.
     * Un producto sin valoraciones no cuenta aunque guarde una nota, y uno sin vendedor no se
     * puede atribuir a nadie.
     */
    private List<AggregationOperation> sellerAverages() {
        return List.of(
                Aggregation.match(Criteria.where(REVIEW_COUNT).gte(1)
                        .and(RATING).gte(0)
                        .and(SELLER_ID).nin(Arrays.asList(null, ""))),
                Aggregation.group(SELLER_ID)
                        .avg(RATING).as(AVERAGE_RATING)
                        .count().as(RATED_PRODUCTS));
    }

    /**
     * Los importes pueden estar guardados como texto o como decimal, y sumar texto da 0,
     * así que se convierten a número antes de agrupar. Un importe que no sea un número
     * ("N/A", por ejemplo) o que falte cuenta como 0, en lugar de hacer fallar el ranking.
     */
    private AggregationOperation subtotalAsNumber() {
        return context -> new Document("$addFields", new Document(SUBTOTAL_VALUE,
                new Document("$convert", new Document("input", "$items.subtotal")
                        .append("to", "decimal")
                        .append("onError", 0)
                        .append("onNull", 0))));
    }

    private ProductSalesRow toRow(Document document) {
        return new ProductSalesRow(
                document.getString(ID),
                document.getString("productName"),
                document.getString("sellerId"),
                ((Number) document.get(UNITS_SOLD)).longValue(),
                toMoney(document.get(REVENUE)));
    }

    private BigDecimal toMoney(Object value) {
        BigDecimal amount = value instanceof Decimal128 decimal
                ? decimal.bigDecimalValue()
                : new BigDecimal(String.valueOf(value));

        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
