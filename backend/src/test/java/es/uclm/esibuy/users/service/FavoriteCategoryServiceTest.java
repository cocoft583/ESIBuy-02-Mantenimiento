package es.uclm.esibuy.users.service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import es.uclm.esibuy.categories.model.Category;
import es.uclm.esibuy.categories.repository.CategoryRepository;
import es.uclm.esibuy.common.exception.CategoryIdNotFoundException;
import es.uclm.esibuy.common.exception.UserNotFoundException;
import es.uclm.esibuy.users.model.ClientProfile;
import es.uclm.esibuy.users.model.CustomerType;
import es.uclm.esibuy.users.model.User;
import es.uclm.esibuy.users.model.UserRole;
import es.uclm.esibuy.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FavoriteCategoryServiceTest {

    private static final String USER_ID = "cliente-1";

    @Mock
    private UserRepository userRepository;

    @Mock
    private CategoryRepository categoryRepository;

    private FavoriteCategoryService service;
    private User customer;

    @BeforeEach
    void setUp() {
        service = new FavoriteCategoryService(userRepository, categoryRepository);

        customer = new User("cliente@example.com", "Ana", "López", UserRole.CLIENT);
        customer.setClientProfile(new ClientProfile(null, CustomerType.STANDARD));
    }

    private void givenTheCustomerExists() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(customer));
    }

    private void givenTheCategoriesExist(String... ids) {
        when(categoryRepository.findAllById(any())).thenReturn(
                Arrays.stream(ids).map(id -> new Category(id, "Categoría " + id)).toList());
    }

    @Test
    @DisplayName("HU26-CP04: un cliente sin favoritas recibe una lista vacía")
    void shouldReturnAnEmptyListWhenTheCustomerHasNoFavorites() {
        givenTheCustomerExists();

        assertTrue(service.list(USER_ID).isEmpty());
    }

    @Test
    @DisplayName("HU26-CP05: se devuelven las categorías favoritas guardadas, en su orden")
    void shouldListTheSavedFavoritesInOrder() {
        customer.getClientProfile().setFavoriteCategoryIds(List.of("moda", "hogar"));
        givenTheCustomerExists();

        assertEquals(List.of("moda", "hogar"), service.list(USER_ID));
    }

    @Test
    @DisplayName("HU26-CP06: un usuario sin perfil de cliente o con la lista a nulo no tiene favoritas")
    void shouldTreatAMissingProfileOrListAsNoFavorites() {
        customer.setClientProfile(null);
        givenTheCustomerExists();
        assertTrue(service.list(USER_ID).isEmpty());

        ClientProfile profileWithoutList = new ClientProfile();
        profileWithoutList.setFavoriteCategoryIds(null);
        customer.setClientProfile(profileWithoutList);
        assertTrue(service.list(USER_ID).isEmpty());
    }

    @Test
    @DisplayName("HU26-CP07: elegir categorías las guarda en el perfil del cliente")
    void shouldSaveTheChosenCategoriesInTheCustomerProfile() {
        givenTheCustomerExists();
        givenTheCategoriesExist("moda", "hogar");

        List<String> result = service.replace(USER_ID, List.of("moda", "hogar"));

        assertEquals(List.of("moda", "hogar"), result);
        assertEquals(List.of("moda", "hogar"), customer.getClientProfile().getFavoriteCategoryIds());
        verify(userRepository).save(customer);
    }

    @Test
    @DisplayName("HU26-CP08: elegir de nuevo sustituye a las anteriores, no se suman")
    void shouldReplaceThePreviousFavoritesInsteadOfAddingToThem() {
        customer.getClientProfile().setFavoriteCategoryIds(List.of("moda", "hogar"));
        givenTheCustomerExists();
        givenTheCategoriesExist("deporte");

        service.replace(USER_ID, List.of("deporte"));

        assertEquals(List.of("deporte"), customer.getClientProfile().getFavoriteCategoryIds());
    }

    @Test
    @DisplayName("HU26-CP09: quitar categorías con una lista vacía deja al cliente sin favoritas")
    void shouldRemoveAllFavoritesWhenTheListIsEmpty() {
        customer.getClientProfile().setFavoriteCategoryIds(List.of("moda", "hogar"));
        givenTheCustomerExists();

        List<String> result = service.replace(USER_ID, List.of());

        assertTrue(result.isEmpty());
        assertTrue(customer.getClientProfile().getFavoriteCategoryIds().isEmpty());
        verify(userRepository).save(customer);
    }

    @Test
    @DisplayName("HU26-CP10: se ignoran los repetidos, los vacíos y los nulos, y se recortan los espacios")
    void shouldIgnoreDuplicatesBlanksAndNullsAndTrimSpaces() {
        givenTheCustomerExists();
        givenTheCategoriesExist("moda", "hogar");

        List<String> result = service.replace(USER_ID, Arrays.asList("moda", " moda ", "", "   ", null, "hogar", "moda"));

        assertEquals(List.of("moda", "hogar"), result);
        assertEquals(List.of("moda", "hogar"), customer.getClientProfile().getFavoriteCategoryIds());
    }

    @Test
    @DisplayName("HU26-CP11: una categoría que no existe se rechaza y no se guarda nada")
    void shouldRejectACategoryThatDoesNotExistAndSaveNothing() {
        customer.getClientProfile().setFavoriteCategoryIds(List.of("moda"));
        givenTheCustomerExists();
        givenTheCategoriesExist("hogar");

        assertThrows(CategoryIdNotFoundException.class,
                () -> service.replace(USER_ID, List.of("hogar", "borrada")));

        assertEquals(List.of("moda"), customer.getClientProfile().getFavoriteCategoryIds());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("HU26-CP12: si el usuario no tenía perfil de cliente, se crea al elegir favoritas")
    void shouldCreateTheClientProfileWhenTheUserHasNone() {
        customer.setClientProfile(null);
        givenTheCustomerExists();
        givenTheCategoriesExist("moda");

        service.replace(USER_ID, List.of("moda"));

        assertNotNull(customer.getClientProfile());
        assertEquals(List.of("moda"), customer.getClientProfile().getFavoriteCategoryIds());
        verify(userRepository).save(customer);
    }

    @Test
    @DisplayName("HU26-CP13: un usuario que no existe no puede consultar ni cambiar favoritas")
    void shouldRejectAnUnknownUser() {
        when(userRepository.findById("fantasma")).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> service.list("fantasma"));
        assertThrows(UserNotFoundException.class, () -> service.replace("fantasma", List.of("moda")));

        verify(userRepository, never()).save(any(User.class));
    }
}
