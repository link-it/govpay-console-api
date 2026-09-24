package it.govpay.console.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifica end-to-end della tracciatura di govpay-common (BP-LOG-3) su
 * console-api, che sostituisce il precedente {@code RequestIdFilter} locale.
 * <p>
 * La differenza di comportamento rispetto a quel filtro e' verificata
 * esplicitamente: il transaction id non e' piu' imponibile dal client tramite
 * {@code X-Request-ID}, che ora alimenta il solo correlation id.
 * <p>
 * Si interroga un path non autenticato della public chain: il filtro di
 * tracciatura gira prima della catena di sicurezza, quindi gli header sono
 * presenti a prescindere dall'esito dell'autenticazione.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("Test Tracciatura Transaction ID / Correlation ID")
class TracciaturaIntegrationTest {

    private static final String HEADER_TRANSACTION_ID = "X-Transaction-ID";
    private static final String HEADER_TRANSACTION_ID_LEGACY = "X-Govpay-IdTransazione";
    private static final String HEADER_CORRELATION_ID = "X-Correlation-ID";
    private static final String HEADER_REQUEST_ID = "X-Request-ID";

    private static final String PATH_PUBBLICO = "/auth/methods";

    @LocalServerPort
    private int serverPort;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    @DisplayName("La risposta espone transaction id e correlation id generati")
    void rispostaEsponeIdentificativiGenerati() throws Exception {
        HttpResponse<String> response = get(null, null);

        String transactionId = header(response, HEADER_TRANSACTION_ID);
        String correlationId = header(response, HEADER_CORRELATION_ID);

        assertThat(UUID.fromString(transactionId)).isNotNull();
        assertThat(UUID.fromString(correlationId)).isNotNull();
        assertThat(transactionId).isNotEqualTo(correlationId);

        // header legacy di GovPay 3: i client esistenti continuano a leggerlo
        assertThat(header(response, HEADER_TRANSACTION_ID_LEGACY)).isEqualTo(transactionId);
        assertThat(header(response, HEADER_REQUEST_ID)).isEqualTo(correlationId);
    }

    @Test
    @DisplayName("Il correlation id del chiamante viene riusato")
    void correlationIdDelChiamanteRiusato() throws Exception {
        HttpResponse<String> response = get(HEADER_CORRELATION_ID, "flusso-esterno-1");

        assertThat(header(response, HEADER_CORRELATION_ID)).isEqualTo("flusso-esterno-1");
        assertThat(header(response, HEADER_REQUEST_ID)).isEqualTo("flusso-esterno-1");
    }

    @Test
    @DisplayName("X-Request-ID alimenta il correlation id, non piu' il transaction id")
    void requestIdAlimentaSoloIlCorrelationId() throws Exception {
        HttpResponse<String> response = get(HEADER_REQUEST_ID, "da-gateway");

        assertThat(header(response, HEADER_CORRELATION_ID)).isEqualTo("da-gateway");
        // Cambio di comportamento rispetto al RequestIdFilter rimosso, che
        // riemetteva il valore ricevuto come X-Govpay-IdTransazione.
        assertThat(header(response, HEADER_TRANSACTION_ID_LEGACY)).isNotEqualTo("da-gateway");
    }

    @Test
    @DisplayName("Il transaction id non e' imponibile dal client")
    void transactionIdNonImponibileDalClient() throws Exception {
        HttpResponse<String> response = get(HEADER_TRANSACTION_ID_LEGACY, "imposto-dal-client");

        String transactionId = header(response, HEADER_TRANSACTION_ID);
        assertThat(transactionId).isNotEqualTo("imposto-dal-client");
        assertThat(UUID.fromString(transactionId)).isNotNull();
    }

    @Test
    @DisplayName("Un correlation id con caratteri non ammessi viene scartato")
    void correlationIdNonConformeScartato() throws Exception {
        HttpResponse<String> response = get(HEADER_CORRELATION_ID, "valore con spazi");

        assertThat(header(response, HEADER_CORRELATION_ID)).isNotEqualTo("valore con spazi");
    }

    @Test
    @DisplayName("Ogni richiesta ha un transaction id diverso")
    void transactionIdDiversoPerOgniRichiesta() throws Exception {
        String primo = header(get(null, null), HEADER_TRANSACTION_ID);
        String secondo = header(get(null, null), HEADER_TRANSACTION_ID);

        assertThat(primo).isNotEqualTo(secondo);
    }

    private static String header(HttpResponse<String> response, String nome) {
        String valore = response.headers().firstValue(nome).orElse(null);
        assertThat(valore).as("Header %s nella risposta", nome).isNotBlank();
        return valore;
    }

    private HttpResponse<String> get(String headerNome, String headerValore) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + serverPort + PATH_PUBBLICO))
                .GET();
        if (headerNome != null) {
            builder.header(headerNome, headerValore);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
