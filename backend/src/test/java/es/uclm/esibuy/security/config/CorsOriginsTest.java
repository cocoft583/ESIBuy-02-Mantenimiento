package es.uclm.esibuy.security.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import es.uclm.esibuy.categories.controller.CategoryController;
import es.uclm.esibuy.categories.service.CategoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CategoryController.class)
@Import(SecurityConfig.class)
class CorsOriginsTest {

    private static final String ALLOWED_ORIGINS = "esibuy.security.cors.allowed-origins";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CategoryService categoryService;

    @Test
    void acceptsRequestsFromTheEsibuyWeb() throws Exception {
        mockMvc.perform(get("/api/categorias").header("Origin", "https://esibuy-2627.web.app"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://esibuy-2627.web.app"));
    }

    @Test
    void rejectsRequestsFromAnotherWeb() throws Exception {
        mockMvc.perform(get("/api/categorias").header("Origin", "https://otra-web.example"))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUT", "DELETE", "PATCH"})
    void theEsibuyWebCanSendWriteRequests(String method) throws Exception {
        mockMvc.perform(options("/api/categorias")
                        .header("Origin", "https://esibuy-2627.web.app")
                        .header("Access-Control-Request-Method", method))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://esibuy-2627.web.app"));
    }


    @Test
    void rejectsLocalhostOutsideTheDevProfile() throws Exception {
        mockMvc.perform(get("/api/categorias").header("Origin", "http://localhost:4200"))
                .andExpect(status().isForbidden());
    }

    @Test
    void productionOriginsHaveNoLocalhost() throws IOException {
        String origins = load("application.properties").getProperty(ALLOWED_ORIGINS);

        assertFalse(origins.contains("localhost"));
    }

    @Test
    void devProfileAllowsTheLocalAngularServer() throws IOException {
        String origins = load("application-dev.properties").getProperty(ALLOWED_ORIGINS);

        assertEquals(List.of("http://localhost:4200"), List.of(origins.split(",")));
    }

    private Properties load(String file) throws IOException {
        return PropertiesLoaderUtils.loadProperties(new ClassPathResource(file));
    }
}