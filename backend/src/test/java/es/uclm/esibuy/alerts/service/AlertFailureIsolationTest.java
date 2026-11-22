package es.uclm.esibuy.alerts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import es.uclm.esibuy.alerts.repository.AlertRepository;
import es.uclm.esibuy.products.event.ProductChangedEvent;
import es.uclm.esibuy.products.event.ProductSnapshot;

class AlertFailureIsolationTest {

    private static final String PRODUCT_ID = "p1";
    private static final String CUSTOMER = "cliente-secreto";

    private final FavoriteRecipientFinder finder = mock(FavoriteRecipientFinder.class);
    private final AlertRepository repository = mock(AlertRepository.class);
    private final Logger guardLogger = (Logger) LoggerFactory.getLogger(AlertFailureGuard.class);
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void captureLogs() {
        logs = new ListAppender<>();
        logs.start();
        guardLogger.addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {
        guardLogger.detachAppender(logs);
    }

    private ProductSnapshot snapshot(String price, boolean available, int stock) {
        return new ProductSnapshot(PRODUCT_ID, "Auriculares", new BigDecimal(price), null, null, available, stock, false);
    }

    // Baja el precio y, a la vez, el producto vuelve a estar disponible: lo atienden los dos escuchadores
    private ProductChangedEvent priceDropAndRestock() {
        return new ProductChangedEvent(snapshot("25.41", false, 0), snapshot("19.99", true, 5));
    }

    @Test
    @DisplayName("HU27-CP37: si buscar los destinatarios falla, la alerta de precio no hace fallar el guardado")
    void aFailureFindingRecipientsDoesNotBreakThePriceAlert() {
        when(finder.find(PRODUCT_ID, false)).thenThrow(new IllegalStateException("Mongo caído"));
        PriceDropAlertListener listener = new PriceDropAlertListener(finder, repository);

        assertThatCode(() -> listener.onProductChanged(priceDropAndRestock())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("HU27-CP38: si guardar las alertas falla, la alerta de disponibilidad no hace fallar el guardado")
    void aFailureSavingAlertsDoesNotBreakTheAvailabilityAlert() {
        when(finder.find(PRODUCT_ID, false)).thenReturn(List.of(new FavoriteRecipient(CUSTOMER, false)));
        doThrow(new IllegalStateException("Mongo caído")).when(repository).saveAll(any());
        AvailabilityAlertListener listener = new AvailabilityAlertListener(finder, repository);

        assertThatCode(() -> listener.onProductChanged(priceDropAndRestock())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("HU27-CP39: el fallo se escribe en el log con el producto y sin datos del cliente")
    void theFailureIsLoggedWithTheProductAndNoCustomerData() {
        when(finder.find(PRODUCT_ID, false)).thenReturn(List.of(new FavoriteRecipient(CUSTOMER, false)));
        doThrow(new IllegalStateException("Mongo caído")).when(repository).saveAll(any());

        new PriceDropAlertListener(finder, repository).onProductChanged(priceDropAndRestock());

        assertThat(logs.list).hasSize(1);
        ILoggingEvent entry = logs.list.get(0);
        assertThat(entry.getLevel()).isEqualTo(Level.WARN);
        assertThat(entry.getFormattedMessage())
                .isEqualTo("No se pudo generar la alerta de bajada de precio del producto p1")
                .doesNotContain(CUSTOMER);
        assertThat(entry.getThrowableProxy().getMessage()).isEqualTo("Mongo caído");
    }

    @Test
    @DisplayName("HU27-CP40: si falla una alerta, la otra se genera igualmente")
    void aFailingAlertDoesNotStopTheOtherOne() {
        when(finder.find(PRODUCT_ID, false)).thenReturn(List.of(new FavoriteRecipient(CUSTOMER, false)));
        // La primera vez que se guardan alertas falla; la segunda sale bien
        doThrow(new IllegalStateException("Mongo caído")).doReturn(List.of()).when(repository).saveAll(any());

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(FavoriteRecipientFinder.class, () -> finder);
            context.registerBean(AlertRepository.class, () -> repository);
            context.registerBean(PriceDropAlertListener.class);
            context.registerBean(AvailabilityAlertListener.class);
            context.refresh();

            assertThatCode(() -> context.publishEvent(priceDropAndRestock())).doesNotThrowAnyException();
        }

        verify(repository, times(2)).saveAll(any());
        assertThat(logs.list).hasSize(1);
    }

    @Test
    @DisplayName("HU27-CP41: si todo va bien no se escribe nada en el log")
    void nothingIsLoggedWhenEverythingWorks() {
        when(finder.find(PRODUCT_ID, false)).thenReturn(List.of(new FavoriteRecipient(CUSTOMER, false)));

        new PriceDropAlertListener(finder, repository).onProductChanged(priceDropAndRestock());
        new AvailabilityAlertListener(finder, repository).onProductChanged(priceDropAndRestock());

        assertThat(logs.list).isEmpty();
        verify(repository, times(2)).saveAll(any());
    }
}
