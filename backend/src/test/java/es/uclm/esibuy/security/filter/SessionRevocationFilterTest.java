package es.uclm.esibuy.security.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import es.uclm.esibuy.security.AuthenticatedUserPrincipal;
import es.uclm.esibuy.users.model.User;
import es.uclm.esibuy.users.model.UserRole;
import es.uclm.esibuy.users.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class SessionRevocationFilterTest {

    private static final String USER_ID = "user-1";
    private static final String EMAIL = "cliente@esibuy.com";

    @Mock
    private UserRepository userRepository;

    private SessionRevocationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain chain;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        filter = new SessionRevocationFilter(userRepository);
        session = new MockHttpSession();
        request = new MockHttpServletRequest("GET", "/api/cart");
        request.setSession(session);
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void letsAnonymousRequestsThrough() throws Exception {
        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
        verifyNoInteractions(userRepository);
    }

    @Test
    void letsUpToDateSessionsThrough() throws Exception {
        authenticateAs(UserRole.CLIENT);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(UserRole.CLIENT)));

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
        assertEquals(200, response.getStatus());
    }

    @Test
    void revokesSessionOfBlockedUser() throws Exception {
        authenticateAs(UserRole.CLIENT);
        User user = user(UserRole.CLIENT);
        user.setBlocked(true);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        filter.doFilter(request, response, chain);

        assertRevoked();
    }

    @Test
    void revokesSessionOfInactiveUser() throws Exception {
        authenticateAs(UserRole.CLIENT);
        User user = user(UserRole.CLIENT);
        user.setActive(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        filter.doFilter(request, response, chain);

        assertRevoked();
    }

    @Test
    void revokesSessionOfDeletedUser() throws Exception {
        authenticateAs(UserRole.CLIENT);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        filter.doFilter(request, response, chain);

        assertRevoked();
    }

    @Test
    void revokesSessionWhenRoleChanged() throws Exception {
        authenticateAs(UserRole.CLIENT);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(UserRole.SELLER)));

        filter.doFilter(request, response, chain);

        assertRevoked();
    }

    @Test
    void revokesSessionWhenSessionVersionChanged() throws Exception {
        authenticateAs(UserRole.CLIENT);
        User user = user(UserRole.CLIENT);
        user.revokeSessions();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        filter.doFilter(request, response, chain);

        assertRevoked();
    }

    private void assertRevoked() throws Exception {
        assertNull(chain.getRequest());
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":\"SESSION_REVOKED\""));
        assertTrue(session.isInvalid());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private void authenticateAs(UserRole role) {
        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(USER_ID, EMAIL, role, true, 0);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    private User user(UserRole role) {
        User user = new User(EMAIL, "Ana", "López", role);
        user.setActive(true);
        return user;
    }
}
