package es.uclm.esibuy.statistics.repository;

import java.util.List;

import org.springframework.stereotype.Repository;

@Repository
public class MongoStatisticsRepository implements StatisticsRepository {

    /**
     * Pendiente de T22.2, que añade la agregación sobre los pedidos. Hasta entonces el
     * ranking sale vacío, y ningún endpoint lo expone todavía.
     */
    @Override
    public List<ProductSalesRow> findTopSellingProducts(int limit) {
        return List.of();
    }
}
