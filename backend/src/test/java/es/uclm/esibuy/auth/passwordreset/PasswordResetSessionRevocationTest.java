package es.uclm.esibuy.auth.passwordreset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import es.uclm.esibuy.auth.passwordreset.controller.PasswordResetController;
import es.uclm.esibuy.auth.passwordreset.model.PasswordResetToken;
import es.uclm.esibuy.auth.passwordreset.repository.PasswordResetTokenRepository;
import es.uclm.esibuy.auth.passwordreset.service.PasswordResetEmailService;
import es.uclm.esibuy.auth.passwordreset.service.PasswordResetRateLimiter;
import es.uclm.esibuy.auth.passwordreset.service.PasswordResetService;
import es.uclm.esibuy.security.config.SecurityConfig;
import es.uclm.esibuy.security.password.CompromisedPasswordService;
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

@WebMvcTest(controllers = { UserController.class, PasswordResetController.class })
@Import({ SecurityConfig.class, SessionAuthenticationService.class, PasswordResetService.class })
class PasswordResetSessionRevocationTest {

    private static final String CLIENT_ID = "client-1";
    private static final String RAW_TOKEN = "reset-token";

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

    @MockBean
    private PasswordResetTokenRepository tokenRepository;

    @MockBean
    private PasswordResetRateLimiter rateLimiter;

    @MockBean
    private MongoTemplate mongoTemplate;

    @MockBean
    private PasswordResetEmailService passwordResetEmailService;

    @MockBean
    private CompromisedPasswordService compromisedPasswordService;

    @Test
    void openSessionStopsWorkingAfterPasswordReset() throws Exception {
        activeClient();
        MockHttpSession otherDevice = login();
        mockMvc.perform(get("/api/users/me").session(otherDevice))
                .andExpect(status().isOk());

        resetPassword();

        mockMvc.perform(get("/api/users/me").session(otherDevice))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
    }

    @Test
    void openSessionKeepsWorkingWithoutPasswordReset() throws Exception {
        activeClient();
        MockHttpSession otherDevice = login();

        mockMvc.perform(get("/api/users/me").session(otherDevice)).andExpect(status().isOk());
        mockMvc.perform(get("/api/users/me").session(otherDevice)).andExpect(status().isOk());
    }

    private void activeClient() {
        User client = new User("client@example.com", "Test", "User", UserRole.CLIENT);
        client.setActive(true);
        ReflectionTestUtils.setField(client, "id", CLIENT_ID);
        when(userService.login(any(LoginRequest.class))).thenReturn(client);
        when(userService.findById(CLIENT_ID)).thenReturn(client);
        when(userRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client));
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

    private void resetPassword() throws Exception {
        Instant now = Instant.now();
        PasswordResetToken token = new PasswordResetToken("hash", CLIENT_ID, now, now.plusSeconds(900));
        ReflectionTestUtils.setField(token, "id", "token-1");
        when(tokenRepository.findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(anyString(), any(Instant.class)))
                .thenReturn(Optional.of(token));
        when(mongoTemplate.findAndModify(
                any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(PasswordResetToken.class)))
                .thenReturn(token);

        // Desde otro navegador, sin sesión: el enlace del correo no requiere estar autenticado
        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","newPassword":"NuevaClave123!#"}
                                """.formatted(RAW_TOKEN)))
                .andExpect(status().isOk());
    }
}
