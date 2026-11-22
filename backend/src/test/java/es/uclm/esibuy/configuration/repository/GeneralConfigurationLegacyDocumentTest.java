package es.uclm.esibuy.configuration.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import es.uclm.esibuy.configuration.model.GeneralConfiguration;

// Necesita Docker: sin Docker estos tests se saltan en lugar de fallar
@DataMongoTest
@Testcontainers(disabledWithoutDocker = true)
class GeneralConfigurationLegacyDocumentTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private GeneralConfigurationRepository repository;

    @BeforeEach
    void setUp() {
        mongoTemplate.dropCollection("configuracion-general");
    }

    @Test
    @DisplayName("HU23-CP37: un documento de configuración guardado antes de existir el mínimo lo lee con el valor por defecto")
    void aDocumentWithoutTheMinimumIsReadWithTheDefault() {
        // Como el que ya hay en Atlas: sin el campo del mínimo de valoraciones
        mongoTemplate.getCollection("configuracion-general").insertOne(new Document("_id", GeneralConfiguration.DOCUMENT_ID)
                .append("ivaPorcentaje", "21.00")
                .append("gastosEnvio", "4.99")
                .append("envioGratuitoDesde", "50.00")
                .append("registroHabilitado", true));

        GeneralConfiguration configuration = repository.findById(GeneralConfiguration.DOCUMENT_ID).orElseThrow();

        assertThat(configuration.getMinimoValoracionesRanking()).isEqualTo(3);
    }

    @Test
    @DisplayName("HU23-CP38: el mínimo guardado se lee tal cual y sobrevive a guardar de nuevo la configuración")
    void theStoredMinimumIsReadAsIs() {
        GeneralConfiguration configuration = new GeneralConfiguration();
        configuration.setMinimoValoracionesRanking(9);
        repository.save(configuration);

        GeneralConfiguration reloaded = repository.findById(GeneralConfiguration.DOCUMENT_ID).orElseThrow();
        repository.save(reloaded);

        assertThat(repository.findById(GeneralConfiguration.DOCUMENT_ID).orElseThrow()
                .getMinimoValoracionesRanking()).isEqualTo(9);
    }
}
