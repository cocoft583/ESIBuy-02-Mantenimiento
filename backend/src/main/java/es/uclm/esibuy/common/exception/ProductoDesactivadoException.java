package es.uclm.esibuy.common.exception;

public class ProductoDesactivadoException extends RuntimeException {

    public static final String CODE = "PRODUCT_UNAVAILABLE";

    public ProductoDesactivadoException() {
        super("Este producto ya no está disponible");
    }
}
