package it.govpay.console.operazioni;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

/**
 * Facade verso l'endpoint di avvio manuale gia' esposto da ogni
 * microservizio batch (govpay-common {@code AbstractBatchController},
 * {@code GET /api/batch/run?force=}). Fire-and-forget lato remoto: la
 * risposta non porta l'id della nuova esecuzione (creata in background),
 * per questo il chiamante deve ricavarla leggendo {@code BatchExecutionReader}.
 */
@Service
public class OperazioneEsecuzioneClient {

    private static final Logger log = LoggerFactory.getLogger(OperazioneEsecuzioneClient.class);

    private final RestTemplate restTemplate;

    public OperazioneEsecuzioneClient(RestTemplate operazioniTriggerRestTemplate) {
        this.restTemplate = operazioniTriggerRestTemplate;
    }

    @CircuitBreaker(name = "operazioni-trigger")
    @Retry(name = "operazioni-trigger")
    public void avviaJob(String triggerUrl, boolean force) {
        String url = UriComponentsBuilder.fromUriString(triggerUrl + "/run")
                .queryParam("force", force)
                .toUriString();
        try {
            restTemplate.getForEntity(url, Void.class);
        } catch (CallNotPermittedException e) {
            log.warn("Circuit breaker aperto sul trigger remoto ({}): {}", url, e.getMessage());
            throw new OperazioneTriggerNonRaggiungibileException(
                    "Microservizio proprietario del job momentaneamente non disponibile (circuit open).", e);
        } catch (RestClientException e) {
            log.warn("Chiamata di avvio remoto fallita ({}): {}", url, e.getMessage());
            throw new OperazioneTriggerNonRaggiungibileException(
                    "Chiamata di avvio remoto fallita.", e);
        }
    }
}
