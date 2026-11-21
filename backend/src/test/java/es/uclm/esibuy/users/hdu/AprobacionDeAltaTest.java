package es.uclm.esibuy.users.hdu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import es.uclm.esibuy.auth.passwordreset.repository.PasswordResetTokenRepository;
import es.uclm.esibuy.cart.repository.CartRepository;
import es.uclm.esibuy.common.exception.AccountPendingException;
import es.uclm.esibuy.configuration.service.GeneralConfigurationService;
import es.uclm.esibuy.orders.repository.OrderRepository;
import es.uclm.esibuy.products.repository.ProductRepository;
import es.uclm.esibuy.security.password.CompromisedPasswordService;
import es.uclm.esibuy.security.repository.EmailOtpChallengeRepository;
import es.uclm.esibuy.security.repository.LoginAttemptRepository;
import es.uclm.esibuy.users.dto.LoginRequest;
import es.uclm.esibuy.users.model.User;
import es.uclm.esibuy.users.model.UserRole;
import es.uclm.esibuy.users.repository.UserRepository;
import es.uclm.esibuy.users.service.AdminService;
import es.uclm.esibuy.users.service.UserService;
import es.uclm.esibuy.wishlist.repository.WishlistRepository;

/** Recorrido completo: una cuenta recién registrada espera la aprobación y, aprobada, entra con normalidad. */
@ExtendWith(MockitoExtension.class)
class AprobacionDeAltaTest {

    private static final String EMAIL = "cliente@test.com";
    private static final String USER_ID = "usuario-1";

    @Mock
    private UserRepository userRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private CartRepository cartRepository;
    @Mock
    private WishlistRepository wishlistRepository;
    @Mock
    private EmailOtpChallengeRepository emailOtpChallengeRepository;
    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock
    private LoginAttemptRepository loginAttemptRepository;
    @Mock
    private CompromisedPasswordService compromisedPasswordService;
    @Mock
    private GeneralConfigurationService generalConfigurationService;

    private UserService userService;
    private AdminService adminService;
    private User pendingUser;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder, productRepository,
                orderRepository, cartRepository, wishlistRepository,
                emailOtpChallengeRepository, passwordResetTokenRepository, loginAttemptRepository,
                compromisedPasswordService, generalConfigurationService);
        adminService = new AdminService(userRepository, passwordEncoder, compromisedPasswordService);

        pendingUser = new User(EMAIL, "Ana", "López", UserRole.CLIENT);
        pendingUser.setActive(false);
        pendingUser.setBlocked(false);
        pendingUser.setPasswordHash("hash");

        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(pendingUser));
        when(passwordEncoder.matches("Password123!", "hash")).thenReturn(true);
    }

    @Test
    @DisplayName("HU17-CP03: una cuenta pendiente que se aprueba entra con normalidad")
    void anApprovedAccountCanLogInAfterWaiting() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(pendingUser));
        LoginRequest loginRequest = request();

        assertThrows(AccountPendingException.class, () -> userService.login(loginRequest));
        assertFalse(pendingUser.isActive());

        adminService.activateUser(USER_ID);

        assertTrue(pendingUser.isActive());
        assertEquals(pendingUser, userService.login(loginRequest));
    }

    private LoginRequest request() {
        LoginRequest request = new LoginRequest();
        request.setEmail(EMAIL);
        request.setPassword("Password123!");
        return request;
    }
}
