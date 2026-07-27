package it.govpay.console.operazioni;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import it.govpay.console.web.ConflictException;

/**
 * Verifica la mappatura degli errori del client verso l'endpoint remoto di
 * trigger (govpay-common {@code AbstractBatchController}): un 409 e' un
 * conflitto di concorrenza gia' rilevato lato remoto (race col pre-check
 * locale su BATCH_*), non un guasto del gateway.
 */
class OperazioneEsecuzioneClientTest {

    private static final String TRIGGER_URL = "http://fake-batch/api/batch";

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private OperazioneEsecuzioneClient client;

    @BeforeEach
    void setup() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        client = new OperazioneEsecuzioneClient(restTemplate);
    }

    @Test
    void avviaJob_successo_nonLanciaEccezioni() {
        server.expect(requestTo(TRIGGER_URL + "/run?force=false")).andRespond(withSuccess());

        assertThatCode(() -> client.avviaJob(TRIGGER_URL, false)).doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void avviaJob_409delRemoto_diventaConflictException() {
        server.expect(requestTo(TRIGGER_URL + "/run?force=false")).andRespond(withStatus(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> client.avviaJob(TRIGGER_URL, false))
                .isInstanceOf(ConflictException.class);
        server.verify();
    }

    @Test
    void avviaJob_500delRemoto_diventaOperazioneTriggerNonRaggiungibileException() {
        server.expect(requestTo(TRIGGER_URL + "/run?force=false")).andRespond(withServerError());

        assertThatThrownBy(() -> client.avviaJob(TRIGGER_URL, false))
                .isInstanceOf(OperazioneTriggerNonRaggiungibileException.class);
        server.verify();
    }
}
