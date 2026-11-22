package es.uclm.esibuy.alerts.service;

import java.util.List;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import es.uclm.esibuy.alerts.model.Alert;
import es.uclm.esibuy.alerts.model.AlertType;
import es.uclm.esibuy.alerts.repository.AlertRepository;
import es.uclm.esibuy.products.event.ProductChangedEvent;
import es.uclm.esibuy.products.event.ProductSnapshot;

/**
 * Avisa a quien tiene un producto en favoritos cuando vuelve a poder comprarse (RN-15): pasa de
 * no disponible a disponible con unidades. Si solo cambian las unidades y sigue disponible, no avisa.
 */
@Component
public class AvailabilityAlertListener {

    private final FavoriteRecipientFinder recipientFinder;
    private final AlertRepository alertRepository;

    public AvailabilityAlertListener(FavoriteRecipientFinder recipientFinder, AlertRepository alertRepository) {
        this.recipientFinder = recipientFinder;
        this.alertRepository = alertRepository;
    }

    @EventListener
    public void onProductChanged(ProductChangedEvent event) {
        AlertFailureGuard.run("disponibilidad", event.after().id(), () -> notifyAvailability(event));
    }

    private void notifyAvailability(ProductChangedEvent event) {
        ProductSnapshot before = event.before();
        ProductSnapshot after = event.after();

        if (before.isPurchasable() || !after.isPurchasable()) {
            return;
        }

        List<Alert> alerts = recipientFinder.find(after.id(), after.premiumOnly()).stream()
                .map(recipient -> new Alert(
                        recipient.customerId(),
                        AlertType.AVAILABILITY,
                        after.id(),
                        after.name(),
                        "«" + after.name() + "» vuelve a estar disponible"))
                .toList();

        if (!alerts.isEmpty()) {
            alertRepository.saveAll(alerts);
        }
    }
}
