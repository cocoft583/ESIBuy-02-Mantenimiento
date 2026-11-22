package es.uclm.esibuy.alerts.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import es.uclm.esibuy.alerts.model.Alert;
import es.uclm.esibuy.alerts.model.AlertType;
import es.uclm.esibuy.alerts.repository.AlertRepository;
import es.uclm.esibuy.products.event.ProductChangedEvent;
import es.uclm.esibuy.products.event.ProductSnapshot;
import es.uclm.esibuy.products.service.ProductPricing;

/**
 * Avisa a quien tiene un producto en favoritos cuando baja el precio que paga: el Estándar o el
 * Premium según su tipo de cliente (RN-12, RN-13).
 */
@Component
public class PriceDropAlertListener {

    private static final Locale SPANISH = Locale.forLanguageTag("es-ES");

    private final FavoriteRecipientFinder recipientFinder;
    private final AlertRepository alertRepository;

    public PriceDropAlertListener(FavoriteRecipientFinder recipientFinder, AlertRepository alertRepository) {
        this.recipientFinder = recipientFinder;
        this.alertRepository = alertRepository;
    }

    @EventListener
    public void onProductChanged(ProductChangedEvent event) {
        ProductSnapshot before = event.before();
        ProductSnapshot after = event.after();

        // Sin precio no hay con qué comparar, y si no ha cambiado ni el precio ni los descuentos
        // no hace falta ni buscar a los destinatarios
        if (before.price() == null || after.price() == null || !pricingChanged(before, after)) {
            return;
        }

        List<Alert> alerts = new ArrayList<>();
        for (FavoriteRecipient recipient : recipientFinder.find(after.id(), after.premiumOnly())) {
            BigDecimal oldPrice = ProductPricing.effectivePrice(before, recipient.premium());
            BigDecimal newPrice = ProductPricing.effectivePrice(after, recipient.premium());

            if (newPrice.compareTo(oldPrice) < 0) {
                alerts.add(new Alert(
                        recipient.customerId(),
                        AlertType.PRICE_DROP,
                        after.id(),
                        after.name(),
                        message(after.name(), oldPrice, newPrice)));
            }
        }

        if (!alerts.isEmpty()) {
            alertRepository.saveAll(alerts);
        }
    }

    private boolean pricingChanged(ProductSnapshot before, ProductSnapshot after) {
        return differs(before.price(), after.price())
                || differs(before.discount(), after.discount())
                || differs(before.premiumDiscount(), after.premiumDiscount());
    }

    private boolean differs(BigDecimal first, BigDecimal second) {
        if (first == null || second == null) {
            return first != second;
        }

        return first.compareTo(second) != 0;
    }

    private String message(String productName, BigDecimal oldPrice, BigDecimal newPrice) {
        return String.format(SPANISH, "El precio de «%s» ha bajado de %.2f € a %.2f €",
                productName, oldPrice, newPrice);
    }
}
