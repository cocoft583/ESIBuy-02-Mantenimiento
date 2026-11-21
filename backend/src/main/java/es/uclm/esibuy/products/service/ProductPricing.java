package es.uclm.esibuy.products.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import es.uclm.esibuy.products.model.Product;

/**
 * Cálculo del precio que paga un cliente por un producto en un momento dado.
 * Lo comparten el carrito y los pedidos para que ambos usen siempre la misma regla.
 */
public final class ProductPricing {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private ProductPricing() {
    }

    /**
     * Descuento aplicable, en porcentaje, y si incluye el descuento premium.
     */
    public record Discount(BigDecimal percentage, boolean premiumApplied) {

        public static Discount none() {
            return new Discount(BigDecimal.ZERO, false);
        }
    }

    public static Discount resolveDiscount(Product product, boolean premiumCustomer) {
        if (product == null) {
            return Discount.none();
        }

        BigDecimal standardDiscount = positiveOrZero(product.getDiscount());
        BigDecimal premiumDiscount = positiveOrZero(product.getPremiumDiscount());

        if (premiumCustomer) {
            BigDecimal totalDiscount = standardDiscount.add(premiumDiscount).min(ONE_HUNDRED);
            boolean hasPremiumDiscount = premiumDiscount.compareTo(BigDecimal.ZERO) > 0;
            return new Discount(totalDiscount, hasPremiumDiscount);
        }

        return new Discount(standardDiscount, false);
    }

    public static BigDecimal effectivePrice(Product product, boolean premiumCustomer) {
        BigDecimal price = product.getPrice();
        BigDecimal discount = resolveDiscount(product, premiumCustomer).percentage();

        if (discount.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal factor = ONE_HUNDRED
                    .subtract(discount)
                    .divide(ONE_HUNDRED, 4, RoundingMode.HALF_UP);

            return price.multiply(factor).setScale(2, RoundingMode.HALF_UP);
        }

        return price.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal positiveOrZero(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0
                ? value
                : BigDecimal.ZERO;
    }
}
