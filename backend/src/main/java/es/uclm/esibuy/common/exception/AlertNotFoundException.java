package es.uclm.esibuy.common.exception;

/**
 * La alerta no existe o es de otro cliente: de cara al usuario es lo mismo, para no
 * revelar que la alerta de otro existe (RN-25).
 */
public class AlertNotFoundException extends RuntimeException {

    public AlertNotFoundException() {
        super("La alerta no existe");
    }
}
