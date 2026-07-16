package it.govpay.console.gde;

import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Enumeration;
import java.util.List;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import it.govpay.common.gde.GdeEventInfo;
import it.govpay.gde.client.beans.CategoriaEvento;
import it.govpay.gde.client.beans.ComponenteEvento;
import it.govpay.gde.client.beans.EsitoEvento;
import it.govpay.gde.client.beans.Header;
import it.govpay.gde.client.beans.RuoloEvento;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Genera un evento GDE per ogni richiesta API (componente
 * {@link ComponenteEvento#API_BACKOFFICE}, categoria
 * {@link CategoriaEvento#INTERFACCIA}, ruolo {@link RuoloEvento#SERVER}).
 * <p>
 * Il perimetro e' tutti gli endpoint: e' la policy configurata su GDE
 * (Giornale, log/dump SEMPRE/MAI/SOLO_ERRORE) a decidere cosa viene
 * effettivamente registrato — {@link ConsoleGdeService} delega questa
 * valutazione ad {@link it.govpay.common.gde.AbstractGdeService#inviaEvento}.
 * <p>
 * Cattura il body di richiesta e risposta solo se il Content-Type e'
 * testuale/strutturato (JSON, problem+json, XML, text/*); altrimenti
 * sostituisce il payload con il placeholder fisso {@code "binary"}, senza mai
 * bufferizzare l'intero contenuto binario in memoria (vedi
 * {@link GdeCapturingResponseWrapper}).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 17)
public class GdeEventFilter extends OncePerRequestFilter {

    private static final String UNKNOWN_OPERATION = "UNKNOWN";
    private static final String BINARY_PLACEHOLDER = "binary";
    private static final int MAX_CAPTURE_BYTES = 65536;

    private final ConsoleGdeService consoleGdeService;

    public GdeEventFilter(ConsoleGdeService consoleGdeService) {
        this.consoleGdeService = consoleGdeService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (DispatcherType.ERROR.equals(request.getDispatcherType())) {
            return true;
        }
        String uri = request.getRequestURI();
        return uri != null && uri.startsWith("/actuator");
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        OffsetDateTime start = OffsetDateTime.now();
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request, MAX_CAPTURE_BYTES);
        GdeCapturingResponseWrapper wrappedResponse = new GdeCapturingResponseWrapper(response, MAX_CAPTURE_BYTES);
        Throwable error = null;

        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        } catch (IOException | ServletException | RuntimeException | Error e) {
            error = e;
            throw e;
        } finally {
            long durataMs = Duration.between(start, OffsetDateTime.now()).toMillis();
            buildAndSendEvent(wrappedRequest, wrappedResponse, start, durataMs, error);
        }
    }

    private void buildAndSendEvent(ContentCachingRequestWrapper request, GdeCapturingResponseWrapper response,
                                   OffsetDateTime start, long durataMs, Throwable error) {
        int status = status(response, error);
        EsitoEvento esito = status < 400 ? EsitoEvento.OK : (status >= 500 ? EsitoEvento.FAIL : EsitoEvento.KO);

        GdeEventInfo eventInfo = GdeEventInfo.builder()
                .componente(ComponenteEvento.API_BACKOFFICE)
                .categoriaEvento(CategoriaEvento.INTERFACCIA)
                .ruolo(RuoloEvento.SERVER)
                .tipoEvento(operationId(request))
                .dataEvento(start)
                .durataEvento(durataMs)
                .esito(esito)
                .descrizioneEsito(error != null ? error.getMessage() : null)
                .principal(currentPrincipal(request))
                .urlRichiesta(fullUrl(request))
                .metodoHttp(request.getMethod())
                .headersRichiesta(extractRequestHeaders(request))
                .payloadRichiesta(requestPayload(request))
                .statusCodeRisposta(status)
                .headersRisposta(extractResponseHeaders(response))
                .payloadRisposta(responsePayload(response))
                .build();

        consoleGdeService.inviaEventoAsync(eventInfo);
    }

    /**
     * Se la chain ha propagato un'eccezione senza che nessuno abbia impostato
     * uno status di errore sulla response (es. eccezione non gestita da
     * nessun exception handler), lo status resterebbe 200 di default e
     * l'evento uscirebbe erroneamente come OK. Stesso pattern gia' applicato
     * in {@code ApiTimingMetricsFilter}.
     */
    private static int status(HttpServletResponse response, Throwable error) {
        int status = response.getStatus();
        if (error != null && status < 400) {
            return 500;
        }
        return status;
    }

    private static String operationId(HttpServletRequest request) {
        Object attr = request.getAttribute(OperationIdHandlerInterceptor.OPERATION_ID_ATTRIBUTE);
        return attr != null ? attr.toString() : UNKNOWN_OPERATION;
    }

    /**
     * Letto dall'attributo valorizzato da {@link OperationIdHandlerInterceptor},
     * non da {@code SecurityContextHolder} direttamente: nel {@code finally}
     * di questo filtro il {@code SecurityContext} e' gia' stato ripulito
     * dalla security chain (vedi javadoc di {@link OperationIdHandlerInterceptor}).
     */
    private static String currentPrincipal(HttpServletRequest request) {
        Object attr = request.getAttribute(OperationIdHandlerInterceptor.PRINCIPAL_ATTRIBUTE);
        return attr != null ? attr.toString() : null;
    }

    private static String fullUrl(HttpServletRequest request) {
        StringBuilder url = new StringBuilder(request.getRequestURL());
        if (request.getQueryString() != null) {
            url.append('?').append(request.getQueryString());
        }
        return url.toString();
    }

    private static List<Header> extractRequestHeaders(HttpServletRequest request) {
        List<Header> headers = new ArrayList<>();
        Enumeration<String> names = request.getHeaderNames();
        if (names != null) {
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                headers.add(header(name, request.getHeader(name)));
            }
        }
        return headers;
    }

    private static List<Header> extractResponseHeaders(HttpServletResponse response) {
        List<Header> headers = new ArrayList<>();
        for (String name : response.getHeaderNames()) {
            headers.add(header(name, response.getHeader(name)));
        }
        return headers;
    }

    private static Header header(String nome, String valore) {
        Header header = new Header();
        header.setNome(nome);
        header.setValore(valore);
        return header;
    }

    private static String requestPayload(ContentCachingRequestWrapper request) {
        if (!isCaptureableContentType(request.getContentType())) {
            return BINARY_PLACEHOLDER;
        }
        byte[] content = request.getContentAsByteArray();
        return content.length == 0 ? null : Base64.getEncoder().encodeToString(content);
    }

    private static String responsePayload(GdeCapturingResponseWrapper response) {
        if (!isCaptureableContentType(response.getContentType())) {
            return BINARY_PLACEHOLDER;
        }
        byte[] content = response.getCapturedBytes();
        return content.length == 0 ? null : Base64.getEncoder().encodeToString(content);
    }

    /**
     * Considera catturabile (testuale/strutturato) solo un elenco esplicito
     * di Content-Type; tutto il resto (PDF, octet-stream, immagini, ecc.)
     * riceve il placeholder fisso. Content-Type assente (es. GET senza body)
     * e' considerato catturabile ma produce comunque un payload vuoto/nullo.
     */
    private static boolean isCaptureableContentType(String contentType) {
        if (contentType == null) {
            return true;
        }
        String lower = contentType.toLowerCase(java.util.Locale.ROOT);
        return lower.startsWith("application/json")
                || lower.startsWith("application/problem+json")
                || lower.startsWith("application/xml")
                || lower.startsWith("text/");
    }
}
