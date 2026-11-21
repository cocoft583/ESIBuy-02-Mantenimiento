package es.uclm.esibuy.common.exception;

public class StockInsuficienteException extends IllegalStateException {

    public static final String CODE = "INSUFFICIENT_STOCK";

    public StockInsuficienteException() {
        super("No hay stock suficiente para la cantidad solicitada");
    }

    public StockInsuficienteException(String message) {
        super(message);
    }
}
