package es.uclm.esibuy.alerts.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Aísla las alertas del guardado del producto (RNF-03). Los escuchadores se ejecutan dentro de
 * la petición que edita el producto: si una alerta fallara, el vendedor vería un error aunque su
 * producto ya estuviera guardado. Aquí el error se escribe en el log y no sale de la alerta, así
 * que tampoco impide que se generen las demás.
 */
final class AlertFailureGuard {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlertFailureGuard.class);

    private AlertFailureGuard() {
    }

    static void run(String alertKind, String productId, Runnable generation) {
        try {
            generation.run();
        } catch (RuntimeException exception) {
            // Solo el identificador del producto: ningún dato del cliente va al log
            LOGGER.warn("No se pudo generar la alerta de {} del producto {}", alertKind, productId, exception);
        }
    }
}
