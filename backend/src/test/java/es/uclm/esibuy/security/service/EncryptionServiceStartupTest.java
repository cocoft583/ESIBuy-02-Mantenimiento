package es.uclm.esibuy.security.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class EncryptionServiceStartupTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(EncryptionService.class);
            

    @Test
    void arrancaConUnaClaveValida() {
        contextRunner
                .withPropertyValues("AES_KEY=0123456789abcdef0123456789abcdef")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void noArrancaSiLaClaveEstaVacia() {
        contextRunner
                .withPropertyValues("AES_KEY=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("AES_KEY");
                });
    }

    @Test
    void noArrancaSiLaClaveTieneUnaLongitudIncorrecta() {
        contextRunner
                .withPropertyValues("AES_KEY=corta")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("AES_KEY");
                });
    }
}
