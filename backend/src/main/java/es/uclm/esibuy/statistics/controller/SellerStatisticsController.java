package es.uclm.esibuy.statistics.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import es.uclm.esibuy.security.AuthenticatedUserPrincipal;
import es.uclm.esibuy.statistics.dto.SalesStatisticsDTO;
import es.uclm.esibuy.statistics.model.StatisticsScope;
import es.uclm.esibuy.statistics.service.StatisticsService;

/**
 * Estadísticas del vendedor de la sesión. Va aparte de {@link StatisticsController}, que es
 * solo del administrador, y de GET /api/products/summary, que sigue con sus métricas rápidas.
 */
@RestController
@RequestMapping("/api/seller/statistics")
@PreAuthorize("hasRole('SELLER')")
public class SellerStatisticsController {

    private final StatisticsService statisticsService;

    public SellerStatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    /**
     * Los 10 productos más vendidos y los 10 menos vendidos del vendedor, incluidos los que no
     * se han vendido nunca. El vendedor sale siempre de la sesión: no se acepta por parámetro,
     * así que nadie puede pedir las ventas de otro.
     */
    @GetMapping("/sales")
    public SalesStatisticsDTO getSales(@AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return statisticsService.getSales(StatisticsScope.VENDEDOR, principal.id());
    }
}
