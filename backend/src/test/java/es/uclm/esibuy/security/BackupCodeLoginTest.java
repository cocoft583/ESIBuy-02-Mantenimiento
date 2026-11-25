package es.uclm.esibuy.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

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
import es.uclm.esibuy.users.service.UserService;

/** HU09: los códigos de respaldo sirven para entrar una vez cada uno y no se guardan en claro. */
@WebMvcTest(controllers = UserController.class)
@Import({ SecurityConfig.class, SessionAuthenticationService.class, TwoFactorAuthenticationService.class })
class BackupCodeLoginTest {

    private static final String CLIENT_ID = "client-1";
    private static final List<String> GENERATED_CODES = List.of(
            "11111111", "22222222", "33333333", "44444444",
            "55555555", "66666666", "77777777", "88888888");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TwoFactorAuthenticationService twoFactorAuthenticationService;

    @MockBean
    private RememberMeService rememberMeService;

    @MockBean
    private UserService userService;

    @MockBean
    private LoginAttemptService loginAttemptService;

    @MockBean
    private TotpService totpService;

    @MockBean
    private EncryptionService encryptionService;

    @MockBean
    private EmailOtpService emailOtpService;

    private User client;

    @BeforeEach
    void enableTwoFactor() {
        client = new User("client@example.com", "Test", "User", UserRole.CLIENT);
        client.setActive(true);
        client.setTotpSecret("encrypted-secret");
        ReflectionTestUtils.setField(client, "id", CLIENT_ID);
        when(userService.findById(CLIENT_ID)).thenReturn(client);
        when(userService.login(any(LoginRequest.class))).thenReturn(client);
        when(encryptionService.decrypt("encrypted-secret")).thenReturn("raw-secret");
        // Solo "123456" es un código válido de la app de autenticación
        when(totpService.verifyCode(anyString(), anyString())).thenReturn(false);
        when(totpService.verifyCode("raw-secret", "123456")).thenReturn(true);
        when(totpService.generateBackupCodes(8)).thenReturn(GENERATED_CODES);

        TwoFactorAuthenticationService.EnableResult result = twoFactorAuthenticationService.enable(CLIENT_ID, "123456");

        assertEquals(GENERATED_CODES, result.backupCodes());
    }

    @Test
    void storesNoBackupCodeInPlainText() {
        List<String> stored = client.getBackupCodeHashes();

        assertEquals(GENERATED_CODES.size(), stored.size());
        for (String hash : stored) {
            assertFalse(GENERATED_CODES.contains(hash));
            assertTrue(hash.startsWith("$2"), "Se esperaba un hash BCrypt");
        }
    }

    @Test
    void backupCodeLogsInOnlyOnce() throws Exception {
        loginWith("33333333").andExpect(status().isOk());

        loginWith("33333333").andExpect(status().isUnauthorized());

        assertEquals(GENERATED_CODES.size() - 1, client.getBackupCodeHashes().size());
    }

    @Test
    void otherBackupCodesKeepWorkingAfterOneIsUsed() throws Exception {
        loginWith("33333333").andExpect(status().isOk());

        loginWith("44444444").andExpect(status().isOk());
    }

    @Test
    void rejectsCodeThatWasNeverGenerated() throws Exception {
        loginWith("99999999").andExpect(status().isUnauthorized());

        assertEquals(GENERATED_CODES.size(), client.getBackupCodeHashes().size());
    }

    private ResultActions loginWith(String code) throws Exception {
        return mockMvc.perform(post("/api/users/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"client@example.com","password":"ClientPassword123!","totpCode":"%s"}
                        """.formatted(code)));
    }
}
