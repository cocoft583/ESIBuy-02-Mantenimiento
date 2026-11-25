package es.uclm.esibuy.security.filter;

import java.io.IOException;

import org.springframework.web.filter.OncePerRequestFilter;

import es.uclm.esibuy.security.service.RememberMeService;
import es.uclm.esibuy.security.service.SessionAuthenticationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Si la petición no trae sesión pero sí la cookie de «recordar sesión» de la app, vuelve a
 * abrir la sesión del usuario (HU30, T30.10). Va antes de SessionRevocationFilter.
 *
 * Ciclo rojo: de momento solo deja pasar la petición.
 */
public class RememberMeFilter extends OncePerRequestFilter {

    private final RememberMeService rememberMeService;
    private final SessionAuthenticationService sessionAuthenticationService;

    public RememberMeFilter(
            RememberMeService rememberMeService,
            SessionAuthenticationService sessionAuthenticationService) {
        this.rememberMeService = rememberMeService;
        this.sessionAuthenticationService = sessionAuthenticationService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        filterChain.doFilter(request, response);
    }
}
