package es.uclm.esibuy.alerts.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import es.uclm.esibuy.users.model.ClientProfile;
import es.uclm.esibuy.users.model.CustomerType;
import es.uclm.esibuy.users.model.User;
import es.uclm.esibuy.users.repository.UserRepository;
import es.uclm.esibuy.wishlist.model.Wishlist;
import es.uclm.esibuy.wishlist.repository.WishlistRepository;

/** Encuentra a quién avisar cuando cambia un producto: los clientes que lo tienen en favoritos. */
@Service
public class FavoriteRecipientFinder {

    private final WishlistRepository wishlistRepository;
    private final UserRepository userRepository;

    public FavoriteRecipientFinder(WishlistRepository wishlistRepository, UserRepository userRepository) {
        this.wishlistRepository = wishlistRepository;
        this.userRepository = userRepository;
    }

    /**
     * Clientes con el producto en alguna de sus listas, uno solo por cliente aunque lo tenga en
     * varias. Los usuarios que ya no existen se saltan. Con {@code premiumOnly} solo salen los
     * clientes Premium: un producto exclusivo no avisa a los demás.
     */
    public List<FavoriteRecipient> find(String productId, boolean premiumOnly) {
        Set<String> customerIds = new LinkedHashSet<>();
        for (Wishlist wishlist : wishlistRepository.findAllByProductIdsContaining(productId)) {
            customerIds.add(wishlist.getUserId());
        }
        if (customerIds.isEmpty()) {
            return List.of();
        }

        return userRepository.findAllById(customerIds).stream()
                .map(user -> new FavoriteRecipient(user.getId(), isPremium(user)))
                .filter(recipient -> !premiumOnly || recipient.premium())
                .toList();
    }

    private boolean isPremium(User user) {
        ClientProfile profile = user.getClientProfile();

        return profile != null && CustomerType.PREMIUM.equals(profile.getCustomerType());
    }
}
