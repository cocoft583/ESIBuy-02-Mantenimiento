package es.uclm.esibuy.statistics.controller;

import java.math.BigDecimal;
import java.util.List;

import es.uclm.esibuy.common.exception.ApiExceptionHandler;
import es.uclm.esibuy.security.AuthenticatedUserPrincipal;
import es.uclm.esibuy.security.config.SecurityConfig;
import es.uclm.esibuy.statistics.dto.ProductSalesDTO;
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

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StatisticsController.class)
@Import({ ApiExceptionHandler.class, SecurityConfig.class })
class StatisticsControllerTest {

    private static final String API = "/api/admin/statistics/sales";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StatisticsService statisticsService;

    private Authentication authenticationFor(UserRole role) {
        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
                "user-1", "ana@example.com", role, true);
        return new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }

    @Test
    @DisplayName("HU22-CP15: el administrador ve el ranking con vendedor, unidades e ingresos")
    void shouldShowTheSalesRankingToAnAdmin() throws Exception {
        when(statisticsService.getTopSellingProducts()).thenReturn(List.of(
                new ProductSalesDTO("p1", "Auriculares", "v1", "Sonido SL", 7, new BigDecimal("140.50")),
                new ProductSalesDTO("p2", "Altavoz", null, null, 3, new BigDecimal("90.00"))));

        mockMvc.perform(get(API).with(authentication(authenticationFor(UserRole.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].productId").value("p1"))
                .andExpect(jsonPath("$[0].productName").value("Auriculares"))
                .andExpect(jsonPath("$[0].sellerId").value("v1"))
                .andExpect(jsonPath("$[0].sellerName").value("Sonido SL"))
                .andExpect(jsonPath("$[0].unitsSold").value(7))
                .andExpect(jsonPath("$[0].revenue").value(140.50))
                .andExpect(jsonPath("$[1].sellerId").doesNotExist());
    }

    @Test
    @DisplayName("HU22-CP16: sin ventas el administrador recibe una lista vacía")
    void shouldReturnAnEmptyListWhenThereAreNoSales() throws Exception {
        when(statisticsService.getTopSellingProducts()).thenReturn(List.of());

        mockMvc.perform(get(API).with(authentication(authenticationFor(UserRole.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("HU22-CP17: un vendedor o un cliente no pueden ver las ventas de la plataforma")
    void shouldForbidSellersAndClients() throws Exception {
        for (UserRole role : List.of(UserRole.SELLER, UserRole.CLIENT)) {
            mockMvc.perform(get(API).with(authentication(authenticationFor(role))))
                    .andExpect(status().isForbidden());
        }

        verify(statisticsService, never()).getTopSellingProducts();
    }

    @Test
    @DisplayName("HU22-CP18: sin sesión no se puede ver el ranking")
    void shouldRejectRequestsWithoutSession() throws Exception {
        mockMvc.perform(get(API)).andExpect(status().isUnauthorized());

        verify(statisticsService, never()).getTopSellingProducts();
    }
}
