package it.govpay.console.eventi;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

/**
 * Chiamata HTTP grezza verso GDE, isolata in un bean a sé perché
 * {@code @CircuitBreaker}/{@code @Retry} sono AOP: se l'eccezione ritentabile
 * o {@code CallNotPermittedException} (circuito aperto) venissero catturate
 * *dentro* lo stesso metodo annotato, l'aspect non le vedrebbe mai — vedi
 * Javadoc di {@code it.govpay.console.sla.PrometheusRawClient} per il
 * ragionamento completo, stesso pattern qui. Il {@link RestTemplate} è
 * passato per chiamata (non iniettato): a differenza di Prometheus/stampe,
 * quello di GDE è risolto dinamicamente da {@code ConfigurazioneService} sul
 * connettore configurato, non è fisso.
 */
@Service
public class GdeRawClient {

    @CircuitBreaker(name = "gde")
    @Retry(name = "gde")
    public <T> T get(RestTemplate restTemplate, String uri, Class<T> type) {
        return restTemplate.getForObject(uri, type);
    }
}
