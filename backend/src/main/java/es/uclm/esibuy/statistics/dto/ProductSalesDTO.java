package es.uclm.esibuy.statistics.dto;

import java.math.BigDecimal;

/**
 * Una fila del ranking de ventas: un producto con lo que se ha vendido de él.
 * El vendedor puede venir a null si ni la línea del pedido ni el producto lo conservan.
 */
public record ProductSalesDTO(
        String productId,
        String productName,
        String sellerId,
        String sellerName,
        long unitsSold,
        BigDecimal revenue) {
}
