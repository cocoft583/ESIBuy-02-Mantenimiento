package es.uclm.esibuy.statistics.repository;

import java.util.List;

public interface StatisticsRepository {

    /**
     * Productos más vendidos, de más a menos, contando solo los pedidos pagados que no
     * están cancelados. Devuelve como máximo {@code limit} filas.
     */
    List<ProductSalesRow> findTopSellingProducts(int limit);

    /**
     * Productos mejor valorados, de más a menos nota, entre los que tienen al menos
     * {@code minimumReviews} valoraciones. Con la misma nota va primero el que tiene más
     * valoraciones. Devuelve como máximo {@code limit} filas.
     */
    List<ProductRatingRow> findTopRatedProducts(int minimumReviews, int limit);

    /**
     * Vendedores mejor valorados, de más a menos, con la media de las medias de sus productos
     * que tienen valoraciones. Devuelve como máximo {@code limit} filas.
     */
    List<SellerRatingRow> findTopRatedSellers(int limit);

    /**
     * Media de las valoraciones medias de todos los vendedores que tienen alguna,
     * o null si ninguno la tiene.
     */
    Double findPlatformAverageRating();
}
