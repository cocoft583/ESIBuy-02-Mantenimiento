package es.uclm.esibuy.security.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import com.mongodb.client.result.UpdateResult;

import es.uclm.esibuy.users.model.User;

/**
 * HU09: antes los códigos de respaldo del 2FA se guardaban en claro en el campo
 * {@code backupCodes}. Al arrancar se borra ese campo de todos los usuarios; los que
 * tengan el 2FA activo regeneran sus códigos desde el perfil. Si ya no queda ninguno,
 * no hace nada.
 */
@Component
public class LegacyBackupCodesCleanup implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(LegacyBackupCodesCleanup.class);
    private static final String LEGACY_FIELD = "backupCodes";

    private final MongoTemplate mongoTemplate;

    public LegacyBackupCodesCleanup(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        UpdateResult result = mongoTemplate.updateMulti(
                Query.query(Criteria.where(LEGACY_FIELD).exists(true)),
                new Update().unset(LEGACY_FIELD),
                User.class);

        if (result.getModifiedCount() > 0) {
            LOGGER.info("Códigos de respaldo en claro eliminados de {} usuarios", result.getModifiedCount());
        }
    }
}
