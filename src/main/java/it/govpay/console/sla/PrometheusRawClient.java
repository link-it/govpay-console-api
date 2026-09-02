package it.govpay.console.sla;

import java.net.URI;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

/**
 * Chiamata HTTP grezza verso Prometheus, isolata in un bean a sé stante
 * apposta perché {@code @CircuitBreaker}/{@code @Retry} sono AOP: se
 * l'eccezione ritentabile ({@code ResourceAccessException}) o
 * {@code CallNotPermittedException} (circuito aperto) venissero catturate
 * *dentro* lo stesso metodo annotato — come nella prima versione di questo
 * client — l'aspect non le vedrebbe mai: {@code CallNotPermittedException}
 * viene sollevata dall'aspect *prima* di invocare il corpo del metodo (quindi
 * un catch interno è codice morto), e un catch interno che converte subito
 * l'eccezione in un tipo custom nasconde a {@code @Retry} l'eccezione
 * originale su cui è configurato a ritentare. Separando la chiamata grezza in
 * un bean diverso, {@link PrometheusQueryClient} la invoca come chiamata
 * *esterna* (attraverso il proxy Spring) e può intercettare l'esito finale
 * (incluso il circuito aperto) nel proprio try/catch, dopo che l'aspect ha
 * fatto il suo lavoro.
 */
@Service
public class PrometheusRawClient {

    private final RestTemplate restTemplate;

    public PrometheusRawClient(RestTemplate prometheusRestTemplate) {
        this.restTemplate = prometheusRestTemplate;
    }

    @CircuitBreaker(name = "prometheus")
    @Retry(name = "prometheus")
    public String query(URI uri) {
        return restTemplate.getForObject(uri, String.class);
    }
}
