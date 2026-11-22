package es.uclm.esibuy.statistics.controller;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

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

import es.uclm.esibuy.common.exception.ApiExceptionHandler;
import es.uclm.esibuy.security.AuthenticatedUserPrincipal;
import es.uclm.esibuy.security.config.SecurityConfig;
import es.uclm.esibuy.statistics.dto.ProductRatingDTO;
import es.uclm.esibuy.statistics.dto.RatingStatisticsDTO;
import es.uclm.esibuy.statistics.dto.SellerRatingDTO;
import es.uclm.esibuy.statistics.service.StatisticsService;
import es.uclm.esibuy.users.model.UserRole;

@WebMvcTest(StatisticsController.class)
@Import({ ApiExceptionHandler.class, SecurityConfig.class })
class StatisticsRatingsControllerTest {

    private static final String API = "/api/admin/statistics/ratings";

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
    @DisplayName("HU23-CP16: el administrador ve los productos y vendedores mejor valorados y la media de la plataforma")
    void shouldShowTheRatingStatisticsToAnAdmin() throws Exception {
        when(statisticsService.getRatingStatistics()).thenReturn(new RatingStatisticsDTO(
                List.of(new ProductRatingDTO("p1", "Auriculares", "v1", "Sonido SL", 4.75, 8),
                        new ProductRatingDTO("p2", "Altavoz", null, null, 4.5, 3)),
                List.of(new SellerRatingDTO("v1", "Sonido SL", 4.6, 2)),
                4.25,
                3));

        mockMvc.perform(get(API).with(authentication(authenticationFor(UserRole.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topProducts.length()").value(2))
                .andExpect(jsonPath("$.topProducts[0].productId").value("p1"))
                .andExpect(jsonPath("$.topProducts[0].productName").value("Auriculares"))
                .andExpect(jsonPath("$.topProducts[0].sellerName").value("Sonido SL"))
                .andExpect(jsonPath("$.topProducts[0].rating").value(4.75))
                .andExpect(jsonPath("$.topProducts[0].reviewCount").value(8))
                .andExpect(jsonPath("$.topProducts[1].sellerId").doesNotExist())
                .andExpect(jsonPath("$.topSellers[0].sellerId").value("v1"))
                .andExpect(jsonPath("$.topSellers[0].averageRating").value(4.6))
                .andExpect(jsonPath("$.topSellers[0].ratedProducts").value(2))
                .andExpect(jsonPath("$.platformAverage").value(4.25))
                .andExpect(jsonPath("$.minimumReviews").value(3));
    }

    @Test
    @DisplayName("HU23-CP17: sin valoraciones el administrador recibe listas vacías y la media de la plataforma a null")
    void shouldReturnEmptyRankingsWhenNothingIsRated() throws Exception {
        when(statisticsService.getRatingStatistics())
                .thenReturn(new RatingStatisticsDTO(List.of(), List.of(), null, 3));

        mockMvc.perform(get(API).with(authentication(authenticationFor(UserRole.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topProducts").isEmpty())
                .andExpect(jsonPath("$.topSellers").isEmpty())
                .andExpect(jsonPath("$.platformAverage").doesNotExist())
                .andExpect(jsonPath("$.minimumReviews").value(3));
    }

    @Test
    @DisplayName("HU23-CP18: un vendedor o un cliente no pueden ver las valoraciones de la plataforma")
    void shouldForbidSellersAndCustomers() throws Exception {
        mockMvc.perform(get(API).with(authentication(authenticationFor(UserRole.SELLER))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(API).with(authentication(authenticationFor(UserRole.CLIENT))))
                .andExpect(status().isForbidden());

        verify(statisticsService, never()).getRatingStatistics();
    }

    @Test
    @DisplayName("HU23-CP19: sin sesión la petición se rechaza")
    void shouldRejectAnonymousRequests() throws Exception {
        mockMvc.perform(get(API))
                .andExpect(status().isUnauthorized());

        verify(statisticsService, never()).getRatingStatistics();
    }
}
