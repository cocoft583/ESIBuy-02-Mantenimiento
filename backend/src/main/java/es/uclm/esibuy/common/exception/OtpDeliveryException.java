package es.uclm.esibuy.common.exception;

public class OtpDeliveryException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public OtpDeliveryException(Throwable cause) {
        super("No se ha podido enviar el código de verificación. Inténtalo de nuevo más tarde.", cause);
    }
}