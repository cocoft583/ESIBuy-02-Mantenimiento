package es.uclm.esibuy.alerts.model;

/**
 * Motivo por el que se avisa al cliente. La HU27 genera los dos primeros (productos de
 * sus listas de favoritos) y la HU28 el tercero (productos nuevos de sus categorías).
 */
public enum AlertType {
    PRICE_DROP,
    AVAILABILITY,
    NEW_PRODUCT
}
