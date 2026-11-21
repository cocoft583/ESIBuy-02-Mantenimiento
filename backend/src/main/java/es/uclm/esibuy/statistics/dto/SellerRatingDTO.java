package es.uclm.esibuy.statistics.dto;

/** Una fila del ranking de vendedores: su valoración media y de cuántos productos sale. */
public record SellerRatingDTO(
        String sellerId,
        String sellerName,
        double averageRating,
        int ratedProducts) {
}
