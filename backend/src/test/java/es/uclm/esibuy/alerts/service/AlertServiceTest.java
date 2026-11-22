package es.uclm.esibuy.alerts.service;

import java.util.List;
import java.util.Optional;

import es.uclm.esibuy.alerts.dto.AlertDTO;
import es.uclm.esibuy.alerts.model.Alert;
import es.uclm.esibuy.alerts.model.AlertType;
import es.uclm.esibuy.alerts.repository.AlertRepository;
import es.uclm.esibuy.common.exception.AlertNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    private static final String CUSTOMER_ID = "cliente-1";

    @Mock
    private AlertRepository alertRepository;

    private AlertService service;

    @BeforeEach
    void setUp() {
        service = new AlertService(alertRepository);
    }

    private Alert alert(String id, AlertType type, String message) {
        Alert alert = new Alert(CUSTOMER_ID, type, "producto-1", "Auriculares", message);
        ReflectionTestUtils.setField(alert, "id", id);
        return alert;
    }

    @Test
    @DisplayName("HU29-CP06: la bandeja devuelve las alertas en el orden del repositorio, con todos sus datos")
    void shouldListTheAlertsWithAllTheirData() {
        when(alertRepository.findAllByCustomerIdOrderByCreatedAtDesc(CUSTOMER_ID)).thenReturn(List.of(
                alert("a2", AlertType.AVAILABILITY, "Vuelve a estar disponible"),
                alert("a1", AlertType.PRICE_DROP, "Ha bajado de precio")));

        List<AlertDTO> result = service.list(CUSTOMER_ID);

        assertEquals(List.of("a2", "a1"), result.stream().map(AlertDTO::id).toList());
        AlertDTO first = result.get(0);
        assertEquals(AlertType.AVAILABILITY, first.type());
        assertEquals("producto-1", first.productId());
        assertEquals("Auriculares", first.productName());
        assertEquals("Vuelve a estar disponible", first.message());
        assertFalse(first.read());
        assertEquals(true, first.createdAt() != null);
    }

    @Test
    @DisplayName("HU29-CP07: un cliente sin alertas recibe una bandeja vacía")
    void shouldReturnAnEmptyInboxWhenThereAreNoAlerts() {
        when(alertRepository.findAllByCustomerIdOrderByCreatedAtDesc(CUSTOMER_ID)).thenReturn(List.of());

        assertTrue(service.list(CUSTOMER_ID).isEmpty());
    }

    @Test
    @DisplayName("HU29-CP08: el contador es el número de alertas sin leer del cliente")
    void shouldCountTheUnreadAlertsOfTheCustomer() {
        when(alertRepository.countByCustomerIdAndReadFalse(CUSTOMER_ID)).thenReturn(3L);

        assertEquals(3L, service.countUnread(CUSTOMER_ID));
    }

    @Test
    @DisplayName("HU29-CP09: marcar una alerta como leída la guarda como leída")
    void shouldMarkAnAlertAsRead() {
        Alert alert = alert("a1", AlertType.PRICE_DROP, "Ha bajado de precio");
        when(alertRepository.findByIdAndCustomerId("a1", CUSTOMER_ID)).thenReturn(Optional.of(alert));

        service.markAsRead(CUSTOMER_ID, "a1");

        assertTrue(alert.isRead());
        verify(alertRepository).save(alert);
    }

    @Test
    @DisplayName("HU29-CP10: marcar como leída una alerta que ya lo estaba no vuelve a guardarla ni da error")
    void shouldNotSaveAgainAnAlertThatWasAlreadyRead() {
        Alert alert = alert("a1", AlertType.PRICE_DROP, "Ha bajado de precio");
        alert.markAsRead();
        when(alertRepository.findByIdAndCustomerId("a1", CUSTOMER_ID)).thenReturn(Optional.of(alert));

        service.markAsRead(CUSTOMER_ID, "a1");

        assertTrue(alert.isRead());
        verify(alertRepository, never()).save(any(Alert.class));
    }

    @Test
    @DisplayName("HU29-CP11: marcar todas como leídas guarda solo las que estaban sin leer")
    void shouldMarkAllTheUnreadAlertsAsRead() {
        Alert first = alert("a1", AlertType.PRICE_DROP, "Ha bajado de precio");
        Alert second = alert("a2", AlertType.NEW_PRODUCT, "Producto nuevo");
        when(alertRepository.findAllByCustomerIdAndReadFalse(CUSTOMER_ID)).thenReturn(List.of(first, second));

        service.markAllAsRead(CUSTOMER_ID);

        assertTrue(first.isRead());
        assertTrue(second.isRead());
        verify(alertRepository).saveAll(List.of(first, second));
    }

    @Test
    @DisplayName("HU29-CP12: si no hay alertas sin leer, marcar todas no guarda nada")
    void shouldNotSaveAnythingWhenThereAreNoUnreadAlerts() {
        when(alertRepository.findAllByCustomerIdAndReadFalse(CUSTOMER_ID)).thenReturn(List.of());

        service.markAllAsRead(CUSTOMER_ID);

        verify(alertRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("HU29-CP13: eliminar una alerta propia la borra")
    void shouldDeleteTheCustomersOwnAlert() {
        Alert alert = alert("a1", AlertType.PRICE_DROP, "Ha bajado de precio");
        when(alertRepository.findByIdAndCustomerId("a1", CUSTOMER_ID)).thenReturn(Optional.of(alert));

        service.delete(CUSTOMER_ID, "a1");

        verify(alertRepository).delete(alert);
    }

    @Test
    @DisplayName("HU29-CP14: la alerta de otro cliente o que no existe responde igual y no se modifica")
    void shouldTreatAnotherCustomersAlertAsNotFound() {
        when(alertRepository.findByIdAndCustomerId("ajena", CUSTOMER_ID)).thenReturn(Optional.empty());

        assertThrows(AlertNotFoundException.class, () -> service.markAsRead(CUSTOMER_ID, "ajena"));
        assertThrows(AlertNotFoundException.class, () -> service.delete(CUSTOMER_ID, "ajena"));

        verify(alertRepository, never()).save(any(Alert.class));
        verify(alertRepository, never()).delete(any(Alert.class));
    }
}
