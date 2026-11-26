package es.uclm.esibuy.security.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.web.util.WebUtils;

import es.uclm.esibuy.security.model.RememberMeToken;
import es.uclm.esibuy.security.repository.RememberMeTokenRepository;
import es.uclm.esibuy.users.model.User;
import es.uclm.esibuy.users.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * «Recordar sesión» de la app (HU30, T30.10): con la cookie ESIBUY_REMEMBER se vuelve a abrir
 * la sesión aunque el móvil haya borrado JSESSIONID o el servidor se haya reiniciado.
 * El token solo vive en la cookie; en Mongo se guarda su hash. Cada uso lo cambia por otro.
 */
@Service
public class RememberMeService {

    public static final String COOKIE_NAME = "ESIBUY_REMEMBER";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;
    private static final String COOKIE_PATH = "/api";

    private final RememberMeTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final Duration validity;
    private final boolean secureCookie;
    private final String sameSite;

    public RememberMeService(
            RememberMeTokenRepository tokenRepository,
            UserRepository userRepository,
            @Value("${esibuy.security.remember-me.validity:30d}") Duration validity,
            @Value("${server.servlet.session.cookie.secure}") boolean secureCookie,
            @Value("${server.servlet.session.cookie.same-site}") String sameSite) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.validity = validity;
        this.secureCookie = secureCookie;
        this.sameSite = sameSite;
    }

    /** Genera un token nuevo para el usuario, guarda su hash y lo manda en la cookie. */
    public void issue(User user, HttpServletResponse response) {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokenRepository.save(new RememberMeToken(
                user.getId(), hash(token), user.getSessionVersion(), Instant.now().plus(validity)));
        addCookie(response, token, validity);
    }

    /** Si la cookie trae un token válido, lo rota y devuelve su usuario; si no, lo borra. */
    public Optional<User> autoLogin(HttpServletRequest request, HttpServletResponse response) {
        Optional<String> token = readCookie(request);
        if (token.isEmpty()) {
            return Optional.empty();
        }
        Optional<RememberMeToken> stored = tokenRepository.findByTokenHash(hash(token.get()));
        if (stored.isEmpty()) {
            clearCookie(response);
            return Optional.empty();
        }
        RememberMeToken current = stored.get();
        // Un token solo sirve una vez: si vale se cambia por otro, y si no, se borra
        tokenRepository.delete(current);
        Optional<User> user = userRepository.findById(current.getUserId())
                .filter(candidate -> isStillValid(candidate, current));
        if (user.isEmpty()) {
            clearCookie(response);
            return Optional.empty();
        }
        issue(user.get(), response);
        return user;
    }

    /** Borra el token de la cookie y la cookie (al cerrar sesión). */
    public void forget(HttpServletRequest request, HttpServletResponse response) {
        readCookie(request).ifPresent(token -> tokenRepository.deleteByTokenHash(hash(token)));
        clearCookie(response);
    }

    private static boolean isStillValid(User user, RememberMeToken token) {
        return token.getExpiresAt().isAfter(Instant.now())
                && user.isActive()
                && !user.isBlocked()
                && user.getSessionVersion() == token.getSessionVersion();
    }

    private static Optional<String> readCookie(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, COOKIE_NAME);
        return cookie == null || cookie.getValue() == null || cookie.getValue().isBlank()
                ? Optional.empty()
                : Optional.of(cookie.getValue());
    }

    private void addCookie(HttpServletResponse response, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite(sameSite)
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearCookie(HttpServletResponse response) {
        addCookie(response, "", Duration.ZERO);
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no está disponible", exception);
        }
    }
}
