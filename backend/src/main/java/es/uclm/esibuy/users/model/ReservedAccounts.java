package es.uclm.esibuy.users.model;

import java.util.Locale;

/** Identificadores de cuenta que reserva la plataforma y que nadie puede registrar. */
public final class ReservedAccounts {

    /** Identificador del usuario técnico que conserva los pedidos de los clientes eliminados. */
    public static final String DELETED_CUSTOMER_ID = "cliente-eliminado";

    /** Correo que usaba antes el usuario técnico; se sigue reconociendo en los datos ya guardados. */
    public static final String LEGACY_DELETED_CUSTOMER_EMAIL = "cliente.eliminado@esibuy.local";

    private static final String RESERVED_EMAIL_DOMAIN = "@esibuy.local";

    private ReservedAccounts() {
    }

    /** Indica si no se puede registrar una cuenta con este correo o identificador. */
    public static boolean isReserved(String email) {
        String normalized = normalize(email);
        return normalized.equals(DELETED_CUSTOMER_ID) || normalized.endsWith(RESERVED_EMAIL_DOMAIN);
    }

    /** Indica si el correo o identificador es el del usuario técnico de los clientes eliminados. */
    public static boolean isDeletedCustomer(String email) {
        String normalized = normalize(email);
        return normalized.equals(DELETED_CUSTOMER_ID) || normalized.equals(LEGACY_DELETED_CUSTOMER_EMAIL);
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
