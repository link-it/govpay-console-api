package it.govpay.console.sla;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import tools.jackson.databind.json.JsonMapper;

/**
 * Verifica la navigazione della risposta di {@code /api/v1/query}: il campo
 * {@code value} è una coppia eterogenea {@code [timestamp, "string"]}, il
 * PromQL nel query param contiene {@code {}}/{@code "} che un
 * {@code RestTemplate.getForObject(String, ...)} interpreterebbe come
 * placeholder di URI template — vedi Javadoc di {@link PrometheusQueryClient}.
 */
class PrometheusQueryClientTest {

    private static final String PROMQL = "sum(increase(x[86400s]))";

    private MockRestServiceServer server;
    private PrometheusQueryClient client;

    @BeforeEach
    void setup() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        PrometheusRawClient rawClient = new PrometheusRawClient(restTemplate);
        client = new PrometheusQueryClient(rawClient, JsonMapper.shared(), "http://fake-prometheus");
    }

    @Test
    void query_conRisultatoParsaIlValoreScalare() {
        server.expect(requestTo(startsWith("http://fake-prometheus/api/v1/query")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("query", encoded(PROMQL)))
                .andExpect(queryParam("time", "1000"))
                .andRespond(withSuccess("""
                        {"status":"success","data":{"resultType":"vector","result":[
                        {"metric":{},"value":[1000,"42.5"]}]}}""", MediaType.APPLICATION_JSON));

        Optional<Double> result = client.query(PROMQL, Instant.ofEpochSecond(1000));

        assertThat(result).contains(42.5);
        server.verify();
    }

    @Test
    void query_conVettoreVuotoRitornaEmpty() {
        server.expect(requestTo(startsWith("http://fake-prometheus/api/v1/query")))
                .andRespond(withSuccess("""
                        {"status":"success","data":{"resultType":"vector","result":[]}}""",
                        MediaType.APPLICATION_JSON));

        Optional<Double> result = client.query(PROMQL, Instant.ofEpochSecond(1000));

        assertThat(result).isEmpty();
    }

    /** {@code 0/0} lato PromQL produce {@code NaN}: equivalente a "nessun dato", non un valore. */
    @Test
    void query_conNaNRitornaEmpty() {
        server.expect(requestTo(startsWith("http://fake-prometheus/api/v1/query")))
                .andRespond(withSuccess("""
                        {"status":"success","data":{"resultType":"vector","result":[
                        {"metric":{},"value":[1000,"NaN"]}]}}""", MediaType.APPLICATION_JSON));

        Optional<Double> result = client.query(PROMQL, Instant.ofEpochSecond(1000));

        assertThat(result).isEmpty();
    }

    @Test
    void query_conStatusErrorLanciaEccezione() {
        server.expect(requestTo(startsWith("http://fake-prometheus/api/v1/query")))
                .andRespond(withSuccess("""
                        {"status":"error","errorType":"bad_data","error":"parse error"}""",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.query(PROMQL, Instant.ofEpochSecond(1000)))
                .isInstanceOf(PrometheusNonRaggiungibileException.class);
    }

    @Test
    void query_conJsonMalformatoLanciaEccezione() {
        server.expect(requestTo(startsWith("http://fake-prometheus/api/v1/query")))
                .andRespond(withSuccess("{questo non e' json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.query(PROMQL, Instant.ofEpochSecond(1000)))
                .isInstanceOf(PrometheusNonRaggiungibileException.class);
    }

    /**
     * Un 200/204 senza contenuto fa tornare {@code null} da
     * {@code RestTemplate.getForObject}: {@code readTree(null)} lancerebbe
     * {@code IllegalArgumentException}, non una {@code JacksonException} —
     * sfuggirebbe al catch dedicato e finirebbe nel generic handler (500)
     * invece del 502 dichiarato.
     */
    @Test
    void query_conRispostaVuotaLanciaEccezione() {
        server.expect(requestTo(startsWith("http://fake-prometheus/api/v1/query")))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        assertThatThrownBy(() -> client.query(PROMQL, Instant.ofEpochSecond(1000)))
                .isInstanceOf(PrometheusNonRaggiungibileException.class);
    }

    /**
     * {@code status=success} ma senza {@code data.result}: risposta malformata
     * (formato Prometheus inatteso), non "nessun dato" — "nessun dato" è
     * {@code data.result: []}, un caso diverso gestito da
     * {@link #query_conVettoreVuotoRitornaEmpty()}.
     */
    @Test
    void query_conResultAssenteLanciaEccezione() {
        server.expect(requestTo(startsWith("http://fake-prometheus/api/v1/query")))
                .andRespond(withSuccess("""
                        {"status":"success","data":{"resultType":"vector"}}""",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.query(PROMQL, Instant.ofEpochSecond(1000)))
                .isInstanceOf(PrometheusNonRaggiungibileException.class);
    }

    @Test
    void query_conDataAssenteLanciaEccezione() {
        server.expect(requestTo(startsWith("http://fake-prometheus/api/v1/query")))
                .andRespond(withSuccess("""
                        {"status":"success"}""", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.query(PROMQL, Instant.ofEpochSecond(1000)))
                .isInstanceOf(PrometheusNonRaggiungibileException.class);
    }

    @Test
    void query_conValueAssenteLanciaEccezione() {
        server.expect(requestTo(startsWith("http://fake-prometheus/api/v1/query")))
                .andRespond(withSuccess("""
                        {"status":"success","data":{"resultType":"vector","result":[
                        {"metric":{}}]}}""", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.query(PROMQL, Instant.ofEpochSecond(1000)))
                .isInstanceOf(PrometheusNonRaggiungibileException.class);
    }

    @Test
    void query_conValueTroncatoLanciaEccezione() {
        server.expect(requestTo(startsWith("http://fake-prometheus/api/v1/query")))
                .andRespond(withSuccess("""
                        {"status":"success","data":{"resultType":"vector","result":[
                        {"metric":{},"value":[1000]}]}}""", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.query(PROMQL, Instant.ofEpochSecond(1000)))
                .isInstanceOf(PrometheusNonRaggiungibileException.class);
    }

    @Test
    void query_conValoreNonNumericoLanciaEccezione() {
        server.expect(requestTo(startsWith("http://fake-prometheus/api/v1/query")))
                .andRespond(withSuccess("""
                        {"status":"success","data":{"resultType":"vector","result":[
                        {"metric":{},"value":[1000,"non-un-numero"]}]}}""", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.query(PROMQL, Instant.ofEpochSecond(1000)))
                .isInstanceOf(PrometheusNonRaggiungibileException.class);
    }

    @Test
    void query_conErroreHttpLanciaEccezione() {
        server.expect(requestTo(startsWith("http://fake-prometheus/api/v1/query")))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.query(PROMQL, Instant.ofEpochSecond(1000)))
                .isInstanceOf(PrometheusNonRaggiungibileException.class);
    }

    @Test
    void query_promqlConCaratteriSpecialiViaggiaCodificatoCorrettamente() {
        // {, }, " sono caratteri reali di un filtro PromQL: verifica che la query
        // arrivi intatta e correttamente percent-encoded (non spezzata/troncata
        // dal parsing degli URI template, vedi Javadoc del client).
        String promql = "sum(increase(govpay_pa_method_seconds_bucket{metodo=\"paGetPayment\",le=\"2.0\"}[86400s]))";
        server.expect(requestTo(startsWith("http://fake-prometheus/api/v1/query")))
                .andExpect(queryParam("query", encoded(promql)))
                .andRespond(withSuccess("""
                        {"status":"success","data":{"resultType":"vector","result":[
                        {"metric":{},"value":[1000,"7"]}]}}""", MediaType.APPLICATION_JSON));

        Optional<Double> result = client.query(promql, Instant.ofEpochSecond(1000));

        assertThat(result).contains(7.0);
    }

    /**
     * Verifica la navigazione della risposta di {@code /api/v1/query_range}
     * (usata da {@link SlaService#calcolaSerieStorica}): {@code values} è un
     * array di coppie {@code [timestamp, "string"]}, non un singolo
     * {@code value} come l'instant query.
     */
    @Test
    void queryRange_conRisultatoParsaTuttiIPunti() {
        server.expect(requestTo(startsWith("http://fake-prometheus/api/v1/query_range")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("query", encoded(PROMQL)))
                .andExpect(queryParam("start", "1000"))
                .andExpect(queryParam("end", "1600"))
                .andExpect(queryParam("step", "600"))
                .andRespond(withSuccess("""
                        {"status":"success","data":{"resultType":"matrix","result":[
                        {"metric":{},"values":[[1000,"1.0"],[1600,"2.5"]]}]}}""", MediaType.APPLICATION_JSON));

        Map<Instant, Double> result = client.queryRange(PROMQL, Instant.ofEpochSecond(1000), Instant.ofEpochSecond(1600), 600);

        assertThat(result).containsExactly(
                Map.entry(Instant.ofEpochSecond(1000), 1.0),
                Map.entry(Instant.ofEpochSecond(1600), 2.5));
        server.verify();
    }

    @Test
    void queryRange_conRisultatoVuotoRitornaMappaVuota() {
        server.expect(requestTo(startsWith("http://fake-prometheus/api/v1/query_range")))
                .andRespond(withSuccess("""
                        {"status":"success","data":{"resultType":"matrix","result":[]}}""",
                        MediaType.APPLICATION_JSON));

        Map<Instant, Double> result = client.queryRange(PROMQL, Instant.ofEpochSecond(1000), Instant.ofEpochSecond(1600), 600);

        assertThat(result).isEmpty();
    }

    /** {@code 0/0} lato PromQL produce {@code NaN} per quel punto: filtrato, non un'entry con valore NaN. */
    @Test
    void queryRange_conNaNInUnPuntoLoOmette() {
        server.expect(requestTo(startsWith("http://fake-prometheus/api/v1/query_range")))
                .andRespond(withSuccess("""
                        {"status":"success","data":{"resultType":"matrix","result":[
                        {"metric":{},"values":[[1000,"NaN"],[1600,"3.0"]]}]}}""", MediaType.APPLICATION_JSON));

        Map<Instant, Double> result = client.queryRange(PROMQL, Instant.ofEpochSecond(1000), Instant.ofEpochSecond(1600), 600);

        assertThat(result).containsOnly(Map.entry(Instant.ofEpochSecond(1600), 3.0));
    }

    @Test
    void queryRange_conValuesAssenteLanciaEccezione() {
        server.expect(requestTo(startsWith("http://fake-prometheus/api/v1/query_range")))
                .andRespond(withSuccess("""
                        {"status":"success","data":{"resultType":"matrix","result":[
                        {"metric":{}}]}}""", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.queryRange(PROMQL, Instant.ofEpochSecond(1000), Instant.ofEpochSecond(1600), 600))
                .isInstanceOf(PrometheusNonRaggiungibileException.class);
    }

    @Test
    void queryRange_conCoppiaTroncataLanciaEccezione() {
        server.expect(requestTo(startsWith("http://fake-prometheus/api/v1/query_range")))
                .andRespond(withSuccess("""
                        {"status":"success","data":{"resultType":"matrix","result":[
                        {"metric":{},"values":[[1000]]}]}}""", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.queryRange(PROMQL, Instant.ofEpochSecond(1000), Instant.ofEpochSecond(1600), 600))
                .isInstanceOf(PrometheusNonRaggiungibileException.class);
    }

    /**
     * {@code JsonNode.asLong()} su un nodo non numerico/mancante ritorna
     * silenziosamente 0 (epoch 1970) invece di fallire: senza un controllo
     * esplicito il punto finirebbe sotto una chiave che nessuna istante
     * attesa interroga, sparendo dalla serie invece di far fallire la
     * risposta — vedi Javadoc di {@link PrometheusQueryClient#queryRange}.
     */
    @Test
    void queryRange_conTimestampNonNumericoLanciaEccezione() {
        server.expect(requestTo(startsWith("http://fake-prometheus/api/v1/query_range")))
                .andRespond(withSuccess("""
                        {"status":"success","data":{"resultType":"matrix","result":[
                        {"metric":{},"values":[["non-un-timestamp","5.0"]]}]}}""", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.queryRange(PROMQL, Instant.ofEpochSecond(1000), Instant.ofEpochSecond(1600), 600))
                .isInstanceOf(PrometheusNonRaggiungibileException.class);
    }

    @Test
    void queryRange_conTimestampAssenteLanciaEccezione() {
        server.expect(requestTo(startsWith("http://fake-prometheus/api/v1/query_range")))
                .andRespond(withSuccess("""
                        {"status":"success","data":{"resultType":"matrix","result":[
                        {"metric":{},"values":[[null,"5.0"]]}]}}""", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.queryRange(PROMQL, Instant.ofEpochSecond(1000), Instant.ofEpochSecond(1600), 600))
                .isInstanceOf(PrometheusNonRaggiungibileException.class);
    }

    @Test
    void queryRange_conValoreNonNumericoLanciaEccezione() {
        server.expect(requestTo(startsWith("http://fake-prometheus/api/v1/query_range")))
                .andRespond(withSuccess("""
                        {"status":"success","data":{"resultType":"matrix","result":[
                        {"metric":{},"values":[[1000,"non-un-numero"]]}]}}""", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.queryRange(PROMQL, Instant.ofEpochSecond(1000), Instant.ofEpochSecond(1600), 600))
                .isInstanceOf(PrometheusNonRaggiungibileException.class);
    }

    @Test
    void queryRange_conStatusErrorLanciaEccezione() {
        server.expect(requestTo(startsWith("http://fake-prometheus/api/v1/query_range")))
                .andRespond(withSuccess("""
                        {"status":"error","errorType":"bad_data","error":"parse error"}""",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.queryRange(PROMQL, Instant.ofEpochSecond(1000), Instant.ofEpochSecond(1600), 600))
                .isInstanceOf(PrometheusNonRaggiungibileException.class);
    }

    /**
     * Il circuito aperto va intercettato nel wrapper: {@code @CircuitBreaker}
     * solleva {@link CallNotPermittedException} *prima* di invocare
     * {@link PrometheusRawClient#query}, quindi non è {@link PrometheusRawClient}
     * a poterla vedere (l'aspect non è nemmeno attivo qui, essendo un test
     * plain senza contesto Spring: si simula il circuito aperto mockando
     * direttamente {@link PrometheusRawClient}, esattamente il punto in cui la
     * eccezione dell'aspect emergerebbe verso {@link PrometheusQueryClient}).
     */
    @Test
    void query_conCircuitoApertoLanciaEccezioneConvertita() {
        PrometheusRawClient rawClient = mock(PrometheusRawClient.class);
        CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("prometheus-test");
        when(rawClient.query(any())).thenThrow(
                CallNotPermittedException.createCallNotPermittedException(circuitBreaker));
        PrometheusQueryClient clientConMock =
                new PrometheusQueryClient(rawClient, JsonMapper.shared(), "http://fake-prometheus");

        assertThatThrownBy(() -> clientConMock.query(PROMQL, Instant.ofEpochSecond(1000)))
                .isInstanceOf(PrometheusNonRaggiungibileException.class);
    }

    /**
     * {@link PrometheusRawClient} non deve convertire {@code ResourceAccessException}
     * (o qualunque {@code RestClientException}): deve attraversarla intatta,
     * altrimenti {@code @Retry} (configurato sulla sola {@code ResourceAccessException})
     * non la vedrebbe mai e non ritenterebbe.
     */
    @Test
    void rawClient_nonConverteLeEccezioniRestClient() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer rawServer = MockRestServiceServer.createServer(restTemplate);
        PrometheusRawClient rawClient = new PrometheusRawClient(restTemplate);
        rawServer.expect(requestTo(startsWith("http://fake-prometheus")))
                .andRespond(withServerError());

        assertThatThrownBy(() -> rawClient.query(java.net.URI.create("http://fake-prometheus/api/v1/query")))
                .isInstanceOf(org.springframework.web.client.RestClientException.class)
                .isNotInstanceOf(PrometheusNonRaggiungibileException.class);
    }

    /**
     * {@link org.springframework.test.web.client.match.MockRestRequestMatchers#queryParam}
     * confronta il valore grezzo (ancora percent-encoded) presente sulla
     * richiesta, non lo decodifica: l'atteso va quindi codificato con lo
     * stesso algoritmo usato da {@code UriComponentsBuilder} per i valori di
     * query param.
     */
    private static String encoded(String value) {
        return UriUtils.encodeQueryParam(value, StandardCharsets.UTF_8);
    }
}
