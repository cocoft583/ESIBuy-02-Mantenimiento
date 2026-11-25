package es.uclm.esibuy.security.filter;

import java.io.IOException;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

import es.uclm.esibuy.security.service.RememberMeService;
import es.uclm.esibuy.security.service.SessionAuthenticationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Si la petición no trae sesión pero sí la cookie de «recordar sesión» de la app, vuelve a
 * abrir la sesión del usuario (HU30, T30.10). Va antes de SessionRevocationFilter, que después
 * comprueba como siempre que la cuenta siga activa y con la misma versión de sesión.
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
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        boolean anonymous = current == null || current instanceof AnonymousAuthenticationToken;

        // Con sesión abierta, o sin la cookie (la web), no se consulta nada
        if (anonymous && WebUtils.getCookie(request, RememberMeService.COOKIE_NAME) != null) {
            rememberMeService.autoLogin(request, response)
                    .ifPresent(user -> sessionAuthenticationService.authenticate(user, request, response));
        }

        filterChain.doFilter(request, response);
    }
}
