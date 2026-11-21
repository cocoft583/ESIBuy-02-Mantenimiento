package es.uclm.esibuy.users.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

import es.uclm.esibuy.categories.repository.CategoryRepository;
import es.uclm.esibuy.common.exception.CategoryIdNotFoundException;
import es.uclm.esibuy.common.exception.UserNotFoundException;
import es.uclm.esibuy.users.model.ClientProfile;
import es.uclm.esibuy.users.model.User;
import es.uclm.esibuy.users.repository.UserRepository;

@Service
public class FavoriteCategoryService {

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;

    public FavoriteCategoryService(UserRepository userRepository, CategoryRepository categoryRepository) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
    }

    public List<String> list(String userId) {
        ClientProfile profile = findUser(userId).getClientProfile();

        return profile == null ? List.of() : List.copyOf(profile.getFavoriteCategoryIds());
    }

    /**
     * Sustituye las categorías favoritas del cliente por las indicadas.
     * Se ignoran los repetidos y los vacíos; todas las demás deben existir.
     */
    public List<String> replace(String userId, List<String> categoryIds) {
        User user = findUser(userId);

        List<String> favorites = List.copyOf(new LinkedHashSet<>(categoryIds.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .toList()));

        if (categoryRepository.findAllById(favorites).size() != favorites.size()) {
            throw new CategoryIdNotFoundException();
        }

        ClientProfile profile = user.getClientProfile();
        if (profile == null) {
            profile = new ClientProfile();
            user.setClientProfile(profile);
        }

        profile.setFavoriteCategoryIds(favorites);
        userRepository.save(user);

        return favorites;
    }

    private User findUser(String userId) {
        return userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    }
}
