package it.govpay.console.avviso;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import it.govpay.stampe.client.ApiClient;
import it.govpay.stampe.client.api.PaymentNoticeApi;

@Configuration
public class StampeConfig {

    @Bean
    public RestTemplate stampeRestTemplate(
            RestTemplateBuilder builder,
            @Value("${app.stampe.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${app.stampe.read-timeout-ms:30000}") int readTimeoutMs) {
        return builder
                .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
    }

    @Bean
    public ApiClient stampeApiClient(RestTemplate stampeRestTemplate,
                                     @Value("${app.stampe.base-url:}") String baseUrl) {
        ApiClient client = new ApiClient(stampeRestTemplate);
        if (StringUtils.hasText(baseUrl)) {
            client.setBasePath(baseUrl);
        }
        return client;
    }

    @Bean
    public PaymentNoticeApi paymentNoticeApi(ApiClient stampeApiClient) {
        return new PaymentNoticeApi(stampeApiClient);
    }
}
