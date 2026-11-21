package es.uclm.esibuy.statistics.dto;

/**
 * Una fila del ranking de productos mejor valorados. La nota va redondeada a dos decimales.
 * El vendedor puede venir a null si el producto no lo conserva.
 */
public record ProductRatingDTO(
        String productId,
        String productName,
        String sellerId,
        String sellerName,
        double rating,
        int reviewCount) {
}
