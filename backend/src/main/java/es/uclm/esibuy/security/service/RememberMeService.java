package es.uclm.esibuy.security.service;

import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import es.uclm.esibuy.security.repository.RememberMeTokenRepository;
import es.uclm.esibuy.users.model.User;
import es.uclm.esibuy.users.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * «Recordar sesión» de la app (HU30, T30.10): con la cookie ESIBUY_REMEMBER se vuelve a abrir
 * la sesión aunque el móvil haya borrado JSESSIONID o el servidor se haya reiniciado.
 *
 * Ciclo rojo: solo la firma. El verde da el token, lo comprueba, lo rota y lo olvida.
 */
@Service
public class RememberMeService {

    public static final String COOKIE_NAME = "ESIBUY_REMEMBER";

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
        // Ciclo rojo
    }

    /** Si la cookie trae un token válido, lo rota y devuelve su usuario; si no, lo borra. */
    public Optional<User> autoLogin(HttpServletRequest request, HttpServletResponse response) {
        return Optional.empty();
    }

    /** Borra el token de la cookie y la cookie (al cerrar sesión). */
    public void forget(HttpServletRequest request, HttpServletResponse response) {
        // Ciclo rojo
    }
}
