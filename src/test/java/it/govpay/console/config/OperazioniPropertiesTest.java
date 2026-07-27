package it.govpay.console.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * frequenzaSchedulata e' tipizzata Duration (non String) proprio perche' il
 * binder di {@code @ConfigurationProperties} valida/converte alla creazione
 * del bean: un valore malformato deve far fallire l'avvio dell'applicazione,
 * non una futura GET /operazioni a runtime (Duration.parse non e' piu'
 * chiamato da OperazioneMapper).
 */
class OperazioniPropertiesTest {

    @EnableConfigurationProperties(OperazioniProperties.class)
    static class TestConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void frequenzaSchedulataValida_vieneConvertitaInDuration() {
        runner.withPropertyValues(
                "govpay.operazioni.catalogo[0].id=X",
                "govpay.operazioni.catalogo[0].nome=X",
                "govpay.operazioni.catalogo[0].frequenzaSchedulata=PT2H")
                .run(context -> {
                    OperazioniProperties properties = context.getBean(OperazioniProperties.class);
                    assertThat(properties.getCatalogo()).hasSize(1);
                    assertThat(properties.getCatalogo().get(0).getFrequenzaSchedulata())
                            .isEqualTo(Duration.ofHours(2));
                });
    }

    @Test
    void frequenzaSchedulataAssente_restaNulla() {
        runner.withPropertyValues(
                "govpay.operazioni.catalogo[0].id=X",
                "govpay.operazioni.catalogo[0].nome=X")
                .run(context -> {
                    OperazioniProperties properties = context.getBean(OperazioniProperties.class);
                    assertThat(properties.getCatalogo().get(0).getFrequenzaSchedulata()).isNull();
                });
    }

    @Test
    void frequenzaSchedulataMalformata_fallisceAllAvvio() {
        runner.withPropertyValues(
                "govpay.operazioni.catalogo[0].id=X",
                "govpay.operazioni.catalogo[0].nome=X",
                "govpay.operazioni.catalogo[0].frequenzaSchedulata=non-una-durata")
                .run(context -> assertThat(context).hasFailed());
    }
}
