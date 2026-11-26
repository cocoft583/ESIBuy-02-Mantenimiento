package es.uclm.esibuy.security.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import es.uclm.esibuy.security.model.RememberMeToken;
import es.uclm.esibuy.security.repository.RememberMeTokenRepository;
import es.uclm.esibuy.users.model.User;
import es.uclm.esibuy.users.model.UserRole;
import es.uclm.esibuy.users.repository.UserRepository;

/** «Recordar sesión» de la app (HU30, T30.10). */
class RememberMeServiceTest {

    private static final String CLIENT_ID = "client-1";
    private static final String RAW_TOKEN = "token-en-la-cookie-del-movil";

    private RememberMeTokenRepository tokenRepository;
    private UserRepository userRepository;
    private RememberMeService service;
    private User client;

    @BeforeEach
    void setUp() {
        tokenRepository = mock(RememberMeTokenRepository.class);
        userRepository = mock(UserRepository.class);
        service = new RememberMeService(tokenRepository, userRepository, Duration.ofDays(30), true, "None");

        client = new User("client@example.com", "Test", "User", UserRole.CLIENT);
        client.setActive(true);
        ReflectionTestUtils.setField(client, "id", CLIENT_ID);
        when(userRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client));
    }

    @Test
    void issueGuardaSoloElHashDelTokenConLaVersionDeSesionYTreintaDias() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.issue(client, response);

        String cookieValue = rememberCookieValue(response);
        RememberMeToken saved = savedToken();
        assertThat(cookieValue).hasSizeGreaterThanOrEqualTo(43);
        assertThat(saved.getTokenHash())
                .isNotEqualTo(cookieValue)
                .isEqualTo(sha256(cookieValue));
        assertThat(saved.getUserId()).isEqualTo(CLIENT_ID);
        assertThat(saved.getSessionVersion()).isEqualTo(client.getSessionVersion());
        assertThat(saved.getExpiresAt())
                .isCloseTo(Instant.now().plus(30, ChronoUnit.DAYS), within(1, ChronoUnit.MINUTES));
    }

    @Test
    void issueMandaUnaCookieHttpOnlySeguraDeTreintaDiasSoloParaLaApi() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.issue(client, response);

        assertThat(rememberSetCookie(response))
                .contains("Max-Age=2592000")
                .contains("Path=/api")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=None");
    }

    @Test
    void cadaTokenNuevoEsDistinto() {
        MockHttpServletResponse first = new MockHttpServletResponse();
        MockHttpServletResponse second = new MockHttpServletResponse();

        service.issue(client, first);
        service.issue(client, second);

        assertThat(rememberCookieValue(first)).isNotEqualTo(rememberCookieValue(second));
    }

    @Test
    void autoLoginConTokenValidoDevuelveElUsuarioYRotaElToken() {
        RememberMeToken stored = storedToken(client.getSessionVersion(), Instant.now().plus(10, ChronoUnit.DAYS));
        MockHttpServletResponse response = new MockHttpServletResponse();

        Optional<User> user = service.autoLogin(requestWithCookie(RAW_TOKEN), response);

        assertThat(user).contains(client);
        verify(tokenRepository).delete(stored);
        String newCookie = rememberCookieValue(response);
        assertThat(newCookie).isNotEqualTo(RAW_TOKEN);
        assertThat(savedToken().getTokenHash()).isEqualTo(sha256(newCookie));
    }

    @Test
    void autoLoginSinCookieNoConsultaNada() {
        Optional<User> user = service.autoLogin(new MockHttpServletRequest(), new MockHttpServletResponse());

        assertThat(user).isEmpty();
        verifyNoInteractions(tokenRepository);
    }

    @Test
    void autoLoginConTokenDesconocidoBorraLaCookie() {
        when(tokenRepository.findByTokenHash(sha256(RAW_TOKEN))).thenReturn(Optional.empty());
        MockHttpServletResponse response = new MockHttpServletResponse();

        Optional<User> user = service.autoLogin(requestWithCookie(RAW_TOKEN), response);

        assertThat(user).isEmpty();
        assertThat(rememberSetCookie(response)).contains("Max-Age=0");
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void autoLoginConTokenCaducadoLoBorraYNoAbreSesion() {
        RememberMeToken stored = storedToken(client.getSessionVersion(), Instant.now().minus(1, ChronoUnit.MINUTES));
        MockHttpServletResponse response = new MockHttpServletResponse();

        Optional<User> user = service.autoLogin(requestWithCookie(RAW_TOKEN), response);

        assertThat(user).isEmpty();
        verify(tokenRepository).delete(stored);
        assertThat(rememberSetCookie(response)).contains("Max-Age=0");
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void autoLoginTrasRevocarLasSesionesNoAbreSesion() {
        RememberMeToken stored = storedToken(client.getSessionVersion(), Instant.now().plus(10, ChronoUnit.DAYS));
        // Bloqueo, cambio de contraseña...: cierra todas las sesiones, también las recordadas
        client.revokeSessions();
        MockHttpServletResponse response = new MockHttpServletResponse();

        Optional<User> user = service.autoLogin(requestWithCookie(RAW_TOKEN), response);

        assertThat(user).isEmpty();
        verify(tokenRepository).delete(stored);
        assertThat(rememberSetCookie(response)).contains("Max-Age=0");
    }

    @Test
    void autoLoginDeUnUsuarioBloqueadoNoAbreSesion() {
        RememberMeToken stored = storedToken(client.getSessionVersion(), Instant.now().plus(10, ChronoUnit.DAYS));
        client.setBlocked(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        Optional<User> user = service.autoLogin(requestWithCookie(RAW_TOKEN), response);

        assertThat(user).isEmpty();
        verify(tokenRepository).delete(stored);
    }

    @Test
    void autoLoginDeUnUsuarioDesactivadoNoAbreSesion() {
        RememberMeToken stored = storedToken(client.getSessionVersion(), Instant.now().plus(10, ChronoUnit.DAYS));
        client.setActive(false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        Optional<User> user = service.autoLogin(requestWithCookie(RAW_TOKEN), response);

        assertThat(user).isEmpty();
        verify(tokenRepository).delete(stored);
    }

    @Test
    void forgetBorraElTokenYLaCookie() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.forget(requestWithCookie(RAW_TOKEN), response);

        verify(tokenRepository).deleteByTokenHash(sha256(RAW_TOKEN));
        assertThat(rememberSetCookie(response)).contains("Max-Age=0");
    }

    private RememberMeToken storedToken(int sessionVersion, Instant expiresAt) {
        RememberMeToken token = new RememberMeToken(CLIENT_ID, sha256(RAW_TOKEN), sessionVersion, expiresAt);
        when(tokenRepository.findByTokenHash(sha256(RAW_TOKEN))).thenReturn(Optional.of(token));
        return token;
    }

    private RememberMeToken savedToken() {
        ArgumentCaptor<RememberMeToken> captor = ArgumentCaptor.forClass(RememberMeToken.class);
        verify(tokenRepository).save(captor.capture());
        return captor.getValue();
    }

    private static MockHttpServletRequest requestWithCookie(String value) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(RememberMeService.COOKIE_NAME, value));
        return request;
    }

    private static String rememberSetCookie(MockHttpServletResponse response) {
        return response.getHeaders("Set-Cookie").stream()
                .filter(header -> header.startsWith(RememberMeService.COOKIE_NAME + "="))
                .findFirst()
                .orElse("");
    }

    private static String rememberCookieValue(MockHttpServletResponse response) {
        String header = rememberSetCookie(response);
        assertThat(header).as("cabecera Set-Cookie de %s", RememberMeService.COOKIE_NAME).isNotEmpty();
        return header.substring(RememberMeService.COOKIE_NAME.length() + 1, header.indexOf(';'));
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
