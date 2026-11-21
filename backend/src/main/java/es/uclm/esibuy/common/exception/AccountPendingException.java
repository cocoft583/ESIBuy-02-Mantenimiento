package es.uclm.esibuy.common.exception;

/**
 * La contraseña es correcta pero la cuenta todavía no la ha aprobado un administrador.
 * No hereda de {@link InvalidCredentialsException} para que no cuente como intento fallido.
 */
public class AccountPendingException extends RuntimeException {

    public static final String CODE = "ACCOUNT_PENDING";

    public AccountPendingException() {
        super("Tu cuenta está pendiente de aprobación por un administrador");
    }
}
