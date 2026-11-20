package es.uclm.esibuy.security.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import es.uclm.esibuy.common.exception.ApiExceptionHandler;
import es.uclm.esibuy.users.controller.AdminController;
import es.uclm.esibuy.users.service.AdminService;

@WebMvcTest(AdminController.class)
@Import({ SecurityConfig.class, ApiExceptionHandler.class })
class CorsAppMovilTest {

        private static final String LOGIN = "/api/users/login";

        @Autowired
        private MockMvc mockMvc;

        @MockBean
        private AdminService adminService;

        @ParameterizedTest
        @ValueSource(strings = { "https://localhost", "capacitor://localhost" })
        void laAppMovilPuedeHacerLaPreflightDelLogin(String origen) throws Exception {
                mockMvc.perform(options(LOGIN)
                                .header("Origin", origen)
                                .header("Access-Control-Request-Method", "POST")
                                .header("Access-Control-Request-Headers", "content-type"))
                                .andExpect(status().isOk())
                                .andExpect(header().string("Access-Control-Allow-Origin", origen))
                                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        }

        @Test
        void unOrigenDesconocidoNoPuedeHacerLaPreflightDelLogin() throws Exception {
                mockMvc.perform(options(LOGIN)
                                .header("Origin", "https://desconocido.example.com")
                                .header("Access-Control-Request-Method", "POST"))
                                .andExpect(status().isForbidden());
        }
}
