package it.govpay.console.metrics;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.web.filter.RequestContextFilter;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Configurazione delle metriche applicative.
 *
 * <p>Registra {@link TimedAspect}: qualunque metodo di un bean Spring puo'
 * essere misurato (timer con conteggio e distribuzione della durata)
 * annotandolo con {@code @Timed("nome.metrica")}. La metrica risultante e'
 * esposta, insieme a quelle automatiche (HTTP server/client, JVM, datasource,
 * Resilience4j), dall'endpoint di scrape Prometheus sulla porta management.
 */
@Configuration
public class MetricsConfig {

    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

    @Bean
    public FilterRegistrationBean<RequestContextFilter> requestContextFilterRegistration() {
        FilterRegistrationBean<RequestContextFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RequestContextFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 15);
        return registration;
    }
}
