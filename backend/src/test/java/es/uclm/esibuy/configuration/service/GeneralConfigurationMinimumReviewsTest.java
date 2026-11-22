package es.uclm.esibuy.configuration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import es.uclm.esibuy.configuration.dto.GeneralConfigurationDTO;
import es.uclm.esibuy.configuration.model.GeneralConfiguration;
import es.uclm.esibuy.configuration.repository.GeneralConfigurationRepository;

@ExtendWith(MockitoExtension.class)
class GeneralConfigurationMinimumReviewsTest {

    @Mock
    private GeneralConfigurationRepository configurationRepository;

    private GeneralConfigurationService configurationService;

    @BeforeEach
    void setUp() {
        configurationService = new GeneralConfigurationService(configurationRepository);
    }

    private GeneralConfigurationDTO request(Integer minimumReviews) {
        return new GeneralConfigurationDTO(
                new BigDecimal("21.00"), new BigDecimal("4.99"), new BigDecimal("50.00"), true, minimumReviews);
    }

    private void stubExisting(GeneralConfiguration existing) {
        when(configurationRepository.findById(GeneralConfiguration.DOCUMENT_ID)).thenReturn(Optional.of(existing));
        when(configurationRepository.save(any(GeneralConfiguration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("HU23-CP28: el mínimo de valoraciones del ranking es 3 por defecto")
    void theMinimumReviewsAreThreeByDefault() {
        assertThat(new GeneralConfiguration().getMinimoValoracionesRanking()).isEqualTo(3);
        assertThat(new GeneralConfiguration(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, true)
                .getMinimoValoracionesRanking()).isEqualTo(GeneralConfiguration.DEFAULT_MINIMO_VALORACIONES_RANKING);
    }

    @Test
    @DisplayName("HU23-CP29: el servicio devuelve el mínimo configurado, y el de por defecto si todavía no hay configuración")
    void theServiceReturnsTheConfiguredMinimumOrTheDefault() {
        GeneralConfiguration configured = new GeneralConfiguration();
        configured.setMinimoValoracionesRanking(7);
        when(configurationRepository.findById(GeneralConfiguration.DOCUMENT_ID))
                .thenReturn(Optional.of(configured))
                .thenReturn(Optional.empty());
        when(configurationRepository.save(any(GeneralConfiguration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(configurationService.getMinimumReviewsForRanking()).isEqualTo(7);
        assertThat(configurationService.getMinimumReviewsForRanking()).isEqualTo(3);
    }

    @Test
    @DisplayName("HU23-CP30: actualizar con un mínimo lo cambia y lo guarda")
    void updatingWithAMinimumChangesAndSavesIt() {
        stubExisting(new GeneralConfiguration());

        GeneralConfiguration updated = configurationService.update(request(10));

        assertThat(updated.getMinimoValoracionesRanking()).isEqualTo(10);
        ArgumentCaptor<GeneralConfiguration> saved = ArgumentCaptor.forClass(GeneralConfiguration.class);
        verify(configurationRepository).save(saved.capture());
        assertThat(saved.getValue().getMinimoValoracionesRanking()).isEqualTo(10);
    }

    @Test
    @DisplayName("HU23-CP31: actualizar sin enviar el mínimo deja el que ya había")
    void updatingWithoutAMinimumKeepsTheCurrentOne() {
        GeneralConfiguration existing = new GeneralConfiguration();
        existing.setMinimoValoracionesRanking(8);
        stubExisting(existing);

        GeneralConfiguration updated = configurationService.update(request(null));

        assertThat(updated.getMinimoValoracionesRanking()).isEqualTo(8);
    }
}
