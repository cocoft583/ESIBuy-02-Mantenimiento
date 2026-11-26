package es.uclm.esibuy.security.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Token de «recordar sesión» de la app (HU30, T30.10). Solo se guarda el hash SHA-256 del
 * token: el valor en claro va únicamente en la cookie del móvil. Mongo borra los caducados.
 */
@Document(collection = "remember_me_tokens")
public class RememberMeToken {

    @Id
    private String id;

    @Indexed
    private String userId;

    @Indexed(unique = true)
    private String tokenHash;

    private int sessionVersion;

    @Indexed(expireAfter = "0s")
    private Instant expiresAt;

    protected RememberMeToken() {
    }

    public RememberMeToken(String userId, String tokenHash, int sessionVersion, Instant expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.sessionVersion = sessionVersion;
        this.expiresAt = expiresAt;
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public int getSessionVersion() {
        return sessionVersion;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
