package it.govpay.console.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import it.govpay.common.metrics.ExternalCallMetricsRecorder;
import it.govpay.common.metrics.GovpayMetricsAutoConfiguration;

/**
 * Verifica che {@link ExternalCallMetricsRecorder} esista sempre come bean
 * (dipendenza obbligatoria di {@code AvvisoService}/{@code RicevutaService}),
 * sia quando le metriche di govpay-common sono disattivate (default: bean
 * no-op) sia quando sono attive (bean reale, nessuna ambiguita').
 */
class FallbackMetricsAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    GovpayMetricsAutoConfiguration.class, FallbackMetricsAutoConfiguration.class))
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new);

    @Test
    void usesNoopRecorderWhenMetricsDisabledByDefault() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(ExternalCallMetricsRecorder.class);
            assertThat(context.getBean(ExternalCallMetricsRecorder.class))
                    .isInstanceOf(NoopExternalCallMetricsRecorder.class);
        });
    }

    @Test
    void usesRealRecorderWhenMetricsEnabledNoAmbiguity() {
        runner.withPropertyValues("govpay.metrics.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(ExternalCallMetricsRecorder.class);
                    assertThat(context.getBean(ExternalCallMetricsRecorder.class))
                            .isNotInstanceOf(NoopExternalCallMetricsRecorder.class);
                });
    }
}
