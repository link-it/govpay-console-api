package it.govpay.console.operazioni;

import java.time.OffsetDateTime;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import it.govpay.common.batch.dto.BatchInfo;
import it.govpay.common.batch.dto.BatchStatusInfo;
import it.govpay.common.batch.dto.ExecutionsPage;
import it.govpay.common.batch.dto.LastExecutionInfo;
import it.govpay.common.batch.dto.NextExecutionInfo;
import it.govpay.console.web.ConflictException;
import it.govpay.console.web.NotFoundException;

/**
 * Facade verso le REST gia' esposte da ogni microservizio batch
 * (govpay-common {@code AbstractBatchController}, base URL {@code /api/batch}):
 * info descrittiva, ultima/prossima esecuzione, avvio manuale, storico
 * esecuzioni, cancellazione. Deserializza direttamente nei DTO di
 * govpay-common (gia' sul classpath), senza duplicarli.
 */
@Service
public class OperazioneBatchClient {

    private static final Logger log = LoggerFactory.getLogger(OperazioneBatchClient.class);

    private final RestTemplate restTemplate;

    public OperazioneBatchClient(RestTemplate operazioniTriggerRestTemplate) {
        this.restTemplate = operazioniTriggerRestTemplate;
    }

    @CircuitBreaker(name = "operazioni-trigger")
    @Retry(name = "operazioni-trigger")
    public BatchInfo info(String url) {
        return get(url + "/info", BatchInfo.class, url);
    }

    @CircuitBreaker(name = "operazioni-trigger")
    @Retry(name = "operazioni-trigger")
    public LastExecutionInfo lastExecution(String url) {
        return get(url + "/lastExecution", LastExecutionInfo.class, url);
    }

    /**
     * Stato corrente del batch: a differenza di {@link #lastExecution(String)}
     * (che esclude le esecuzioni non terminali) include l'eventuale
     * esecuzione in corso — l'unico modo per rilevare "e' gia' in esecuzione"
     * o per recuperare l'id di un'esecuzione appena avviata.
     */
    @CircuitBreaker(name = "operazioni-trigger")
    @Retry(name = "operazioni-trigger")
    public BatchStatusInfo status(String url) {
        return get(url + "/status", BatchStatusInfo.class, url);
    }

    @CircuitBreaker(name = "operazioni-trigger")
    @Retry(name = "operazioni-trigger")
    public NextExecutionInfo nextExecution(String url) {
        return get(url + "/nextExecution", NextExecutionInfo.class, url);
    }

    @CircuitBreaker(name = "operazioni-trigger")
    @Retry(name = "operazioni-trigger")
    public void run(String url, boolean force) {
        String uri = UriComponentsBuilder.fromUriString(url + "/run")
                .queryParam("force", force)
                .toUriString();
        call(() -> restTemplate.getForEntity(uri, Void.class), url);
    }

    @CircuitBreaker(name = "operazioni-trigger")
    @Retry(name = "operazioni-trigger")
    public ExecutionsPage listExecutions(String url, String statoCsv, OffsetDateTime dataInizioMin,
            OffsetDateTime dataInizioMax, int page, int limit, boolean total) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url + "/executions")
                .queryParam("page", page)
                .queryParam("limit", limit)
                .queryParam("total", total);
        if (statoCsv != null) {
            builder.queryParam("stato", statoCsv);
        }
        if (dataInizioMin != null) {
            builder.queryParam("dataInizioMin", dataInizioMin);
        }
        if (dataInizioMax != null) {
            builder.queryParam("dataInizioMax", dataInizioMax);
        }
        return get(builder.toUriString(), ExecutionsPage.class, url);
    }

    @CircuitBreaker(name = "operazioni-trigger")
    @Retry(name = "operazioni-trigger")
    public LastExecutionInfo getExecution(String url, long executionId) {
        return get(url + "/executions/" + executionId, LastExecutionInfo.class, url);
    }

    @CircuitBreaker(name = "operazioni-trigger")
    @Retry(name = "operazioni-trigger")
    public void stopExecution(String url, long executionId) {
        call(() -> restTemplate.delete(url + "/executions/" + executionId), url);
    }

    private <T> T get(String uri, Class<T> type, String url) {
        return call(() -> restTemplate.getForObject(uri, type), url);
    }

    private <T> T call(Supplier<T> action, String url) {
        try {
            return action.get();
        } catch (CallNotPermittedException e) {
            log.warn("Circuit breaker aperto su {}: {}", url, e.getMessage());
            throw new OperazioneTriggerNonRaggiungibileException(
                    "Microservizio proprietario del job momentaneamente non disponibile (circuit open).", e);
        } catch (HttpClientErrorException.Conflict e) {
            // Il microservizio remoto ha il proprio controllo di concorrenza
            // (AbstractBatchController) ed e' quello autoritativo: puo'
            // rifiutare la richiesta con 409 anche dopo un eventuale
            // pre-check locale (race tra le due verifiche).
            log.info("Il microservizio proprietario del job ha risposto con conflitto ({}): {}", url, e.getMessage());
            throw new ConflictException("Conflitto segnalato dal microservizio proprietario del job.", e);
        } catch (HttpClientErrorException.NotFound e) {
            throw new NotFoundException("Risorsa non trovata sul microservizio proprietario del job.", e);
        } catch (RestClientException e) {
            log.warn("Chiamata verso {} fallita: {}", url, e.getMessage());
            throw new OperazioneTriggerNonRaggiungibileException("Chiamata remota fallita.", e);
        }
    }

    private void call(Runnable action, String url) {
        call(() -> {
            action.run();
            return null;
        }, url);
    }
}
