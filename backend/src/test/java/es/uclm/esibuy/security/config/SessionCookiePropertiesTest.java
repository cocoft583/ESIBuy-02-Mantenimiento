package es.uclm.esibuy.security.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

class SessionCookiePropertiesTest {

    private Properties load(String file) throws IOException {
        return PropertiesLoaderUtils.loadProperties(new ClassPathResource(file));
    }

    @Test
    void sessionCookieIsSameSiteNone() throws IOException {
        assertEquals("None", load("application.properties").getProperty("server.servlet.session.cookie.same-site"));
    }

    @Test
    void sessionCookieIsAlwaysSecure() throws IOException {
        assertEquals("true", load("application.properties").getProperty("server.servlet.session.cookie.secure"));
    }

    @Test
    void devProfileKeepsTheSameCookieAttributes() throws IOException {
        Properties dev = load("application-dev.properties");

        assertFalse(dev.containsKey("server.servlet.session.cookie.same-site"));
        assertFalse(dev.containsKey("server.servlet.session.cookie.secure"));
    }

    @Test
    void dockerfileDoesNotOverrideCookieAttributes() throws IOException {
        String dockerfile = Files.readString(Path.of("Dockerfile"));

        assertFalse(dockerfile.contains("SESSION_COOKIE_SAME_SITE"));
        assertFalse(dockerfile.contains("SESSION_COOKIE_SECURE"));
    }
}