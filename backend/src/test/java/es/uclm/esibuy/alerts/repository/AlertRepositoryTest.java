package es.uclm.esibuy.alerts.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import es.uclm.esibuy.alerts.model.Alert;
import es.uclm.esibuy.alerts.model.AlertType;

// Necesita Docker: sin Docker estos tests se saltan en lugar de fallar
@DataMongoTest
@Testcontainers(disabledWithoutDocker = true)
class AlertRepositoryTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @Autowired
    private AlertRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    private Alert alert(String customerId, AlertType type, String message, Instant createdAt) {
        Alert alert = new Alert(customerId, type, "producto-1", "Auriculares", message);
        // La fecha se fija a mano para controlar el orden de la bandeja
        ReflectionTestUtils.setField(alert, "createdAt", createdAt);
        return repository.save(alert);
    }

    @Test
    @DisplayName("HU29-CP01: la bandeja del cliente va de la alerta más reciente a la más antigua")
    void shouldListTheCustomerAlertsNewestFirst() {
        Instant now = Instant.now();
        alert("cliente-1", AlertType.PRICE_DROP, "antigua", now.minus(2, ChronoUnit.DAYS));
        alert("cliente-1", AlertType.AVAILABILITY, "reciente", now);
        alert("cliente-1", AlertType.NEW_PRODUCT, "intermedia", now.minus(1, ChronoUnit.DAYS));

        List<Alert> alerts = repository.findAllByCustomerIdOrderByCreatedAtDesc("cliente-1");

        assertThat(alerts).extracting(Alert::getMessage).containsExactly("reciente", "intermedia", "antigua");
    }

    @Test
    @DisplayName("HU29-CP02: solo se ven las alertas del propio cliente")
    void shouldOnlyReturnTheAlertsOfTheCustomer() {
        alert("cliente-1", AlertType.PRICE_DROP, "mía", Instant.now());
        alert("cliente-2", AlertType.PRICE_DROP, "de otro", Instant.now());

        assertThat(repository.findAllByCustomerIdOrderByCreatedAtDesc("cliente-1"))
                .extracting(Alert::getMessage).containsExactly("mía");
        assertThat(repository.countByCustomerIdAndReadFalse("cliente-2")).isEqualTo(1);
    }

    @Test
    @DisplayName("HU29-CP03: el contador solo cuenta las alertas sin leer")
    void shouldCountOnlyTheUnreadAlerts() {
        Alert read = alert("cliente-1", AlertType.PRICE_DROP, "leída", Instant.now());
        read.markAsRead();
        repository.save(read);
        alert("cliente-1", AlertType.AVAILABILITY, "sin leer 1", Instant.now());
        alert("cliente-1", AlertType.NEW_PRODUCT, "sin leer 2", Instant.now());

        assertThat(repository.countByCustomerIdAndReadFalse("cliente-1")).isEqualTo(2);
        assertThat(repository.findAllByCustomerIdAndReadFalse("cliente-1"))
                .extracting(Alert::getMessage).containsExactlyInAnyOrder("sin leer 1", "sin leer 2");
    }

    @Test
    @DisplayName("HU29-CP04: una alerta ajena no se encuentra aunque se conozca su identificador")
    void shouldNotFindAnotherCustomersAlert() {
        Alert others = alert("cliente-2", AlertType.PRICE_DROP, "de otro", Instant.now());

        assertThat(repository.findByIdAndCustomerId(others.getId(), "cliente-1")).isEmpty();
        assertThat(repository.findByIdAndCustomerId(others.getId(), "cliente-2")).isPresent();
    }

    @Test
    @DisplayName("HU29-CP05: la alerta guarda el tipo, el producto, el mensaje, la fecha y que no está leída")
    void shouldStoreAllTheFieldsOfTheAlert() {
        Alert saved = alert("cliente-1", AlertType.NEW_PRODUCT, "Nuevo en Moda", Instant.now());

        Alert found = repository.findById(saved.getId()).orElseThrow();

        assertThat(found.getType()).isEqualTo(AlertType.NEW_PRODUCT);
        assertThat(found.getProductId()).isEqualTo("producto-1");
        assertThat(found.getProductName()).isEqualTo("Auriculares");
        assertThat(found.getMessage()).isEqualTo("Nuevo en Moda");
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.isRead()).isFalse();
    }
}
