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
class PriceDropAlertListenerTest {

    private static final String PRODUCT_ID = "p1";
    private static final String PRODUCT_NAME = "Auriculares";
    private static final String OLD_PRICE = "25.41";
    private static final String NEW_PRICE = "19.99";
    private static final String HUNDRED = "100.00";
    private static final String STANDARD_CUSTOMER = "estandar";
    private static final String PREMIUM_CUSTOMER = "premium";

    @Mock
    private FavoriteRecipientFinder recipientFinder;

    @Mock
    private AlertRepository alertRepository;

    private PriceDropAlertListener listener;

    @BeforeEach
    void setUp() {
        listener = new PriceDropAlertListener(recipientFinder, alertRepository);
    }

    private ProductSnapshot snapshot(String price, String discount, String premiumDiscount, boolean premiumOnly) {
        return new ProductSnapshot(
                PRODUCT_ID,
                PRODUCT_NAME,
                price == null ? null : new BigDecimal(price),
                discount == null ? null : new BigDecimal(discount),
                premiumDiscount == null ? null : new BigDecimal(premiumDiscount),
                true,
                5,
                premiumOnly);
    }

    private ProductSnapshot snapshot(String price) {
        return snapshot(price, null, null, false);
    }

    @SuppressWarnings("unchecked")
    private List<Alert> savedAlerts() {
        ArgumentCaptor<List<Alert>> captor = ArgumentCaptor.forClass(List.class);
        verify(alertRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("HU27-CP20: si baja el precio se avisa con el precio anterior y el nuevo")
    void aPriceDropAlertsWithTheOldAndNewPrice() {
        when(recipientFinder.find(PRODUCT_ID, false)).thenReturn(List.of(new FavoriteRecipient("u1", false)));

        listener.onProductChanged(new ProductChangedEvent(snapshot(OLD_PRICE), snapshot(NEW_PRICE)));

        List<Alert> alerts = savedAlerts();
        assertThat(alerts).hasSize(1);
        Alert alert = alerts.get(0);
        assertThat(alert.getCustomerId()).isEqualTo("u1");
        assertThat(alert.getType()).isEqualTo(AlertType.PRICE_DROP);
        assertThat(alert.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(alert.getProductName()).isEqualTo(PRODUCT_NAME);
        assertThat(alert.getMessage()).isEqualTo("El precio de «Auriculares» ha bajado de 25,41 € a 19,99 €");
        assertThat(alert.isRead()).isFalse();
    }

    @Test
    @DisplayName("HU27-CP21: si sube el precio o queda igual no se avisa")
    void aPriceRiseOrNoChangeDoesNotAlert() {
        when(recipientFinder.find(PRODUCT_ID, false)).thenReturn(List.of(new FavoriteRecipient("u1", false)));

        listener.onProductChanged(new ProductChangedEvent(snapshot(NEW_PRICE), snapshot(OLD_PRICE)));
        listener.onProductChanged(new ProductChangedEvent(snapshot(NEW_PRICE), snapshot("19.990")));

        verify(alertRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("HU27-CP22: un cliente Estándar compara el precio Estándar y uno Premium el Premium")
    void eachCustomerTypeComparesItsOwnPrice() {
        // Solo mejora el descuento Premium: el cliente Estándar paga lo mismo, el Premium paga menos
        when(recipientFinder.find(PRODUCT_ID, false)).thenReturn(List.of(
                new FavoriteRecipient(STANDARD_CUSTOMER, false), new FavoriteRecipient(PREMIUM_CUSTOMER, true)));

        listener.onProductChanged(new ProductChangedEvent(
                snapshot(HUNDRED, "10", "5", false), snapshot(HUNDRED, "10", "20", false)));

        List<Alert> alerts = savedAlerts();
        assertThat(alerts).extracting(Alert::getCustomerId).containsExactly(PREMIUM_CUSTOMER);
        assertThat(alerts.get(0).getMessage()).contains("de 85,00 € a 70,00 €");
    }

    @Test
    @DisplayName("HU27-CP23: una bajada del descuento Estándar avisa a los dos tipos de cliente")
    void aStandardDiscountIncreaseAlertsBothCustomerTypes() {
        when(recipientFinder.find(PRODUCT_ID, false)).thenReturn(List.of(
                new FavoriteRecipient(STANDARD_CUSTOMER, false), new FavoriteRecipient(PREMIUM_CUSTOMER, true)));

        listener.onProductChanged(new ProductChangedEvent(
                snapshot(HUNDRED, "0", "10", false), snapshot(HUNDRED, "20", "10", false)));

        List<Alert> alerts = savedAlerts();
        assertThat(alerts).extracting(Alert::getCustomerId).containsExactly(STANDARD_CUSTOMER, PREMIUM_CUSTOMER);
        assertThat(alerts.get(0).getMessage()).contains("de 100,00 € a 80,00 €");
        assertThat(alerts.get(1).getMessage()).contains("de 90,00 € a 70,00 €");
    }

    @Test
    @DisplayName("HU27-CP24: si solo cambia el stock o la disponibilidad ni siquiera se buscan destinatarios")
    void anUnrelatedChangeDoesNotLookForRecipients() {
        ProductSnapshot before = snapshot(OLD_PRICE);
        ProductSnapshot after = new ProductSnapshot(
                PRODUCT_ID, PRODUCT_NAME, new BigDecimal(OLD_PRICE), null, null, false, 0, false);

        listener.onProductChanged(new ProductChangedEvent(before, after));

        verifyNoInteractions(recipientFinder, alertRepository);
    }

    @Test
    @DisplayName("HU27-CP25: un producto sin precio no genera alertas")
    void aProductWithoutPriceDoesNotAlert() {
        listener.onProductChanged(new ProductChangedEvent(snapshot(null), snapshot("10.00")));
        listener.onProductChanged(new ProductChangedEvent(snapshot("10.00"), snapshot(null)));

        verifyNoInteractions(recipientFinder, alertRepository);
    }

    @Test
    @DisplayName("HU27-CP26: un producto exclusivo Premium solo busca destinatarios Premium")
    void aPremiumOnlyProductOnlyLooksForPremiumRecipients() {
        when(recipientFinder.find(PRODUCT_ID, true)).thenReturn(List.of(new FavoriteRecipient(PREMIUM_CUSTOMER, true)));

        listener.onProductChanged(new ProductChangedEvent(
                snapshot(HUNDRED, null, null, true), snapshot("80.00", null, null, true)));

        verify(recipientFinder).find(PRODUCT_ID, true);
        assertThat(savedAlerts()).extracting(Alert::getCustomerId).containsExactly(PREMIUM_CUSTOMER);
    }

    @Test
    @DisplayName("HU27-CP27: sin destinatarios no se guarda nada")
    void noRecipientsMeansNothingIsSaved() {
        when(recipientFinder.find(PRODUCT_ID, false)).thenReturn(List.of());

        listener.onProductChanged(new ProductChangedEvent(snapshot(OLD_PRICE), snapshot(NEW_PRICE)));

        verify(alertRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("HU27-CP28: el escuchador recibe el evento publicado por Spring")
    void springDeliversThePublishedEventToTheListener() {
        FavoriteRecipientFinder finder = mock(FavoriteRecipientFinder.class);
        AlertRepository repository = mock(AlertRepository.class);
        when(finder.find(PRODUCT_ID, false)).thenReturn(List.of(new FavoriteRecipient("u1", false)));

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(FavoriteRecipientFinder.class, () -> finder);
            context.registerBean(AlertRepository.class, () -> repository);
            context.registerBean(PriceDropAlertListener.class);
            context.refresh();

            context.publishEvent(new ProductChangedEvent(snapshot(OLD_PRICE), snapshot(NEW_PRICE)));
        }

        verify(repository).saveAll(any());
    }
}
