package it.govpay.console.operazioni;

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
 * Il circuit breaker verso i microservizi batch non deve contare come guasto
 * cio' che {@link OperazioneBatchClient} traduce in un esito di business: il
 * 409 (job gia' in esecuzione) e il 404 (job sconosciuto) sono richieste
 * andate a buon fine con esito negativo, non segnali di indisponibilita'.
 * Senza {@code ignore-exceptions}, con {@code minimum-number-of-calls=5} su
 * una finestra di 10, cinque tentativi di avviare un job gia' avviato
 * aprirebbero il circuito.
 *
 * <p>Gli altri 4xx restano guasti: li' il client non ha un esito di business
 * da restituire e li mappa su "non raggiungibile".
 *
 * <p>Si verificano i predicati della configurazione effettivamente caricata:
 * l'annotazione {@code @CircuitBreaker} e' AOP e il comportamento nasce dalle
 * properties, non dal codice, quindi una regressione qui sarebbe altrimenti
 * silenziosa — compreso un errore di sintassi sui nomi delle classi annidate,
 * che il binder segnalerebbe solo all'avvio.
 */
@SpringBootTest
@ActiveProfiles("test")
class OperazioniTriggerCircuitBreakerConfigTest {

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private CircuitBreakerConfig config() {
        return circuitBreakerRegistry.circuitBreaker("operazioni-trigger").getCircuitBreakerConfig();
    }

    @Test
    void conflittoENonTrovatoNonContanoComeGuasto() {
        CircuitBreakerConfig config = config();

        assertThat(config.getIgnoreExceptionPredicate())
                .as("409: il job e' gia' in esecuzione, la richiesta e' arrivata a destinazione")
                .accepts(HttpClientErrorException.create(HttpStatus.CONFLICT,
                        "Conflict", null, null, null))
                .as("404: il job non esiste sul microservizio, non e' un guasto")
                .accepts(HttpClientErrorException.create(HttpStatus.NOT_FOUND,
                        "Not Found", null, null, null));
    }

    @Test
    void gliAltri4xxEIGuastiVeriRestanoFallimenti() {
        CircuitBreakerConfig config = config();

        HttpClientErrorException badRequest = HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "Bad Request", null, null, null);
        HttpServerErrorException errore5xx = HttpServerErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", null, null, null);
        ResourceAccessException timeout = new ResourceAccessException(
                "Read timed out", new SocketTimeoutException());

        assertThat(config.getIgnoreExceptionPredicate())
                .as("un 400 non ha un esito di business corrispondente: resta un'anomalia")
                .rejects(badRequest)
                .as("un 5xx e' un guasto del microservizio")
                .rejects(errore5xx)
                .as("un timeout e' un guasto di trasporto")
                .rejects(timeout);

        assertThat(config.getRecordExceptionPredicate())
                .accepts(badRequest)
                .accepts(errore5xx)
                .accepts(timeout);
    }
}
