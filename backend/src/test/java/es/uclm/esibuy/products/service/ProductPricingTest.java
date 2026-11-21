package es.uclm.esibuy.products.service;

import java.math.BigDecimal;
import java.util.List;

import es.uclm.esibuy.products.model.Product;
import es.uclm.esibuy.products.model.ProductDetails;
import es.uclm.esibuy.products.service.ProductPricing.Discount;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductPricingTest {

    @Test
    @DisplayName("HU12-CP01: sin descuentos el precio efectivo es el precio del producto")
    void shouldReturnTheListPriceWhenThereIsNoDiscount() {
        Product product = product("19.99", null, null);

        assertEquals(new BigDecimal("19.99"), ProductPricing.effectivePrice(product, false));
        assertEquals(new BigDecimal("19.99"), ProductPricing.effectivePrice(product, true));
    }

    @Test
    @DisplayName("HU12-CP02: un cliente estándar solo recibe el descuento general")
    void shouldApplyOnlyTheStandardDiscountToAStandardCustomer() {
        Product product = product("100.00", "10", "5");

        Discount discount = ProductPricing.resolveDiscount(product, false);

        assertEquals(new BigDecimal("10"), discount.percentage());
        assertFalse(discount.premiumApplied());
        assertEquals(new BigDecimal("90.00"), ProductPricing.effectivePrice(product, false));
    }

    @Test
    @DisplayName("HU12-CP03: un cliente premium suma el descuento general y el premium")
    void shouldAddTheStandardAndPremiumDiscountsForAPremiumCustomer() {
        Product product = product("100.00", "10", "5");

        Discount discount = ProductPricing.resolveDiscount(product, true);

        assertEquals(new BigDecimal("15"), discount.percentage());
        assertTrue(discount.premiumApplied());
        assertEquals(new BigDecimal("85.00"), ProductPricing.effectivePrice(product, true));
    }

    @Test
    @DisplayName("HU12-CP04: sin descuento premium, un cliente premium paga como uno estándar")
    void shouldNotMarkTheDiscountAsPremiumWhenTheProductHasNoPremiumDiscount() {
        Product product = product("100.00", "10", null);

        Discount discount = ProductPricing.resolveDiscount(product, true);

        assertEquals(new BigDecimal("10"), discount.percentage());
        assertFalse(discount.premiumApplied());
    }

    @Test
    @DisplayName("HU12-CP05: el descuento acumulado nunca supera el 100 %")
    void shouldCapTheAccumulatedDiscountAtOneHundredPercent() {
        Product product = product("50.00", "80", "40");

        assertEquals(new BigDecimal("100"), ProductPricing.resolveDiscount(product, true).percentage());
        assertEquals(new BigDecimal("0.00"), ProductPricing.effectivePrice(product, true));
    }

    @Test
    @DisplayName("HU12-CP06: un descuento negativo se ignora")
    void shouldIgnoreNegativeDiscounts() {
        Product product = product("40.00", "-10", "-5");

        assertEquals(BigDecimal.ZERO, ProductPricing.resolveDiscount(product, true).percentage());
        assertEquals(new BigDecimal("40.00"), ProductPricing.effectivePrice(product, true));
    }

    @Test
    @DisplayName("HU12-CP07: el precio efectivo se redondea a dos decimales hacia arriba")
    void shouldRoundTheEffectivePriceToTwoDecimals() {
        Product product = product("19.99", "15", null);

        // 19,99 * 0,85 = 16,9915
        assertEquals(new BigDecimal("16.99"), ProductPricing.effectivePrice(product, false));
    }

    @Test
    @DisplayName("HU12-CP08: sin producto no hay descuento")
    void shouldReturnNoDiscountWhenThereIsNoProduct() {
        Discount discount = ProductPricing.resolveDiscount(null, true);

        assertEquals(BigDecimal.ZERO, discount.percentage());
        assertFalse(discount.premiumApplied());
    }

    private static Product product(String price, String discount, String premiumDiscount) {
        return new Product(new ProductDetails(
                "Auriculares",
                List.of("categoria-1"),
                10,
                "Audio",
                new BigDecimal(price),
                discount != null ? new BigDecimal(discount) : null,
                premiumDiscount != null ? new BigDecimal(premiumDiscount) : null,
                null,
                true,
                "vendedor-1"));
    }
}
