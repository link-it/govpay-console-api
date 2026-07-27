package it.govpay.console.operazioni;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class OperazioneEsecuzioneClientConfig {

    @Bean
    public RestTemplate operazioniTriggerRestTemplate(
            RestTemplateBuilder builder,
            @Value("${app.operazioni.trigger.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${app.operazioni.trigger.read-timeout-ms:10000}") int readTimeoutMs) {
        return builder
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .readTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
    }
}
