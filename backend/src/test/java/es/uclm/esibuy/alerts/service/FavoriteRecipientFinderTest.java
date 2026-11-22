package es.uclm.esibuy.alerts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import es.uclm.esibuy.users.model.ClientProfile;
import es.uclm.esibuy.users.model.CustomerType;
import es.uclm.esibuy.users.model.User;
import es.uclm.esibuy.users.model.UserRole;
import es.uclm.esibuy.users.repository.UserRepository;
import es.uclm.esibuy.wishlist.model.Wishlist;
import es.uclm.esibuy.wishlist.repository.WishlistRepository;

@ExtendWith(MockitoExtension.class)
class FavoriteRecipientFinderTest {

    private static final String PRODUCT_ID = "p1";

    @Mock
    private WishlistRepository wishlistRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private FavoriteRecipientFinder finder;

    private Wishlist wishlistOf(String userId) {
        return new Wishlist(userId, "Lista de " + userId);
    }

    private User customer(String id, CustomerType type) {
        User user = new User(id + "@example.com", "Ana", "López", UserRole.CLIENT);
        ReflectionTestUtils.setField(user, "id", id);
        user.setClientProfile(type == null ? null : new ClientProfile(null, type));
        return user;
    }

    @Test
    @DisplayName("HU27-CP11: devuelve a los clientes con el producto en favoritos y si son Premium")
    void returnsTheCustomersWithTheProductAndWhetherTheyArePremium() {
        when(wishlistRepository.findAllByProductIdsContaining(PRODUCT_ID))
                .thenReturn(List.of(wishlistOf("u1"), wishlistOf("u2")));
        when(userRepository.findAllById(Set.of("u1", "u2")))
                .thenReturn(List.of(customer("u1", CustomerType.STANDARD), customer("u2", CustomerType.PREMIUM)));

        List<FavoriteRecipient> recipients = finder.find(PRODUCT_ID, false);

        assertThat(recipients).containsExactly(
                new FavoriteRecipient("u1", false),
                new FavoriteRecipient("u2", true));
    }

    @Test
    @DisplayName("HU27-CP12: un cliente con el producto en dos listas recibe una sola alerta")
    void aCustomerWithTheProductInTwoListsAppearsOnce() {
        when(wishlistRepository.findAllByProductIdsContaining(PRODUCT_ID))
                .thenReturn(List.of(wishlistOf("u1"), wishlistOf("u1")));
        when(userRepository.findAllById(Set.of("u1")))
                .thenReturn(List.of(customer("u1", CustomerType.STANDARD)));

        assertThat(finder.find(PRODUCT_ID, false)).containsExactly(new FavoriteRecipient("u1", false));
    }

    @Test
    @DisplayName("HU27-CP13: un producto exclusivo Premium solo avisa a los clientes Premium")
    void aPremiumOnlyProductOnlyNotifiesPremiumCustomers() {
        when(wishlistRepository.findAllByProductIdsContaining(PRODUCT_ID))
                .thenReturn(List.of(wishlistOf("u1"), wishlistOf("u2")));
        when(userRepository.findAllById(Set.of("u1", "u2")))
                .thenReturn(List.of(customer("u1", CustomerType.STANDARD), customer("u2", CustomerType.PREMIUM)));

        assertThat(finder.find(PRODUCT_ID, true)).containsExactly(new FavoriteRecipient("u2", true));
    }

    @Test
    @DisplayName("HU27-CP14: un usuario que ya no existe se salta")
    void aUserThatNoLongerExistsIsSkipped() {
        when(wishlistRepository.findAllByProductIdsContaining(PRODUCT_ID))
                .thenReturn(List.of(wishlistOf("u1"), wishlistOf("borrado")));
        when(userRepository.findAllById(Set.of("u1", "borrado")))
                .thenReturn(List.of(customer("u1", CustomerType.STANDARD)));

        assertThat(finder.find(PRODUCT_ID, false)).containsExactly(new FavoriteRecipient("u1", false));
    }

    @Test
    @DisplayName("HU27-CP15: un cliente sin perfil no es Premium y no recibe avisos de exclusivos")
    void aCustomerWithoutProfileIsNotPremium() {
        when(wishlistRepository.findAllByProductIdsContaining(PRODUCT_ID))
                .thenReturn(List.of(wishlistOf("u1")));
        when(userRepository.findAllById(Set.of("u1"))).thenReturn(List.of(customer("u1", null)));

        assertThat(finder.find(PRODUCT_ID, false)).containsExactly(new FavoriteRecipient("u1", false));
        assertThat(finder.find(PRODUCT_ID, true)).isEmpty();
    }

    @Test
    @DisplayName("HU27-CP16: si nadie tiene el producto en favoritos no se consultan los usuarios")
    void doesNotQueryUsersWhenNobodyHasTheProduct() {
        when(wishlistRepository.findAllByProductIdsContaining(PRODUCT_ID)).thenReturn(List.of());

        assertThat(finder.find(PRODUCT_ID, false)).isEmpty();

        verify(wishlistRepository).findAllByProductIdsContaining(PRODUCT_ID);
        verifyNoInteractions(userRepository);
    }
}
