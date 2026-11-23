package es.uclm.esibuy.statistics.dto;

import java.util.List;

/**
 * Valoraciones del vendedor de la sesión (HU25): sus 10 productos mejor valorados, sus 10 peor
 * valorados y su valoración media, que es la misma cifra que ve el administrador (RN-06).
 * {@code averageRating} viene a null si ningún producto suyo tiene valoraciones, y
 * {@code platformAverage} si no las tiene nadie en la plataforma.
 */
public record SellerRatingStatisticsDTO(
        List<ProductRatingDTO> topRated,
        List<ProductRatingDTO> worstRated,
        Double averageRating,
        int ratedProducts,
        Double platformAverage) {
}
