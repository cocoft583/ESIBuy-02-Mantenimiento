package es.uclm.esibuy.alerts.dto;

import java.time.Instant;

import es.uclm.esibuy.alerts.model.Alert;
import es.uclm.esibuy.alerts.model.AlertType;

public record AlertDTO(
        String id,
        AlertType type,
        String productId,
        String productName,
        String message,
        Instant createdAt,
        boolean read) {

    public static AlertDTO from(Alert alert) {
        return new AlertDTO(
                alert.getId(),
                alert.getType(),
                alert.getProductId(),
                alert.getProductName(),
                alert.getMessage(),
                alert.getCreatedAt(),
                alert.isRead());
    }
}
