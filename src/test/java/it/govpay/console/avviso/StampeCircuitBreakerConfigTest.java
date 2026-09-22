package it.govpay.console.avviso;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.SocketTimeoutException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

/**
 * Il circuit breaker del client {@code govpay-stampe} deve distinguere il
 * rifiuto di un payload dal guasto del servizio: un 4xx dice che quella
 * specifica pendenza non e' stampabile (es. 422 "Iban obbligatorio in caso di
 * avviso postale"), non che il microservizio sia indisponibile. Senza
 * {@code ignore-exceptions} il default di Resilience4j conta ogni eccezione
 * come guasto, e una manciata di avvisi non stampabili di fila aprirebbe il
 * circuito bloccando la stampa per tutti gli enti.
 *
 * <p>Si verificano i predicati della configurazione effettivamente caricata:
 * l'annotazione {@code @CircuitBreaker} e' AOP e il comportamento nasce dalle
 * properties, non dal codice, quindi una regressione qui sarebbe altrimenti
 * silenziosa.
 */
@SpringBootTest
@ActiveProfiles("test")
class StampeCircuitBreakerConfigTest {

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private CircuitBreakerConfig config() {
        return circuitBreakerRegistry.circuitBreaker("stampe").getCircuitBreakerConfig();
    }

    @Test
    void i4xxNonContanoComeGuastoDelServizio() {
        CircuitBreakerConfig config = config();

        assertThat(config.getIgnoreExceptionPredicate())
                .as("un 422 e' il rifiuto di un payload, non un guasto")
                .accepts(HttpClientErrorException.create(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Unprocessable Entity", null, null, null))
                .as("vale per ogni 4xx, non solo per il 422")
                .accepts(HttpClientErrorException.create(HttpStatus.BAD_REQUEST,
                        "Bad Request", null, null, null));
    }

    @Test
    void i5xxEIFallimentiDiTrasportoRestanoGuasti() {
        CircuitBreakerConfig config = config();

        HttpServerErrorException errore5xx = HttpServerErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", null, null, null);
        ResourceAccessException timeout = new ResourceAccessException(
                "Read timed out", new SocketTimeoutException());

        assertThat(config.getIgnoreExceptionPredicate())
                .as("un 5xx e' un guasto del microservizio")
                .rejects(errore5xx)
                .as("un timeout e' un guasto di trasporto")
                .rejects(timeout);

        assertThat(config.getRecordExceptionPredicate())
                .as("un 5xx apre il circuito")
                .accepts(errore5xx)
                .as("un timeout apre il circuito")
                .accepts(timeout);
    }
}
