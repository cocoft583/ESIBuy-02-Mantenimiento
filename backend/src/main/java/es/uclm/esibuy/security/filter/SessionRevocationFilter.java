package es.uclm.esibuy.security.filter;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import es.uclm.esibuy.common.filter.CorrelationIdFilter;
import es.uclm.esibuy.security.AuthenticatedUserPrincipal;
import es.uclm.esibuy.users.model.User;
import es.uclm.esibuy.users.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Recarga el usuario en cada petición autenticada y cierra la sesión si, desde que
 * inició sesión, la cuenta se ha bloqueado, desactivado, eliminado o cambiado de rol.
 */
public class SessionRevocationFilter extends OncePerRequestFilter {

    public static final String CODE = "SESSION_REVOKED";
    private static final String MESSAGE = "Tu sesión se ha cerrado. Vuelve a iniciar sesión";

    private final UserRepository userRepository;

    public SessionRevocationFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null
                && authentication.getPrincipal() instanceof AuthenticatedUserPrincipal principal
                && isRevoked(principal)) {
            revoke(request, response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isRevoked(AuthenticatedUserPrincipal principal) {
        return userRepository.findById(principal.id())
                .map(user -> !isStillValid(user, principal))
                .orElse(true);
    }

    private boolean isStillValid(User user, AuthenticatedUserPrincipal principal) {
        return user.isActive()
                && !user.isBlocked()
                && user.getRole() == principal.role()
                && user.getSessionVersion() == principal.sessionVersion();
    }

    private void revoke(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();

        Object correlationId = request.getAttribute(CorrelationIdFilter.ATTRIBUTE);
        String id = correlationId == null ? "" : correlationId.toString();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader(CorrelationIdFilter.HEADER, id);
        response.getWriter().write("{\"success\":false,\"code\":\"" + CODE
                + "\",\"message\":\"" + MESSAGE
                + "\",\"correlationId\":\"" + id + "\"}");
    }
}
