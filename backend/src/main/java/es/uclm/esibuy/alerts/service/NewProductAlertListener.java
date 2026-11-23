package es.uclm.esibuy.alerts.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import es.uclm.esibuy.alerts.model.Alert;
import es.uclm.esibuy.alerts.model.AlertType;
import es.uclm.esibuy.alerts.repository.AlertRepository;
import es.uclm.esibuy.products.event.ProductCreatedEvent;
import es.uclm.esibuy.products.event.ProductSnapshot;
import es.uclm.esibuy.users.model.ClientProfile;
import es.uclm.esibuy.users.model.CustomerType;
import es.uclm.esibuy.users.model.User;
import es.uclm.esibuy.users.repository.UserRepository;

/**
 * Avisa de un producto nuevo a los clientes que tienen alguna de sus categorías entre las
 * favoritas (HU28): solo si se da de alta disponible (RN-18), una alerta por cliente, y un
 * exclusivo Premium solo a los clientes Premium.
 */
@Component
public class NewProductAlertListener {

    private final UserRepository userRepository;
    private final AlertRepository alertRepository;

    public NewProductAlertListener(UserRepository userRepository, AlertRepository alertRepository) {
        this.userRepository = userRepository;
        this.alertRepository = alertRepository;
    }

    @EventListener
    public void onProductCreated(ProductCreatedEvent event) {
        AlertFailureGuard.run("producto nuevo", event.product().id(), () -> notifyNewProduct(event));
    }

    private void notifyNewProduct(ProductCreatedEvent event) {
        ProductSnapshot product = event.product();
        if (!product.available() || event.categoryIds().isEmpty()) {
            return;
        }

        // Un cliente solo una vez, aunque tenga varias de las categorías del producto
        Set<String> customerIds = new LinkedHashSet<>();
        for (User user : userRepository.findAllByClientProfileFavoriteCategoryIdsIn(event.categoryIds())) {
            if (!product.premiumOnly() || isPremium(user)) {
                customerIds.add(user.getId());
            }
        }

        List<Alert> alerts = customerIds.stream()
                .map(customerId -> new Alert(
                        customerId,
                        AlertType.NEW_PRODUCT,
                        product.id(),
                        product.name(),
                        "Hay un producto nuevo en tus categorías favoritas: «" + product.name() + "»"))
                .toList();

        if (!alerts.isEmpty()) {
            alertRepository.saveAll(alerts);
        }
    }

    private boolean isPremium(User user) {
        ClientProfile profile = user.getClientProfile();

        return profile != null && CustomerType.PREMIUM.equals(profile.getCustomerType());
    }
}
