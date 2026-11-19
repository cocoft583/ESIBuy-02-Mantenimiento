package es.uclm.esibuy.security.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

class SensitiveInformationPropertiesTest {

    private Properties load(String file) throws IOException {
        return PropertiesLoaderUtils.loadProperties(new ClassPathResource(file));
    }

    @Test
    void errorResponsesDoNotIncludeStackTrace() throws IOException {
        assertEquals("never", load("application.properties").getProperty("server.error.include-stacktrace"));
    }

    @Test
    void errorResponsesDoNotIncludeExceptionMessage() throws IOException {
        assertEquals("never", load("application.properties").getProperty("server.error.include-message"));
    }

    @Test
    void otpCodeIsNotLoggedByDefault() throws IOException {
        assertEquals("false", load("application.properties").getProperty("app.security.email-otp.log-code"));
    }

    @Test
    void passwordResetTokenIsNotLoggedByDefault() throws IOException {
        assertEquals("false", load("application.properties").getProperty("esibuy.password-reset.log-token"));
    }

    @Test
    void devProfileLogsOtpCodeAndResetToken() throws IOException {
        Properties dev = load("application-dev.properties");

        assertEquals("true", dev.getProperty("app.security.email-otp.log-code"));
        assertEquals("true", dev.getProperty("esibuy.password-reset.log-token"));
    }
}