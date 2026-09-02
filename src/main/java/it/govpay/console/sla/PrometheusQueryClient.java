package it.govpay.console.sla;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Client verso l'HTTP Query API di Prometheus (istanza, non range):
 * {@code GET /api/v1/query?query=<promql>&time=<epoch>}. Non un client
 * generato da OpenAPI: la superficie usata è un solo endpoint con una
 * risposta poco tipizzabile (il campo {@code value} è una coppia
 * eterogenea {@code [timestamp_number, "value_string"]}), quindi si naviga
 * l'albero JSON invece di deserializzare in DTO.
 *
 * <p>La chiamata HTTP vera e propria è delegata a {@link PrometheusRawClient}
 * (bean separato, con {@code @CircuitBreaker}/{@code @Retry}): qui si
 * intercetta l'esito finale di quella chiamata — incluso il circuito aperto
 * — vedi Javadoc di {@link PrometheusRawClient} per il perché della
 * separazione.
 */
@Service
public class PrometheusQueryClient {

    private static final Logger log = LoggerFactory.getLogger(PrometheusQueryClient.class);

    private final PrometheusRawClient rawClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public PrometheusQueryClient(PrometheusRawClient rawClient,
                                 ObjectMapper objectMapper,
                                 @Value("${govpay.prometheus.url}") String baseUrl) {
        this.rawClient = rawClient;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
    }

    /**
     * Esegue una instant query. {@code promql} contiene caratteri (`{`, `}`,
     * `"`) che {@code RestTemplate.getForObject(String, ...)} interpreterebbe
     * come placeholder di URI template: si costruisce quindi un {@link URI}
     * già codificato ({@code UriComponentsBuilder...encode().toUri()}) e si
     * usa l'overload che lo accetta direttamente, bypassando l'espansione
     * template.
     *
     * @return vuoto se il vettore risultato è vuoto (nessun dato nel periodo)
     *         o se Prometheus risponde {@code NaN} (division by zero lato
     *         PromQL, es. {@code totale=0}); altrimenti il valore scalare.
     * @throws PrometheusNonRaggiungibileException se la chiamata fallisce
     *         (rete, HTTP, circuito aperto) o la risposta non ha la forma
     *         attesa (JSON malformato, {@code status != success},
     *         {@code data.result} assente/non-array, {@code value} assente o
     *         non numerico) — una risposta strutturalmente inattesa non è
     *         "nessun dato", è un errore da segnalare come tale.
     */
    public Optional<Double> query(String promql, Instant at) {
        URI uri = UriComponentsBuilder.fromUriString(baseUrl + "/api/v1/query")
                .queryParam("query", promql)
                .queryParam("time", at.getEpochSecond())
                .build()
                .encode()
                .toUri();
        String body;
        try {
            body = rawClient.query(uri);
        } catch (CallNotPermittedException e) {
            log.warn("Circuit breaker aperto su Prometheus: {}", e.getMessage());
            throw new PrometheusNonRaggiungibileException(
                    "Prometheus momentaneamente non disponibile (circuit open).", e);
        } catch (RestClientException e) {
            log.warn("Chiamata a Prometheus fallita: {}", e.getMessage());
            throw new PrometheusNonRaggiungibileException("Chiamata a Prometheus fallita.", e);
        }
        return parse(body);
    }

    private Optional<Double> parse(String body) {
        // Un 200/204 senza contenuto fa tornare null da getForObject: readTree(null)
        // lancerebbe IllegalArgumentException, non una JacksonException, sfuggendo
        // al catch sotto e finendo nel generic handler (500) invece del 502 dichiarato.
        if (body == null || body.isBlank()) {
            throw new PrometheusNonRaggiungibileException("Risposta Prometheus vuota.", null);
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (JacksonException e) {
            throw new PrometheusNonRaggiungibileException("Risposta Prometheus non è JSON valido.", e);
        }

        String status = root.path("status").asString();
        if (!"success".equals(status)) {
            throw new PrometheusNonRaggiungibileException(
                    "Risposta Prometheus non 'success' (status=" + status + ").", null);
        }

        // result assente/non-array = risposta malformata, non "nessun dato":
        // "nessun dato" è result presente E vuoto ([]), un caso diverso e
        // legittimo gestito sotto.
        JsonNode result = root.path("data").path("result");
        if (!result.isArray()) {
            throw new PrometheusNonRaggiungibileException(
                    "Risposta Prometheus priva del campo atteso 'data.result' (array).", null);
        }
        if (result.isEmpty()) {
            return Optional.empty();
        }

        JsonNode value = result.get(0).path("value");
        if (!value.isArray() || value.size() < 2 || !value.get(1).isString()) {
            throw new PrometheusNonRaggiungibileException(
                    "Risposta Prometheus con formato 'value' inatteso.", null);
        }
        String raw = value.get(1).asString();
        double parsed;
        try {
            parsed = Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            throw new PrometheusNonRaggiungibileException(
                    "Risposta Prometheus con valore non numerico: '" + raw + "'.", e);
        }
        return Double.isNaN(parsed) ? Optional.empty() : Optional.of(parsed);
    }
}
