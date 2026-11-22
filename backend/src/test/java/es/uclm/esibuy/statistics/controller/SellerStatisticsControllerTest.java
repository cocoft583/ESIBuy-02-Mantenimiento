package es.uclm.esibuy.statistics.controller;

import java.math.BigDecimal;
import java.util.List;

import es.uclm.esibuy.common.exception.ApiExceptionHandler;
import es.uclm.esibuy.security.AuthenticatedUserPrincipal;
import es.uclm.esibuy.security.config.SecurityConfig;
import es.uclm.esibuy.statistics.dto.ProductSalesDTO;
import es.uclm.esibuy.statistics.dto.SalesStatisticsDTO;
import es.uclm.esibuy.statistics.model.StatisticsScope;
import es.uclm.esibuy.statistics.service.StatisticsService;
import es.uclm.esibuy.users.model.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SellerStatisticsController.class)
@Import({ ApiExceptionHandler.class, SecurityConfig.class })
class SellerStatisticsControllerTest {

    private static final String API = "/api/seller/statistics/sales";
    private static final String SELLER_ID = "vendedor-1";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StatisticsService statisticsService;

    private Authentication authenticationFor(String userId, UserRole role) {
        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
                userId, "ana@example.com", role, true);
        return new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }

    private SalesStatisticsDTO sellerSales() {
        return new SalesStatisticsDTO(
                StatisticsScope.VENDEDOR,
                List.of(new ProductSalesDTO("p1", "Teclado", SELLER_ID, "Tienda Ana", 7, new BigDecimal("140.50"))),
                List.of(
                        new ProductSalesDTO("p2", "Ratón", SELLER_ID, "Tienda Ana", 0, new BigDecimal("0.00")),
                        new ProductSalesDTO("p1", "Teclado", SELLER_ID, "Tienda Ana", 7, new BigDecimal("140.50"))));
    }

    @Test
    @DisplayName("HU24-CP13: el vendedor ve sus más vendidos y sus menos vendidos, con los que no ha vendido")
    void shouldShowTheSellerItsTopAndLeastSoldProducts() throws Exception {
        when(statisticsService.getSales(StatisticsScope.VENDEDOR, SELLER_ID)).thenReturn(sellerSales());

        mockMvc.perform(get(API).with(authentication(authenticationFor(SELLER_ID, UserRole.SELLER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("VENDEDOR"))
                .andExpect(jsonPath("$.topSold.length()").value(1))
                .andExpect(jsonPath("$.topSold[0].productId").value("p1"))
                .andExpect(jsonPath("$.topSold[0].productName").value("Teclado"))
                .andExpect(jsonPath("$.topSold[0].unitsSold").value(7))
                .andExpect(jsonPath("$.topSold[0].revenue").value(140.50))
                .andExpect(jsonPath("$.leastSold.length()").value(2))
                .andExpect(jsonPath("$.leastSold[0].productId").value("p2"))
                .andExpect(jsonPath("$.leastSold[0].unitsSold").value(0));
    }

    @Test
    @DisplayName("HU24-CP14: el vendedor sale de la sesión y no se puede pedir el de otro por parámetro")
    void shouldAlwaysUseTheSellerOfTheSession() throws Exception {
        when(statisticsService.getSales(StatisticsScope.VENDEDOR, SELLER_ID)).thenReturn(sellerSales());

        mockMvc.perform(get(API)
                        .param("sellerId", "vendedor-2")
                        .param("vendedorId", "vendedor-2")
                        .with(authentication(authenticationFor(SELLER_ID, UserRole.SELLER))))
                .andExpect(status().isOk());

        verify(statisticsService).getSales(StatisticsScope.VENDEDOR, SELLER_ID);
        verify(statisticsService, never()).getSales(any(), org.mockito.ArgumentMatchers.eq("vendedor-2"));
    }

    @Test
    @DisplayName("HU24-CP15: un vendedor sin ventas ni productos recibe los rankings vacíos")
    void shouldReturnEmptyRankingsWhenTheSellerHasNothing() throws Exception {
        when(statisticsService.getSales(StatisticsScope.VENDEDOR, SELLER_ID))
                .thenReturn(new SalesStatisticsDTO(StatisticsScope.VENDEDOR, List.of(), List.of()));

        mockMvc.perform(get(API).with(authentication(authenticationFor(SELLER_ID, UserRole.SELLER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topSold").isEmpty())
                .andExpect(jsonPath("$.leastSold").isEmpty());
    }

    @Test
    @DisplayName("HU24-CP16: un cliente o un administrador no pueden ver las ventas de un vendedor")
    void shouldForbidClientsAndAdmins() throws Exception {
        for (UserRole role : List.of(UserRole.CLIENT, UserRole.ADMIN)) {
            mockMvc.perform(get(API).with(authentication(authenticationFor("user-1", role))))
                    .andExpect(status().isForbidden());
        }

        verify(statisticsService, never()).getSales(any(), any());
    }

    @Test
    @DisplayName("HU24-CP17: sin sesión no se pueden ver las ventas del vendedor")
    void shouldRejectRequestsWithoutSession() throws Exception {
        mockMvc.perform(get(API)).andExpect(status().isUnauthorized());

        verify(statisticsService, never()).getSales(any(), any());
    }
}
