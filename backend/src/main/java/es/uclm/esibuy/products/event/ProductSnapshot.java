package es.uclm.esibuy.products.event;

import java.math.BigDecimal;

import es.uclm.esibuy.products.model.Product;

/**
 * Copia inmutable de lo que importa de un producto en un momento dado. El producto es un objeto
 * que se modifica al editarlo, así que la foto de "antes" hay que sacarla antes de tocarlo.
 */
public record ProductSnapshot(
        String id,
        String name,
        BigDecimal price,
        BigDecimal discount,
        BigDecimal premiumDiscount,
        boolean available,
        int stock,
        boolean premiumOnly) {

    public static ProductSnapshot of(Product product) {
        return new ProductSnapshot(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getDiscount(),
                product.getPremiumDiscount(),
                product.isAvailable(),
                product.getStock(),
                product.isPremiumOnly());
    }

    public ProductSnapshot withStock(int newStock) {
        return new ProductSnapshot(id, name, price, discount, premiumDiscount, available, newStock, premiumOnly);
    }

    /** Un producto se puede comprar si está activo y le quedan unidades. */
    public boolean isPurchasable() {
        return available && stock > 0;
    }
}
