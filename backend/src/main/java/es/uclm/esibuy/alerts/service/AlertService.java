package es.uclm.esibuy.alerts.service;

import java.util.List;

import org.springframework.stereotype.Service;

import es.uclm.esibuy.alerts.dto.AlertDTO;
import es.uclm.esibuy.alerts.model.Alert;
import es.uclm.esibuy.alerts.repository.AlertRepository;
import es.uclm.esibuy.common.exception.AlertNotFoundException;

@Service
public class AlertService {

    private final AlertRepository alertRepository;

    public AlertService(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    /** Bandeja del cliente, de la más reciente a la más antigua. */
    public List<AlertDTO> list(String customerId) {
        return alertRepository.findAllByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(AlertDTO::from)
                .toList();
    }

    public long countUnread(String customerId) {
        return alertRepository.countByCustomerIdAndReadFalse(customerId);
    }

    /** Marcar como leída una alerta que ya lo está no cambia nada ni da error. */
    public void markAsRead(String customerId, String alertId) {
        Alert alert = findOwnAlert(customerId, alertId);

        if (!alert.isRead()) {
            alert.markAsRead();
            alertRepository.save(alert);
        }
    }

    public void markAllAsRead(String customerId) {
        List<Alert> unread = alertRepository.findAllByCustomerIdAndReadFalse(customerId);
        if (unread.isEmpty()) {
            return;
        }

        unread.forEach(Alert::markAsRead);
        alertRepository.saveAll(unread);
    }

    public void delete(String customerId, String alertId) {
        alertRepository.delete(findOwnAlert(customerId, alertId));
    }

    private Alert findOwnAlert(String customerId, String alertId) {
        return alertRepository.findByIdAndCustomerId(alertId, customerId)
                .orElseThrow(AlertNotFoundException::new);
    }
}
