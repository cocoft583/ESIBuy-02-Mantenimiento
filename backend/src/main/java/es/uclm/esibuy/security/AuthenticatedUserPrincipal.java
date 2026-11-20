package es.uclm.esibuy.security;

import java.io.Serializable;

import es.uclm.esibuy.users.model.UserRole;
import org.springframework.security.core.AuthenticatedPrincipal;

public record AuthenticatedUserPrincipal(
        String id,
        String email,
        UserRole role,
        boolean active,
        int sessionVersion) implements AuthenticatedPrincipal, Serializable {

    // Mantiene la firma anterior, que usan los tests de los controladores
    public AuthenticatedUserPrincipal(String id, String email, UserRole role, boolean active) {
        this(id, email, role, active, 0);
    }

    @Override
    public String getName() {
        return email;
    }
}
