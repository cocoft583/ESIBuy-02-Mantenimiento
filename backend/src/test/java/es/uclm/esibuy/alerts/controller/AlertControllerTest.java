package es.uclm.esibuy.alerts.controller;

import java.time.Instant;
import java.util.List;

import es.uclm.esibuy.alerts.dto.AlertDTO;
import es.uclm.esibuy.alerts.model.AlertType;
import es.uclm.esibuy.alerts.service.AlertService;
import es.uclm.esibuy.common.exception.AlertNotFoundException;
import es.uclm.esibuy.common.exception.ApiExceptionHandler;
import es.uclm.esibuy.security.AuthenticatedUserPrincipal;
import es.uclm.esibuy.security.config.SecurityConfig;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AlertController.class)
@Import({ ApiExceptionHandler.class, SecurityConfig.class })
class AlertControllerTest {

    private static final String API = "/api/customer/alerts";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AlertService alertService;

    private Authentication authenticationFor(UserRole role) {
        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
                "cliente-1", "ana@example.com", role, true);
        return new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }

    private Authentication client() {
        return authenticationFor(UserRole.CLIENT);
    }

    @Test
    @DisplayName("HU29-CP15: el cliente ve su bandeja con tipo, producto, mensaje, fecha y si está leída")
    void shouldShowTheInboxOfTheClient() throws Exception {
        Instant now = Instant.parse("2026-11-21T10:00:00Z");
        when(alertService.list("cliente-1")).thenReturn(List.of(
                new AlertDTO("a2", AlertType.AVAILABILITY, "p1", "Auriculares", "Vuelve a estar disponible", now, false),
                new AlertDTO("a1", AlertType.PRICE_DROP, "p2", "Altavoz", "Ha bajado de precio", now.minusSeconds(60), true)));

        mockMvc.perform(get(API).with(authentication(client())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("a2"))
                .andExpect(jsonPath("$[0].type").value("AVAILABILITY"))
                .andExpect(jsonPath("$[0].productId").value("p1"))
                .andExpect(jsonPath("$[0].productName").value("Auriculares"))
                .andExpect(jsonPath("$[0].message").value("Vuelve a estar disponible"))
                .andExpect(jsonPath("$[0].createdAt").exists())
                .andExpect(jsonPath("$[0].read").value(false))
                .andExpect(jsonPath("$[1].read").value(true));
    }

    @Test
    @DisplayName("HU29-CP16: el contador de la campana devuelve las alertas sin leer")
    void shouldShowTheUnreadCount() throws Exception {
        when(alertService.countUnread("cliente-1")).thenReturn(4L);

        mockMvc.perform(get(API + "/unread-count").with(authentication(client())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(4));
    }

    @Test
    @DisplayName("HU29-CP17: el cliente marca una alerta como leída")
    void shouldMarkAnAlertAsRead() throws Exception {
        mockMvc.perform(patch(API + "/a1/read").with(authentication(client())).with(csrf()))
                .andExpect(status().isNoContent());

        verify(alertService).markAsRead("cliente-1", "a1");
    }

    @Test
    @DisplayName("HU29-CP18: el cliente marca todas sus alertas como leídas")
    void shouldMarkAllTheAlertsAsRead() throws Exception {
        mockMvc.perform(patch(API + "/read-all").with(authentication(client())).with(csrf()))
                .andExpect(status().isNoContent());

        verify(alertService).markAllAsRead("cliente-1");
    }

    @Test
    @DisplayName("HU29-CP19: el cliente elimina una alerta")
    void shouldDeleteAnAlert() throws Exception {
        mockMvc.perform(delete(API + "/a1").with(authentication(client())).with(csrf()))
                .andExpect(status().isNoContent());

        verify(alertService).delete("cliente-1", "a1");
    }

    @Test
    @DisplayName("HU29-CP20: una alerta ajena o inexistente responde 404 al marcarla o eliminarla")
    void shouldReturnNotFoundForAnotherCustomersAlert() throws Exception {
        doThrow(new AlertNotFoundException()).when(alertService).markAsRead("cliente-1", "ajena");
        doThrow(new AlertNotFoundException()).when(alertService).delete("cliente-1", "ajena");

        mockMvc.perform(patch(API + "/ajena/read").with(authentication(client())).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("La alerta no existe"));
        mockMvc.perform(delete(API + "/ajena").with(authentication(client())).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("La alerta no existe"));
    }

    @Test
    @DisplayName("HU29-CP21: sin sesión ninguna operación de la bandeja está permitida")
    void shouldRejectRequestsWithoutSession() throws Exception {
        for (MockHttpServletRequestBuilder request : List.of(
                get(API), get(API + "/unread-count"),
                patch(API + "/a1/read").with(csrf()), patch(API + "/read-all").with(csrf()),
                delete(API + "/a1").with(csrf()))) {
            mockMvc.perform(request).andExpect(status().isUnauthorized());
        }

        verifyNoInteractions(alertService);
    }

    @Test
    @DisplayName("HU29-CP22: los vendedores y los administradores no tienen bandeja de alertas")
    void shouldForbidSellersAndAdmins() throws Exception {
        for (UserRole role : List.of(UserRole.SELLER, UserRole.ADMIN)) {
            mockMvc.perform(get(API).with(authentication(authenticationFor(role))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(API + "/unread-count").with(authentication(authenticationFor(role))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(patch(API + "/read-all").with(authentication(authenticationFor(role))).with(csrf()))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete(API + "/a1").with(authentication(authenticationFor(role))).with(csrf()))
                    .andExpect(status().isForbidden());
        }

        verifyNoInteractions(alertService);
    }

    @Test
    @DisplayName("HU29-CP23: marcar y eliminar exigen el token CSRF")
    void shouldRejectChangesWithoutCsrfToken() throws Exception {
        mockMvc.perform(patch(API + "/a1/read").with(authentication(client())))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(API + "/a1").with(authentication(client())))
                .andExpect(status().isForbidden());

        verify(alertService, never()).markAsRead(any(), any());
        verify(alertService, never()).delete(any(), any());
    }
}
