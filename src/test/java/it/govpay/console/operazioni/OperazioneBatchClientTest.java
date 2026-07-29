package it.govpay.console.operazioni;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import it.govpay.common.batch.dto.ExecutionsPage;
import it.govpay.common.batch.dto.LastExecutionInfo;
import it.govpay.console.web.ConflictException;
import it.govpay.console.web.NotFoundException;

/**
 * Verifica che il client deserializzi correttamente i DTO di govpay-common
 * e mappi gli errori HTTP del microservizio remoto (govpay-common
 * {@code AbstractBatchController}) sulle eccezioni applicative attese.
 */
class OperazioneBatchClientTest {

    private static final String URL = "http://fake-batch/api/batch";

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private OperazioneBatchClient client;

    @BeforeEach
    void setup() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        client = new OperazioneBatchClient(restTemplate);
    }

    @Test
    void info_deserializzaBatchInfo() {
        server.expect(requestTo(URL + "/info"))
                .andRespond(withSuccess("""
                        {"jobName":"ibanCheckJob","displayName":"Censimento IBAN","description":"desc"}""",
                        MediaType.APPLICATION_JSON));

        var info = client.info(URL);

        assertThat(info.getJobName()).isEqualTo("ibanCheckJob");
        assertThat(info.getDisplayName()).isEqualTo("Censimento IBAN");
        server.verify();
    }

    @Test
    void lastExecution_deserializzaLastExecutionInfo() {
        server.expect(requestTo(URL + "/lastExecution"))
                .andRespond(withSuccess("""
                        {"executionId":42,"status":"COMPLETED","triggerType":"MANUAL"}""",
                        MediaType.APPLICATION_JSON));

        LastExecutionInfo info = client.lastExecution(URL);

        assertThat(info.getExecutionId()).isEqualTo(42L);
        assertThat(info.getStatus()).isEqualTo("COMPLETED");
        assertThat(info.getTriggerType()).isEqualTo("MANUAL");
        server.verify();
    }

    @Test
    void nextExecution_deserializzaNextExecutionInfo() {
        server.expect(requestTo(URL + "/nextExecution"))
                .andRespond(withSuccess("""
                        {"schedulingMode":"scheduler","intervalMillis":7200000}""",
                        MediaType.APPLICATION_JSON));

        var info = client.nextExecution(URL);

        assertThat(info.getSchedulingMode()).isEqualTo("scheduler");
        assertThat(info.getIntervalMillis()).isEqualTo(7200000L);
        server.verify();
    }

    @Test
    void status_deserializzaBatchStatusInfo() {
        server.expect(requestTo(URL + "/status"))
                .andRespond(withSuccess("""
                        {"running":true,"executionId":5,"status":"STARTED"}""", MediaType.APPLICATION_JSON));

        var status = client.status(URL);

        assertThat(status.isRunning()).isTrue();
        assertThat(status.getExecutionId()).isEqualTo(5L);
        server.verify();
    }

    @Test
    void run_successo_nonLanciaEccezioni() {
        server.expect(requestTo(URL + "/run?force=false")).andRespond(withSuccess());

        assertThatCode(() -> client.run(URL, false)).doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void run_409delRemoto_diventaConflictException() {
        server.expect(requestTo(URL + "/run?force=false")).andRespond(withStatus(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> client.run(URL, false)).isInstanceOf(ConflictException.class);
        server.verify();
    }

    @Test
    void run_500delRemoto_diventaOperazioneTriggerNonRaggiungibileException() {
        server.expect(requestTo(URL + "/run?force=false")).andRespond(withServerError());

        assertThatThrownBy(() -> client.run(URL, false))
                .isInstanceOf(OperazioneTriggerNonRaggiungibileException.class);
        server.verify();
    }

    @Test
    void listExecutions_deserializzaExecutionsPage() {
        server.expect(requestTo(URL + "/executions?page=1&limit=10&total=false&stato=FAILED,UNKNOWN"))
                .andRespond(withSuccess("""
                        {"results":[{"executionId":1,"status":"FAILED"}],"page":1,"limit":10,"hasNextPage":false}""",
                        MediaType.APPLICATION_JSON));

        ExecutionsPage page = client.listExecutions(URL, "FAILED,UNKNOWN", null, null, 1, 10, false);

        assertThat(page.getResults()).hasSize(1);
        assertThat(page.getResults().get(0).getExecutionId()).isEqualTo(1L);
        assertThat(page.isHasNextPage()).isFalse();
        server.verify();
    }

    @Test
    void getExecution_deserializzaLastExecutionInfo() {
        server.expect(requestTo(URL + "/executions/7"))
                .andRespond(withSuccess("""
                        {"executionId":7,"status":"COMPLETED"}""", MediaType.APPLICATION_JSON));

        LastExecutionInfo info = client.getExecution(URL, 7L);

        assertThat(info.getExecutionId()).isEqualTo(7L);
        server.verify();
    }

    @Test
    void getExecution_404delRemoto_diventaNotFoundException() {
        server.expect(requestTo(URL + "/executions/999")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.getExecution(URL, 999L)).isInstanceOf(NotFoundException.class);
        server.verify();
    }

    @Test
    void stopExecution_successo_nonLanciaEccezioni() {
        server.expect(requestTo(URL + "/executions/7")).andExpect(method(HttpMethod.DELETE)).andRespond(withSuccess());

        assertThatCode(() -> client.stopExecution(URL, 7L)).doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void stopExecution_409delRemoto_diventaConflictException() {
        server.expect(requestTo(URL + "/executions/7")).andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> client.stopExecution(URL, 7L)).isInstanceOf(ConflictException.class);
        server.verify();
    }

    @Test
    void stopExecution_404delRemoto_diventaNotFoundException() {
        server.expect(requestTo(URL + "/executions/7")).andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.stopExecution(URL, 7L)).isInstanceOf(NotFoundException.class);
        server.verify();
    }
}
