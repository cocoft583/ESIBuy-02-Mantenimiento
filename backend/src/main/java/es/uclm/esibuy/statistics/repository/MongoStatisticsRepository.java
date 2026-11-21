package es.uclm.esibuy.statistics.repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

@Repository
public class MongoStatisticsRepository implements StatisticsRepository {

    private static final String ORDERS_COLLECTION = "pedidos";
    private static final String PRODUCT_ID = "items.productoId";
    private static final String UNITS_SOLD = "unitsSold";
    private static final String REVENUE = "revenue";
    private static final String SUBTOTAL_VALUE = "subtotalValue";
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

    /**
     * Los importes pueden estar guardados como texto o como decimal, y sumar texto da 0,
     * así que se convierten a número antes de agrupar.
     */
    private AggregationOperation subtotalAsNumber() {
        return context -> new Document("$addFields", new Document(SUBTOTAL_VALUE,
                new Document("$toDecimal", new Document("$ifNull", Arrays.asList("$items.subtotal", 0)))));
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
