package it.govpay.console.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.annotation.Timed;

/**
 * Verifica end-to-end dell'esposizione delle metriche Prometheus:
 * <ul>
 *   <li>lo scrape {@code GET /actuator/prometheus} risponde sulla porta
 *       management (senza autenticazione) in formato testuale Prometheus,
 *       con il tag comune {@code application};</li>
 *   <li>le richieste HTTP servite dall'API producono
 *       {@code http_server_requests} con bucket di istogramma;</li>
 *   <li>sulla porta applicativa l'actuator non e' mappato (404): le
 *       metriche non sono raggiungibili dai client dell'API;</li>
 *   <li>un metodo interno annotato {@code @Timed} produce il proprio timer
 *       nello scrape (via {@code TimedAspect});</li>
 *   <li>i circuit breaker Resilience4j pubblicano le proprie metriche.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.server.port=0"
        })
@ActiveProfiles("test")
class PrometheusScrapeIntegrationTest {

    @LocalServerPort
    private int serverPort;

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private TimedProbe timedProbe;

    @Autowired
    private ExternalCallMetricsRecorder externalCallMetricsRecorder;

    private final HttpClient http = HttpClient.newHttpClient();

    @TestConfiguration(proxyBeanMethods = false)
    static class TimedProbeConfig {
        @Bean
        TimedProbe timedProbe() {
            return new TimedProbe();
        }
    }

    /** Bean campione: metodo interno misurato con {@code @Timed}. */
    static class TimedProbe {
        @Timed("test.metodo.interno")
        public void esegui() {
            // il corpo e' irrilevante: conta solo la misurazione
        }
    }

    @Test
    void scrapeOnManagementPortReturns200PrometheusFormat() throws Exception {
        HttpResponse<String> response = get(managementPort, "/actuator/prometheus");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElse(""))
                .contains("text/plain");
        assertThat(response.body()).contains("# TYPE jvm_memory_used_bytes gauge");
        assertThat(response.body()).contains("application=\"govpay-console-api\"");
    }

    @Test
    void apiCallProducesHttpServerRequestsWithHistogramBuckets() throws Exception {
        HttpResponse<String> apiResponse = get(serverPort, "/auth/methods");
        assertThat(apiResponse.statusCode()).isEqualTo(200);

        String scrape = get(managementPort, "/actuator/prometheus").body();
        assertThat(scrape).contains("http_server_requests_seconds_bucket");
        assertThat(scrape).contains("uri=\"/auth/methods\"");
    }

    @Test
    void actuatorNotMappedOnApplicationPortReturns404() throws Exception {
        assertThat(get(serverPort, "/actuator/prometheus").statusCode()).isEqualTo(404);
        assertThat(get(serverPort, "/actuator/health").statusCode()).isEqualTo(404);
    }

    @Test
    void healthOnManagementPortReturns200StatusOnly() throws Exception {
        HttpResponse<String> response = get(managementPort, "/actuator/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\"");
        // niente show-details: nessun dettaglio sui componenti all'anonimo
        assertThat(response.body()).doesNotContain("\"components\"");
    }

    @Test
    void timedAnnotatedMethodAppearsInScrape() throws Exception {
        timedProbe.esegui();

        String scrape = get(managementPort, "/actuator/prometheus").body();
        assertThat(scrape).contains("test_metodo_interno_seconds_count");
    }

    @Test
    void resilience4jCircuitBreakerMetricsAppearInScrape() throws Exception {
        // forza la creazione dell'istanza: i breaker annotation-driven
        // nascono alla prima chiamata, non allo startup
        circuitBreakerRegistry.circuitBreaker("stampe");

        String scrape = get(managementPort, "/actuator/prometheus").body();
        assertThat(scrape).contains("resilience4j_circuitbreaker_state");
        assertThat(scrape).contains("name=\"stampe\"");
    }

    @Test
    void apiBreakdownAndExternalServiceMetricsAppearInScrape() throws Exception {
        HttpResponse<String> apiResponse = get(serverPort, "/auth/methods");
        assertThat(apiResponse.statusCode()).isEqualTo(200);
        externalCallMetricsRecorder.record("test-client", "test-operation", () -> {
            // produce una metrica client esportabile da Prometheus
        });

        String scrape = get(managementPort, "/actuator/prometheus").body();
        assertThat(scrape).contains("govpay_api_external_duration_seconds_count");
        assertThat(scrape).contains("govpay_api_internal_duration_seconds_count");
        assertThat(scrape).contains("uri=\"/auth/methods\"");
        assertThat(scrape).contains("govpay_external_service_duration_seconds_count");
        assertThat(scrape).contains("client=\"test-client\"");
        assertThat(scrape).contains("operation=\"test-operation\"");
    }

    private HttpResponse<String> get(int port, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .GET()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
