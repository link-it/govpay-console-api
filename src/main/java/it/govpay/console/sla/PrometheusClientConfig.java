package it.govpay.console.sla;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class PrometheusClientConfig {

    @Bean
    public RestTemplate prometheusRestTemplate(
            RestTemplateBuilder builder,
            @Value("${app.prometheus.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${app.prometheus.read-timeout-ms:10000}") int readTimeoutMs) {
        return builder
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .readTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
    }
}
