package es.uclm.esibuy.alerts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import es.uclm.esibuy.alerts.model.Alert;
import es.uclm.esibuy.alerts.model.AlertType;
import es.uclm.esibuy.alerts.repository.AlertRepository;
import es.uclm.esibuy.products.event.ProductCreatedEvent;
import es.uclm.esibuy.products.event.ProductSnapshot;
import es.uclm.esibuy.users.model.ClientProfile;
import es.uclm.esibuy.users.model.CustomerType;
import es.uclm.esibuy.users.model.User;
import es.uclm.esibuy.users.repository.UserRepository;

/**
 * HU28 (T28.2): cuando se da de alta un producto disponible, se avisa a los clientes que tienen
 * alguna de sus categorías entre las favoritas, una sola alerta por cliente. Un exclusivo Premium
 * solo avisa a los clientes Premium.
 */
@ExtendWith(MockitoExtension.class)
class NewProductAlertListenerTest {

    private static final String PRODUCT_ID = "p1";
    private static final String PRODUCT_NAME = "Auriculares";
    private static final String AUDIO = "audio";
    private static final String GAMING = "gaming";

    @Mock
    private UserRepository userRepository;

    @Mock
    private AlertRepository alertRepository;

    private NewProductAlertListener listener;

    @BeforeEach
    void setUp() {
        listener = new NewProductAlertListener(userRepository, alertRepository);
    }

    private ProductCreatedEvent created(boolean available, boolean premiumOnly, List<String> categories) {
        ProductSnapshot product = new ProductSnapshot(
                PRODUCT_ID, PRODUCT_NAME, new BigDecimal("19.99"), null, null, available, 5, premiumOnly);
        return new ProductCreatedEvent(product, categories);
    }

    private User client(String id, CustomerType type) {
        User user = mock(User.class);
        lenient().when(user.getId()).thenReturn(id);
        lenient().when(user.getClientProfile()).thenReturn(new ClientProfile(null, type));
        return user;
    }

    @SuppressWarnings("unchecked")
    private List<Alert> savedAlerts() {
        ArgumentCaptor<List<Alert>> captor = ArgumentCaptor.forClass(List.class);
        verify(alertRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("HU28: un producto nuevo disponible avisa a cada cliente con alguna de sus categorías en favoritas")
    void newAvailableProductAlertsClientsWithAFavoriteCategory() {
        List<User> clients = List.of(client("u1", CustomerType.STANDARD), client("u2", CustomerType.PREMIUM));
        when(userRepository.findAllByClientProfileFavoriteCategoryIdsIn(List.of(AUDIO))).thenReturn(clients);

        listener.onProductCreated(created(true, false, List.of(AUDIO)));

        List<Alert> alerts = savedAlerts();
        assertThat(alerts).extracting(Alert::getCustomerId).containsExactly("u1", "u2");
        assertThat(alerts).allSatisfy(alert -> {
            assertThat(alert.getType()).isEqualTo(AlertType.NEW_PRODUCT);
            assertThat(alert.getProductId()).isEqualTo(PRODUCT_ID);
            assertThat(alert.getProductName()).isEqualTo(PRODUCT_NAME);
            assertThat(alert.getMessage()).contains(PRODUCT_NAME);
        });
    }

    @Test
    @DisplayName("HU28: un producto dado de alta no disponible no avisa a nadie (RN-18)")
    void unavailableNewProductAlertsNobody() {
        listener.onProductCreated(created(false, false, List.of(AUDIO)));

        verifyNoInteractions(userRepository, alertRepository);
    }

    @Test
    @DisplayName("HU28: si el producto tiene varias de mis categorías favoritas, recibo una sola alerta")
    void severalFavoriteCategoriesGiveASingleAlert() {
        User both = client("u1", CustomerType.STANDARD);
        // Se busca una sola vez con todas las categorías; aunque llegara repetido, se avisa una vez
        when(userRepository.findAllByClientProfileFavoriteCategoryIdsIn(List.of(AUDIO, GAMING)))
                .thenReturn(List.of(both, both));

        listener.onProductCreated(created(true, false, List.of(AUDIO, GAMING)));

        assertThat(savedAlerts()).extracting(Alert::getCustomerId).containsExactly("u1");
    }

    @Test
    @DisplayName("HU28: un producto exclusivo Premium solo avisa a los clientes Premium")
    void premiumOnlyNewProductAlertsOnlyPremiumClients() {
        List<User> clients = List.of(client("estandar", CustomerType.STANDARD), client("premium", CustomerType.PREMIUM));
        when(userRepository.findAllByClientProfileFavoriteCategoryIdsIn(List.of(AUDIO))).thenReturn(clients);

        listener.onProductCreated(created(true, true, List.of(AUDIO)));

        assertThat(savedAlerts()).extracting(Alert::getCustomerId).containsExactly("premium");
    }

    @Test
    @DisplayName("HU28: un usuario sin perfil de cliente no recibe la alerta de un exclusivo")
    void userWithoutClientProfileIsNotPremium() {
        User noProfile = mock(User.class);
        lenient().when(noProfile.getId()).thenReturn("sin-perfil");
        when(userRepository.findAllByClientProfileFavoriteCategoryIdsIn(List.of(AUDIO)))
                .thenReturn(List.of(noProfile));

        listener.onProductCreated(created(true, true, List.of(AUDIO)));

        verify(alertRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("HU28: si nadie tiene esas categorías en favoritas, no se guarda nada")
    void nobodyWithThoseFavoriteCategoriesSavesNothing() {
        when(userRepository.findAllByClientProfileFavoriteCategoryIdsIn(List.of(AUDIO))).thenReturn(List.of());

        listener.onProductCreated(created(true, false, List.of(AUDIO)));

        verify(alertRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("HU28: un producto sin categorías no avisa a nadie")
    void productWithoutCategoriesAlertsNobody() {
        listener.onProductCreated(created(true, false, List.of()));

        verifyNoInteractions(userRepository, alertRepository);
    }

    @Test
    @DisplayName("HU28: un fallo al generar las alertas no rompe el alta del producto (RNF-03)")
    void aFailureGeneratingAlertsDoesNotBreakTheProductCreation() {
        User u1 = client("u1", CustomerType.STANDARD);
        when(userRepository.findAllByClientProfileFavoriteCategoryIdsIn(List.of(AUDIO))).thenReturn(List.of(u1));
        doThrow(new IllegalStateException("Mongo caído")).when(alertRepository).saveAll(any());

        assertThatCode(() -> listener.onProductCreated(created(true, false, List.of(AUDIO))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("HU28: el listener recibe el evento que publica Spring al dar de alta un producto")
    void springDeliversTheProductCreatedEventToTheListener() {
        UserRepository users = mock(UserRepository.class);
        AlertRepository alerts = mock(AlertRepository.class);
        User u1 = client("u1", CustomerType.STANDARD);
        when(users.findAllByClientProfileFavoriteCategoryIdsIn(List.of(AUDIO))).thenReturn(List.of(u1));

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(UserRepository.class, () -> users);
            context.registerBean(AlertRepository.class, () -> alerts);
            context.registerBean(NewProductAlertListener.class);
            context.refresh();

            context.publishEvent(created(true, false, List.of(AUDIO)));
        }

        verify(alerts).saveAll(any());
    }
}
