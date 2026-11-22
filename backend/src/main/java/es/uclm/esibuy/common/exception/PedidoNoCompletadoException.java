package es.uclm.esibuy.common.exception;

public class PedidoNoCompletadoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PedidoNoCompletadoException(Throwable cause) {
        super("No se ha podido completar el pedido. No se te ha cobrado nada ni se ha modificado tu carrito. Inténtalo de nuevo más tarde.", cause);
    }
}
