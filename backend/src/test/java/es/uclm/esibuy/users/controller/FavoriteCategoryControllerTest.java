package es.uclm.esibuy.users.controller;

import java.util.List;

import es.uclm.esibuy.common.exception.ApiExceptionHandler;
import es.uclm.esibuy.common.exception.CategoryIdNotFoundException;
import es.uclm.esibuy.security.AuthenticatedUserPrincipal;
import es.uclm.esibuy.security.config.SecurityConfig;
import es.uclm.esibuy.users.model.UserRole;
import es.uclm.esibuy.users.service.FavoriteCategoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FavoriteCategoryController.class)
@Import({ ApiExceptionHandler.class, SecurityConfig.class })
class FavoriteCategoryControllerTest {

    private static final String API = "/api/users/me/favorite-categories";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FavoriteCategoryService favoriteCategoryService;

    private Authentication authenticationFor(UserRole role) {
        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
                "user-1", "ana@example.com", role, true);
        return new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }

    @Test
    @DisplayName("HU26-CP14: el cliente consulta sus categorías favoritas")
    void shouldListTheFavoriteCategoriesOfTheClient() throws Exception {
        when(favoriteCategoryService.list("user-1")).thenReturn(List.of("moda", "hogar"));

        mockMvc.perform(get(API).with(authentication(authenticationFor(UserRole.CLIENT))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryIds[0]").value("moda"))
                .andExpect(jsonPath("$.categoryIds[1]").value("hogar"));
    }

    @Test
    @DisplayName("HU26-CP15: el cliente elige sus categorías favoritas")
    void shouldReplaceTheFavoriteCategoriesOfTheClient() throws Exception {
        when(favoriteCategoryService.replace("user-1", List.of("moda", "deporte")))
                .thenReturn(List.of("moda", "deporte"));

        mockMvc.perform(put(API)
                .with(authentication(authenticationFor(UserRole.CLIENT)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryIds\":[\"moda\",\"deporte\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryIds[0]").value("moda"))
                .andExpect(jsonPath("$.categoryIds[1]").value("deporte"));

        verify(favoriteCategoryService).replace("user-1", List.of("moda", "deporte"));
    }

    @Test
    @DisplayName("HU26-CP16: el cliente quita todas sus favoritas enviando una lista vacía")
    void shouldAllowTheClientToRemoveAllFavorites() throws Exception {
        when(favoriteCategoryService.replace("user-1", List.of())).thenReturn(List.of());

        mockMvc.perform(put(API)
                .with(authentication(authenticationFor(UserRole.CLIENT)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryIds\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryIds").isEmpty());
    }

    @Test
    @DisplayName("HU26-CP17: elegir una categoría que ya no existe devuelve 404")
    void shouldReturnNotFoundWhenTheCategoryDoesNotExist() throws Exception {
        when(favoriteCategoryService.replace("user-1", List.of("borrada")))
                .thenThrow(new CategoryIdNotFoundException());

        mockMvc.perform(put(API)
                .with(authentication(authenticationFor(UserRole.CLIENT)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryIds\":[\"borrada\"]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("La categoría ya no existe"));
    }

    @Test
    @DisplayName("HU26-CP18: sin la lista de categorías la petición es inválida")
    void shouldRejectARequestWithoutTheCategoryList() throws Exception {
        mockMvc.perform(put(API)
                .with(authentication(authenticationFor(UserRole.CLIENT)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());

        verify(favoriteCategoryService, never()).replace(any(), any());
    }

    @Test
    @DisplayName("HU26-CP19: sin sesión no se puede consultar ni cambiar las favoritas")
    void shouldRejectRequestsWithoutSession() throws Exception {
        mockMvc.perform(get(API)).andExpect(status().isUnauthorized());
        mockMvc.perform(put(API).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryIds\":[]}"))
                .andExpect(status().isUnauthorized());

        verify(favoriteCategoryService, never()).replace(any(), any());
    }

    @Test
    @DisplayName("HU26-CP20: los vendedores y los administradores no tienen categorías favoritas")
    void shouldForbidSellersAndAdmins() throws Exception {
        for (UserRole role : List.of(UserRole.SELLER, UserRole.ADMIN)) {
            mockMvc.perform(get(API).with(authentication(authenticationFor(role))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(put(API)
                    .with(authentication(authenticationFor(role)))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"categoryIds\":[\"moda\"]}"))
                    .andExpect(status().isForbidden());
        }

        verify(favoriteCategoryService, never()).list(any());
        verify(favoriteCategoryService, never()).replace(any(), any());
    }

    @Test
    @DisplayName("HU26-CP21: cambiar las favoritas exige el token CSRF")
    void shouldRejectTheChangeWithoutCsrfToken() throws Exception {
        mockMvc.perform(put(API)
                .with(authentication(authenticationFor(UserRole.CLIENT)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryIds\":[\"moda\"]}"))
                .andExpect(status().isForbidden());

        verify(favoriteCategoryService, never()).replace(any(), any());
    }
}
