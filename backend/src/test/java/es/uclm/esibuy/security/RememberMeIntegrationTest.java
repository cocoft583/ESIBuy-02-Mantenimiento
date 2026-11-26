package es.uclm.esibuy.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import es.uclm.esibuy.security.config.SecurityConfig;
import es.uclm.esibuy.security.service.EmailOtpService;
import es.uclm.esibuy.security.service.EncryptionService;
import es.uclm.esibuy.security.service.LoginAttemptService;
import es.uclm.esibuy.security.service.RememberMeService;
import es.uclm.esibuy.security.service.SessionAuthenticationService;
import es.uclm.esibuy.security.service.TotpService;
import es.uclm.esibuy.security.service.TwoFactorAuthenticationService;
import es.uclm.esibuy.users.controller.UserController;
import es.uclm.esibuy.users.dto.LoginRequest;
import es.uclm.esibuy.users.model.User;
import es.uclm.esibuy.users.model.UserRole;
import es.uclm.esibuy.users.repository.UserRepository;
import es.uclm.esibuy.users.service.UserService;

/** «Recordar sesión» de la app (HU30, T30.10): login, filtro y logout. */
@WebMvcTest(controllers = UserController.class)
@Import({ SecurityConfig.class, SessionAuthenticationService.class })
class RememberMeIntegrationTest {

    private static final String USER_ID = "user-1";
    private static final Cookie REMEMBER_COOKIE = new Cookie(RememberMeService.COOKIE_NAME, "token-del-movil");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RememberMeService rememberMeService;

    @MockBean
    private UserService userService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private LoginAttemptService loginAttemptService;

    @MockBean
    private TotpService totpService;

    @MockBean
    private EncryptionService encryptionService;

    @MockBean
    private TwoFactorAuthenticationService twoFactorAuthenticationService;

    @MockBean
    private EmailOtpService emailOtpService;

    @Test
    void clienteQueIniciaSesionEnLaAppConRecordarRecibeElToken() throws Exception {
        User client = activeUser(UserRole.CLIENT);

        login(true);

        verify(rememberMeService).issue(eq(client), any());
    }

    @Test
    void sinPedirRecordarSesionNoSeDaToken() throws Exception {
        activeUser(UserRole.CLIENT);

        login(false);

        verify(rememberMeService, never()).issue(any(), any());
    }

    @Test
    void unVendedorNoRecibeTokenAunqueLoPida() throws Exception {
        activeUser(UserRole.SELLER);

        login(true);

        verify(rememberMeService, never()).issue(any(), any());
    }

    @Test
    void sinSesionPeroConUnTokenValidoSeVuelveAAbrirLaSesion() throws Exception {
        User client = activeUser(UserRole.CLIENT);
        when(rememberMeService.autoLogin(any(), any())).thenReturn(Optional.of(client));

        MockHttpSession session = (MockHttpSession) mockMvc.perform(get("/api/users/me").cookie(REMEMBER_COOKIE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID))
                .andReturn().getRequest().getSession(false);

        // Las siguientes peticiones ya van con la sesión: no se vuelve a consultar el token
        mockMvc.perform(get("/api/users/me").session(session)).andExpect(status().isOk());
        verify(rememberMeService).autoLogin(any(), any());
    }

    @Test
    void sinSesionYConUnTokenNoValidoSigueSinAutenticar() throws Exception {
        activeUser(UserRole.CLIENT);
        when(rememberMeService.autoLogin(any(), any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/users/me").cookie(REMEMBER_COOKIE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void conLaSesionAbiertaNoSeConsultaElToken() throws Exception {
        activeUser(UserRole.CLIENT);
        MockHttpSession session = login(true);

        mockMvc.perform(get("/api/users/me").session(session).cookie(REMEMBER_COOKIE))
                .andExpect(status().isOk());

        verify(rememberMeService, never()).autoLogin(any(), any());
    }

    @Test
    void cerrarSesionOlvidaElToken() throws Exception {
        activeUser(UserRole.CLIENT);
        MockHttpSession session = login(true);

        mockMvc.perform(post("/api/users/logout").session(session).cookie(REMEMBER_COOKIE).with(csrf()))
                .andExpect(status().isNoContent());

        verify(rememberMeService).forget(any(), any());
    }

    private User activeUser(UserRole role) {
        User user = new User("user@example.com", "Test", "User", role);
        user.setActive(true);
        ReflectionTestUtils.setField(user, "id", USER_ID);
        when(userService.login(any(LoginRequest.class))).thenReturn(user);
        when(userService.findById(USER_ID)).thenReturn(user);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        return user;
    }

    private MockHttpSession login(boolean rememberMe) throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/api/users/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.com","password":"UserPassword123!","rememberMe":%s}
                                """.formatted(rememberMe)))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }
}
