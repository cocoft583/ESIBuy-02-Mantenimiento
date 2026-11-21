package es.uclm.esibuy.statistics.repository;

/**
 * Un producto con valoraciones, tal como lo guarda la base de datos.
 * {@code sellerId} puede ser null en productos antiguos.
 */
public record ProductRatingRow(
        String productId,
        String productName,
        String sellerId,
        double rating,
        int reviewCount) {
}
