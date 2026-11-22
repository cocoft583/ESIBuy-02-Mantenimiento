package es.uclm.esibuy.statistics.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import es.uclm.esibuy.statistics.dto.ProductSalesDTO;
import es.uclm.esibuy.statistics.dto.RatingStatisticsDTO;
import es.uclm.esibuy.statistics.service.StatisticsService;

@RestController
@RequestMapping("/api/admin/statistics")
@PreAuthorize("hasRole('ADMIN')")
public class StatisticsController {

    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    /**
     * Los 10 productos más vendidos de la plataforma, de más a menos unidades.
     */
    @GetMapping("/sales")
    public List<ProductSalesDTO> getSales() {
        return statisticsService.getTopSellingProducts();
    }

    /**
     * Los productos y vendedores mejor valorados y la media de la plataforma, separado de las ventas.
     */
    @GetMapping("/ratings")
    public RatingStatisticsDTO getRatings() {
        return statisticsService.getRatingStatistics();
    }
}
