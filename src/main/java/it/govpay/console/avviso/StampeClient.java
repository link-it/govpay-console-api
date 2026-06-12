package it.govpay.console.avviso;

import java.io.OutputStream;
import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import it.govpay.stampe.client.model.PaymentNotice;

/**
 * Facade verso il microservizio {@code govpay-stampe}. Responsabilita':
 * <ul>
 *   <li>verifica che {@code app.stampe.base-url} sia configurato e lancia
 *       {@link StampeNotConfiguredException} (→ 503) altrimenti;</li>
 *   <li>fa <b>vero streaming</b> della response PDF dal microservizio verso
 *       l'{@link OutputStream} fornito (no buffer in memoria);</li>
 *   <li>cattura {@link RestClientException} → {@link StampeUnavailableException}
 *       (→ 502);</li>
 *   <li>protegge la chiamata con retry + circuit breaker (Resilience4j,
 *       instance {@code stampe}, configurata in {@code application.properties}).
 *       Quando il circuito e' aperto {@link CallNotPermittedException} viene
 *       mappata anch'essa su {@link StampeUnavailableException}.</li>
 * </ul>
 *
 * <p>Bypassa il client generato dall'OpenAPI Generator (che ritorna
 * {@code byte[]} bufferizzato) e usa direttamente
 * {@link RestTemplate#execute(URI, HttpMethod,
 * org.springframework.web.client.RequestCallback,
 * org.springframework.web.client.ResponseExtractor)} per fare copy-through
 * input → output.
 */
@Service
public class StampeClient {

    private static final Logger log = LoggerFactory.getLogger(StampeClient.class);

    /** Path del microservizio sul base-url (allineato a govpay-stampe.yaml). */
    private static final String PAYMENT_NOTICE_PATH = "/standard";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public StampeClient(RestTemplate stampeRestTemplate,
                        ObjectMapper objectMapper,
                        @Value("${app.stampe.base-url:}") String baseUrl) {
        this.restTemplate = stampeRestTemplate;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
    }

    @CircuitBreaker(name = "stampe")
    @Retry(name = "stampe")
    public void streamPaymentNotice(PaymentNotice payload, OutputStream output) {
        if (!StringUtils.hasText(baseUrl)) {
            throw new StampeNotConfiguredException();
        }
        URI url = URI.create(baseUrl + PAYMENT_NOTICE_PATH);
        try {
            restTemplate.execute(url, HttpMethod.POST,
                    request -> {
                        request.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                        request.getHeaders().setAccept(List.of(MediaType.APPLICATION_PDF));
                        objectMapper.writeValue(request.getBody(), payload);
                    },
                    response -> {
                        StreamUtils.copy(response.getBody(), output);
                        return null;
                    });
        } catch (CallNotPermittedException e) {
            log.warn("Circuit breaker aperto sul client govpay-stampe: {}", e.getMessage());
            throw new StampeUnavailableException(
                    "Microservizio govpay-stampe momentaneamente non disponibile (circuit open).", e);
        } catch (RestClientException e) {
            log.warn("Chiamata al microservizio govpay-stampe fallita: {}", e.getMessage());
            throw new StampeUnavailableException(
                    "Chiamata al microservizio govpay-stampe fallita.", e);
        }
    }
}
