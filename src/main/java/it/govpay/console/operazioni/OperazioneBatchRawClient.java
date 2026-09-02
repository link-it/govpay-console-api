package it.govpay.console.operazioni;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

/**
 * Chiamata HTTP grezza verso il microservizio batch, isolata in un bean a sé
 * perché {@code @CircuitBreaker}/{@code @Retry} sono AOP — vedi Javadoc di
 * {@code it.govpay.console.sla.PrometheusRawClient} per il ragionamento
 * completo.
 *
 * <p>{@link #run}/{@link #delete} non hanno {@code @Retry}, a differenza di
 * {@link #get}: sono comandi con effetti collaterali (avvio/cancellazione di
 * un'esecuzione), non letture. Una perdita di connessione dopo che il
 * microservizio remoto ha già accettato il comando farebbe ripetere il
 * comando stesso al retry — non c'è garanzia di idempotenza lato client per
 * queste due chiamate. Il circuit breaker resta comunque utile (evita di
 * insistere su un microservizio già rilevato come non raggiungibile).
 */
@Service
public class OperazioneBatchRawClient {

    private final RestTemplate restTemplate;

    public OperazioneBatchRawClient(RestTemplate operazioniTriggerRestTemplate) {
        this.restTemplate = operazioniTriggerRestTemplate;
    }

    @CircuitBreaker(name = "operazioni-trigger")
    @Retry(name = "operazioni-trigger")
    public <T> T get(String uri, Class<T> type) {
        return restTemplate.getForObject(uri, type);
    }

    @CircuitBreaker(name = "operazioni-trigger")
    public void run(String uri) {
        restTemplate.getForEntity(uri, Void.class);
    }

    @CircuitBreaker(name = "operazioni-trigger")
    public void delete(String uri) {
        restTemplate.delete(uri);
    }
}
