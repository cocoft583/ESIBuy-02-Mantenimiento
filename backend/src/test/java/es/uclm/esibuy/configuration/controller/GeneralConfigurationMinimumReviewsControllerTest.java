package es.uclm.esibuy.configuration.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import es.uclm.esibuy.common.exception.ApiExceptionHandler;
import es.uclm.esibuy.configuration.dto.GeneralConfigurationDTO;
import es.uclm.esibuy.configuration.model.GeneralConfiguration;
import es.uclm.esibuy.configuration.service.GeneralConfigurationService;
import es.uclm.esibuy.security.config.SecurityConfig;

@WebMvcTest(GeneralConfigurationController.class)
@Import({ ApiExceptionHandler.class, SecurityConfig.class })
class GeneralConfigurationMinimumReviewsControllerTest {

    private static final String API = "/api/configuracion";
    private static final String FIELD = "minimoValoracionesRanking";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GeneralConfigurationService configurationService;

    private ResultActions sendConfiguration(String minimumReviewsJson) throws Exception {
        return mockMvc.perform(put(API)
                .with(user("admin").roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "ivaPorcentaje": 21,
                          "gastosEnvio": 4.99,
                          "envioGratuitoDesde": 50,
                          "registroHabilitado": true%s
                        }
                        """.formatted(minimumReviewsJson)));
    }

    @Test
    @DisplayName("HU23-CP32: la configuración devuelve el mínimo de valoraciones del ranking")
    void theConfigurationShowsTheMinimumReviews() throws Exception {
        GeneralConfiguration configuration = new GeneralConfiguration();
        configuration.setMinimoValoracionesRanking(6);
        when(configurationService.getCurrent()).thenReturn(configuration);

        mockMvc.perform(get(API).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$." + FIELD).value(6));
    }

    @Test
    @DisplayName("HU23-CP33: el administrador puede cambiar el mínimo y llega al servicio")
    void theAdminCanChangeTheMinimum() throws Exception {
        GeneralConfiguration updated = new GeneralConfiguration();
        updated.setMinimoValoracionesRanking(10);
        when(configurationService.update(any())).thenReturn(updated);

        sendConfiguration(", \"" + FIELD + "\": 10")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$." + FIELD).value(10));

        ArgumentCaptor<GeneralConfigurationDTO> sent = ArgumentCaptor.forClass(GeneralConfigurationDTO.class);
        verify(configurationService).update(sent.capture());
        assertThat(sent.getValue().minimoValoracionesRanking()).isEqualTo(10);
    }

    @Test
    @DisplayName("HU23-CP34: el mínimo es opcional: quien no lo envía sigue pudiendo guardar la configuración")
    void theMinimumIsOptional() throws Exception {
        when(configurationService.update(any())).thenReturn(new GeneralConfiguration());

        sendConfiguration("").andExpect(status().isOk());

        ArgumentCaptor<GeneralConfigurationDTO> sent = ArgumentCaptor.forClass(GeneralConfigurationDTO.class);
        verify(configurationService).update(sent.capture());
        assertThat(sent.getValue().minimoValoracionesRanking()).isNull();
    }

    @Test
    @DisplayName("HU23-CP35: un mínimo menor que 1 o mayor que 1000 se rechaza")
    void aMinimumOutsideTheAllowedRangeIsRejected() throws Exception {
        sendConfiguration(", \"" + FIELD + "\": 0")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors." + FIELD).exists());
        sendConfiguration(", \"" + FIELD + "\": -4")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors." + FIELD).exists());
        sendConfiguration(", \"" + FIELD + "\": 1001")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors." + FIELD).exists());

        verify(configurationService, never()).update(any());
    }

    @Test
    @DisplayName("HU23-CP36: los límites 1 y 1000 son válidos")
    void theLimitsOfTheRangeAreAccepted() throws Exception {
        when(configurationService.update(any())).thenReturn(new GeneralConfiguration());

        sendConfiguration(", \"" + FIELD + "\": 1").andExpect(status().isOk());
        sendConfiguration(", \"" + FIELD + "\": 1000").andExpect(status().isOk());
    }
}
