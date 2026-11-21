package es.uclm.esibuy.statistics.repository;

/**
 * Valoración media de un vendedor: la media de las medias de sus productos con valoraciones.
 * {@code ratedProducts} es cuántos productos suyos entran en esa media.
 */
public record SellerRatingRow(
        String sellerId,
        double averageRating,
        int ratedProducts) {
}
