package es.uclm.esibuy.statistics.dto;

import java.util.List;

/**
 * Estadísticas de valoraciones de la plataforma.
 * {@code platformAverage} es la media de las valoraciones medias de los vendedores y viene a null
 * si todavía no hay ninguna. {@code minimumReviews} es el mínimo que se pide a un producto
 * para entrar en el ranking.
 */
public record RatingStatisticsDTO(
        List<ProductRatingDTO> topProducts,
        List<SellerRatingDTO> topSellers,
        Double platformAverage,
        int minimumReviews) {
}
