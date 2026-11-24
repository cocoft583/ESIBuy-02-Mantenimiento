package es.uclm.esibuy.products.event;

import java.util.List;

/**
 * Se ha dado de alta un producto nuevo (HU28). Se publica después de guardarlo y solo al crearlo,
 * nunca al editarlo (RN-17). Lleva las categorías para saber a quién avisar.
 */
public record ProductCreatedEvent(ProductSnapshot product, List<String> categoryIds) {

    public ProductCreatedEvent {
        categoryIds = categoryIds == null ? List.of() : List.copyOf(categoryIds);
    }
}
