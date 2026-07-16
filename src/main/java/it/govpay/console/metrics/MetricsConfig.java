package it.govpay.console.metrics;

import jakarta.annotation.PostConstruct;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;

import it.govpay.common.metrics.ExternalCallOutcomeRegistry;

/**
 * Configurazione delle metriche applicative.
 *
 * <p>Registra {@link TimedAspect}: qualunque metodo di un bean Spring puo'
 * essere misurato (timer con conteggio e distribuzione della durata)
 * annotandolo con {@code @Timed("nome.metrica")}. La metrica risultante e'
 * esposta, insieme a quelle automatiche (HTTP server/client, JVM, datasource,
 * Resilience4j), dall'endpoint di scrape Prometheus sulla porta management.
 *
 * <p>Il breakdown API interno/esterno e il recorder delle chiamate esterne
 * sono forniti da {@code GovpayMetricsAutoConfiguration} (govpay-common):
 * qui si registra solo la classificazione {@code circuit_open} nel
 * {@link ExternalCallOutcomeRegistry}, dato che common non dipende da
 * Resilience4j.
 */
@Configuration
public class MetricsConfig {

    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

    @PostConstruct
    public void registerCircuitOpenOutcome() {
        ExternalCallOutcomeRegistry.registerCircuitOpen(CallNotPermittedException.class);
    }
}
