package es.uclm.esibuy.statistics.dto;

import java.util.List;

import es.uclm.esibuy.statistics.model.StatisticsScope;

/**
 * Ranking de ventas de un ámbito. {@code leastSold} solo se rellena en el ámbito VENDEDOR
 * e incluye los productos que no se han vendido nunca (RF-EST-08).
 */
public record SalesStatisticsDTO(
        StatisticsScope scope,
        List<ProductSalesDTO> topSold,
        List<ProductSalesDTO> leastSold) {

    public SalesStatisticsDTO {
        topSold = topSold == null ? List.of() : List.copyOf(topSold);
        leastSold = leastSold == null ? List.of() : List.copyOf(leastSold);
    }
}
