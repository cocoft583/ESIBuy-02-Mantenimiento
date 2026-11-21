package es.uclm.esibuy.statistics.repository;

import java.math.BigDecimal;

/**
 * Resultado de agrupar por producto las líneas de los pedidos que cuentan como venta.
 * {@code sellerId} es el que guarda la línea del pedido y puede ser null.
 */
public record ProductSalesRow(
        String productId,
        String productName,
        String sellerId,
        long unitsSold,
        BigDecimal revenue) {
}
