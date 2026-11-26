package es.uclm.esibuy.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

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

@WebMvcTest(controllers = UserController.class)
@Import({ SecurityConfig.class, SessionAuthenticationService.class })
class SessionRevocationIntegrationTest {

    private static final String CLIENT_ID = "client-1";

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
    void blockingUserClosesEveryOpenSession() throws Exception {
        User client = activeClient();

        MockHttpSession firstBrowser = login();
        MockHttpSession secondBrowser = login();
        mockMvc.perform(get("/api/users/me").session(firstBrowser))
                .andExpect(status().isOk());

        // Lo que hace AdminService.blockUser
        client.setActive(false);
        client.setBlocked(true);
        client.revokeSessions();

        mockMvc.perform(get("/api/users/me").session(firstBrowser))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
        mockMvc.perform(get("/api/users/me").session(secondBrowser))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
    }

    @Test
    void activeUserKeepsSession() throws Exception {
        activeClient();

        MockHttpSession session = login();

        mockMvc.perform(get("/api/users/me").session(session)).andExpect(status().isOk());
        mockMvc.perform(get("/api/users/me").session(session)).andExpect(status().isOk());
    }

    private User activeClient() {
        User client = new User("client@example.com", "Test", "User", UserRole.CLIENT);
        client.setActive(true);
        ReflectionTestUtils.setField(client, "id", CLIENT_ID);
        when(userService.login(any(LoginRequest.class))).thenReturn(client);
        when(userService.findById(CLIENT_ID)).thenReturn(client);
        when(userRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client));
        return client;
    }

    private MockHttpSession login() throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/api/users/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"client@example.com","password":"ClientPassword123!"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }
}
