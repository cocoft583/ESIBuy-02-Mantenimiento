package es.uclm.esibuy.alerts.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import es.uclm.esibuy.alerts.model.Alert;

@Repository
public interface AlertRepository extends MongoRepository<Alert, String> {

    /** Bandeja del cliente, de la más reciente a la más antigua. */
    List<Alert> findAllByCustomerIdOrderByCreatedAtDesc(String customerId);

    long countByCustomerIdAndReadFalse(String customerId);

    List<Alert> findAllByCustomerIdAndReadFalse(String customerId);

    /** Busca por cliente a la vez, así una alerta ajena no se encuentra (RN-25). */
    Optional<Alert> findByIdAndCustomerId(String id, String customerId);
}
