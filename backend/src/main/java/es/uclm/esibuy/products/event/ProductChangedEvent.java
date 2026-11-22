package es.uclm.esibuy.products.event;

/**
 * Un producto existente se ha guardado con cambios. Se publica después de guardar y no al borrar
 * ni al crear el producto. Quien lo escuche no puede hacer fallar el guardado.
 */
public record ProductChangedEvent(ProductSnapshot before, ProductSnapshot after) {
}
