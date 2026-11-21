package es.uclm.esibuy.statistics.service;

import java.util.List;

import org.springframework.stereotype.Service;

import es.uclm.esibuy.statistics.dto.ProductSalesDTO;
import es.uclm.esibuy.statistics.repository.StatisticsRepository;

@Service
public class StatisticsService {

    /** Tamaño del ranking: con empate en el puesto 10 se enseñan solo 10 (RN-01). */
    static final int RANKING_SIZE = 10;

    private final StatisticsRepository statisticsRepository;

    public StatisticsService(StatisticsRepository statisticsRepository) {
        this.statisticsRepository = statisticsRepository;
    }

    public List<ProductSalesDTO> getTopSellingProducts() {
        return statisticsRepository.findTopSellingProducts(RANKING_SIZE).stream()
                .map(row -> new ProductSalesDTO(
                        row.productId(),
                        row.productName(),
                        row.sellerId(),
                        null,
                        row.unitsSold(),
                        row.revenue()))
                .toList();
    }
}
