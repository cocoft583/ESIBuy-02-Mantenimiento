package es.uclm.esibuy.common.exception;

public class ProductoExclusivoPremiumException extends RuntimeException {

    public static final String CODE = "PREMIUM_ONLY";

    public ProductoExclusivoPremiumException() {
        super("Este producto es exclusivo para clientes Premium");
    }
}
