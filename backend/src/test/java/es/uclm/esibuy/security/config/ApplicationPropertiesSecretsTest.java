package es.uclm.esibuy.security.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

class ApplicationPropertiesSecretsTest {

    private Properties loadProperties() throws IOException {
        return PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));
    }

    @Test
    void aesKeyHasNoDefaultValue() throws IOException {
        assertEquals("${AES_KEY}", loadProperties().getProperty("app.security.aes-key"));
    }

    @Test
    void unusedJwtSecretIsRemoved() throws IOException {
        assertFalse(loadProperties().containsKey("jwt.secret"));
    }
}