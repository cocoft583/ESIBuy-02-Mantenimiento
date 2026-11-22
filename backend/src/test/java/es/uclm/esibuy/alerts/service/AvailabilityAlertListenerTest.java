package es.uclm.esibuy.alerts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import es.uclm.esibuy.products.event.ProductChangedEvent;
import es.uclm.esibuy.products.event.ProductSnapshot;

@ExtendWith(MockitoExtension.class)
class AvailabilityAlertListenerTest {

    private static final String PRODUCT_ID = "p1";
    private static final String PRODUCT_NAME = "Auriculares";
    private static final String CUSTOMER = "u1";

    @Mock
    private FavoriteRecipientFinder recipientFinder;

    @Mock
    private AlertRepository alertRepository;

    private AvailabilityAlertListener listener;

    @BeforeEach
    void setUp() {
        listener = new AvailabilityAlertListener(recipientFinder, alertRepository);
    }

    private ProductSnapshot snapshot(boolean available, int stock, boolean premiumOnly) {
        return new ProductSnapshot(PRODUCT_ID, PRODUCT_NAME, BigDecimal.TEN, null, null, available, stock, premiumOnly);
    }

    private ProductSnapshot snapshot(boolean available, int stock) {
        return snapshot(available, stock, false);
    }

    private ProductChangedEvent change(ProductSnapshot before, ProductSnapshot after) {
        return new ProductChangedEvent(before, after);
    }

    @SuppressWarnings("unchecked")
    private List<Alert> savedAlerts() {
        ArgumentCaptor<List<Alert>> captor = ArgumentCaptor.forClass(List.class);
        verify(alertRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("HU27-CP29: si el producto vuelve a estar disponible se avisa a quien lo tiene en favoritos")
    void aProductThatBecomesAvailableAlertsItsFollowers() {
        when(recipientFinder.find(PRODUCT_ID, false)).thenReturn(List.of(
                new FavoriteRecipient(CUSTOMER, false), new FavoriteRecipient("u2", true)));

        listener.onProductChanged(change(snapshot(false, 5), snapshot(true, 5)));

        List<Alert> alerts = savedAlerts();
        assertThat(alerts).extracting(Alert::getCustomerId).containsExactly(CUSTOMER, "u2");
        Alert alert = alerts.get(0);
        assertThat(alert.getType()).isEqualTo(AlertType.AVAILABILITY);
        assertThat(alert.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(alert.getProductName()).isEqualTo(PRODUCT_NAME);
        assertThat(alert.getMessage()).isEqualTo("«Auriculares» vuelve a estar disponible");
        assertThat(alert.isRead()).isFalse();
    }

    @Test
    @DisplayName("HU27-CP30: pasar de sin stock a con stock, estando activo, también cuenta como volver a estar disponible")
    void restockingAnActiveProductAlerts() {
        when(recipientFinder.find(PRODUCT_ID, false)).thenReturn(List.of(new FavoriteRecipient(CUSTOMER, false)));

        listener.onProductChanged(change(snapshot(true, 0), snapshot(true, 3)));

        assertThat(savedAlerts()).extracting(Alert::getCustomerId).containsExactly(CUSTOMER);
    }

    @Test
    @DisplayName("HU27-CP31: si cambian las unidades pero sigue disponible no se avisa (RN-15)")
    void changingTheUnitsOfAnAvailableProductDoesNotAlert() {
        listener.onProductChanged(change(snapshot(true, 3), snapshot(true, 8)));
        listener.onProductChanged(change(snapshot(true, 8), snapshot(true, 1)));

        verifyNoInteractions(recipientFinder, alertRepository);
    }

    @Test
    @DisplayName("HU27-CP32: si el producto deja de estar disponible no se avisa")
    void aProductThatStopsBeingAvailableDoesNotAlert() {
        listener.onProductChanged(change(snapshot(true, 5), snapshot(false, 5)));
        listener.onProductChanged(change(snapshot(true, 5), snapshot(true, 0)));

        verifyNoInteractions(recipientFinder, alertRepository);
    }

    @Test
    @DisplayName("HU27-CP33: si sigue sin poder comprarse no se avisa")
    void aProductThatStaysUnavailableDoesNotAlert() {
        // Activo pero sin stock, o con stock pero desactivado: en ambos casos sigue sin poder comprarse
        listener.onProductChanged(change(snapshot(false, 5), snapshot(false, 9)));
        listener.onProductChanged(change(snapshot(true, 0), snapshot(false, 4)));
        listener.onProductChanged(change(snapshot(false, 0), snapshot(true, 0)));

        verifyNoInteractions(recipientFinder, alertRepository);
    }

    @Test
    @DisplayName("HU27-CP34: un producto exclusivo Premium solo busca destinatarios Premium")
    void aPremiumOnlyProductOnlyLooksForPremiumRecipients() {
        when(recipientFinder.find(PRODUCT_ID, true)).thenReturn(List.of(new FavoriteRecipient("premium", true)));

        listener.onProductChanged(change(snapshot(false, 5, true), snapshot(true, 5, true)));

        verify(recipientFinder).find(PRODUCT_ID, true);
        assertThat(savedAlerts()).extracting(Alert::getCustomerId).containsExactly("premium");
    }

    @Test
    @DisplayName("HU27-CP35: sin destinatarios no se guarda nada")
    void noRecipientsMeansNothingIsSaved() {
        when(recipientFinder.find(PRODUCT_ID, false)).thenReturn(List.of());

        listener.onProductChanged(change(snapshot(false, 5), snapshot(true, 5)));

        verify(alertRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("HU27-CP36: el escuchador recibe el evento publicado por Spring")
    void springDeliversThePublishedEventToTheListener() {
        FavoriteRecipientFinder finder = mock(FavoriteRecipientFinder.class);
        AlertRepository repository = mock(AlertRepository.class);
        when(finder.find(PRODUCT_ID, false)).thenReturn(List.of(new FavoriteRecipient(CUSTOMER, false)));

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(FavoriteRecipientFinder.class, () -> finder);
            context.registerBean(AlertRepository.class, () -> repository);
            context.registerBean(AvailabilityAlertListener.class);
            context.refresh();

            context.publishEvent(change(snapshot(false, 5), snapshot(true, 5)));
        }

        verify(repository).saveAll(any());
    }
}
