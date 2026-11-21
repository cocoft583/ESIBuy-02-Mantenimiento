package es.uclm.esibuy.statistics.repository;

import java.util.List;

public interface StatisticsRepository {

    /**
     * Productos más vendidos, de más a menos, contando solo los pedidos pagados que no
     * están cancelados. Devuelve como máximo {@code limit} filas.
     */
    List<ProductSalesRow> findTopSellingProducts(int limit);
}
