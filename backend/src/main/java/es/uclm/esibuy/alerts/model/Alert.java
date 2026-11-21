package es.uclm.esibuy.alerts.model;

import java.time.Instant;
import java.util.Objects;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Aviso que recibe un cliente en su bandeja. Guarda el nombre del producto tal como era
 * al generarse, porque el producto puede cambiar de nombre o desaparecer después.
 */
@Document("alerts")
@CompoundIndex(name = "customer_created_idx", def = "{'customerId': 1, 'createdAt': -1}")
public class Alert {

    @Id
    private String id;

    private String customerId;
    private AlertType type;
    private String productId;
    private String productName;
    private String message;
    private Instant createdAt;
    private boolean read;

    protected Alert() {
    }

    public Alert(
            String customerId,
            AlertType type,
            String productId,
            String productName,
            String message) {
        this.customerId = Objects.requireNonNull(customerId);
        this.type = Objects.requireNonNull(type);
        this.productId = Objects.requireNonNull(productId);
        this.productName = productName;
        this.message = Objects.requireNonNull(message);
        this.createdAt = Instant.now();
        this.read = false;
    }

    public String getId() {
        return id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public AlertType getType() {
        return type;
    }

    public String getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isRead() {
        return read;
    }

    public void markAsRead() {
        this.read = true;
    }
}
