package it.govpay.console.eventi;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import it.govpay.common.client.model.Connettore;
import it.govpay.common.configurazione.service.ConfigurazioneService;
import it.govpay.console.web.NotFoundException;
import it.govpay.gde.client.beans.Evento;
import it.govpay.gde.client.beans.ListaEventi;

/**
 * Facade verso {@code GET /eventi} del servizio GDE. Riusa l'infrastruttura di
 * connessione gia' costruita per l'invio eventi ({@link ConfigurazioneService},
 * govpay-common): stesso RestTemplate autenticato sul connettore
 * {@code servizioGDE} usato da {@code ConsoleGdeService} per il {@code POST}.
 * Stesso pattern di errore di {@code StampeClient}/{@code OperazioneBatchClient}:
 * 503 se il connettore non e' configurato/abilitato, 502 se la chiamata fallisce
 * o il circuito e' aperto.
 *
 * <p>La chiamata HTTP vera e propria (con {@code @CircuitBreaker}/{@code @Retry})
 * e' delegata a {@link GdeRawClient}: qui si intercetta l'esito finale,
 * incluso il circuito aperto — vedi Javadoc di {@link GdeRawClient}.
 */
@Service
public class EventoGdeClient {

    private static final Logger log = LoggerFactory.getLogger(EventoGdeClient.class);

    private final ConfigurazioneService configurazioneService;
    private final GdeRawClient rawClient;

    public EventoGdeClient(ConfigurazioneService configurazioneService, GdeRawClient rawClient) {
        this.configurazioneService = configurazioneService;
        this.rawClient = rawClient;
    }

    public ListaEventi findEventi(EventoGdeQuery query) {
        if (!configurazioneService.isServizioGDEAbilitato()) {
            throw new GdeNonConfiguratoException();
        }
        Connettore connettore = configurazioneService.getServizioGDE();
        RestTemplate restTemplate = configurazioneService.getRestTemplateGDE();

        String uri = buildUri(connettore.getUrl(), query);
        try {
            return rawClient.get(restTemplate, uri, ListaEventi.class);
        } catch (CallNotPermittedException e) {
            log.warn("Circuit breaker aperto sul client GDE: {}", e.getMessage());
            throw new GdeNonRaggiungibileException(
                    "Servizio GDE momentaneamente non disponibile (circuit open).", e);
        } catch (RestClientException e) {
            log.warn("Chiamata al servizio GDE fallita: {}", e.getMessage());
            throw new GdeNonRaggiungibileException("Chiamata al servizio GDE fallita.", e);
        }
    }

    public Evento getEventoById(Long id) {
        if (!configurazioneService.isServizioGDEAbilitato()) {
            throw new GdeNonConfiguratoException();
        }
        Connettore connettore = configurazioneService.getServizioGDE();
        RestTemplate restTemplate = configurazioneService.getRestTemplateGDE();

        String uri = connettore.getUrl() + "/eventi/" + id;
        try {
            return rawClient.get(restTemplate, uri, Evento.class);
        } catch (CallNotPermittedException e) {
            log.warn("Circuit breaker aperto sul client GDE: {}", e.getMessage());
            throw new GdeNonRaggiungibileException(
                    "Servizio GDE momentaneamente non disponibile (circuit open).", e);
        } catch (HttpClientErrorException.NotFound e) {
            throw new NotFoundException("Evento non trovato: " + id, e);
        } catch (RestClientException e) {
            log.warn("Chiamata al servizio GDE fallita: {}", e.getMessage());
            throw new GdeNonRaggiungibileException("Chiamata al servizio GDE fallita.", e);
        }
    }

    private String buildUri(String baseUrl, EventoGdeQuery query) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl + "/eventi")
                .queryParam("limit", query.limit());

        if (query.cursorMode()) {
            builder.queryParam("pagingMode", "CURSOR");
            if (query.cursorData() != null) {
                builder.queryParam("cursorData", istanteUtc(query.cursorData()))
                        .queryParam("cursorId", query.cursorId());
            }
        } else {
            builder.queryParam("offset", query.offset());
            if (query.total()) {
                builder.queryParam("total", true);
            }
        }
        addIstante(builder, "dataDa", query.dataDa());
        addIstante(builder, "dataA", query.dataA());
        for (String dominio : query.idDominio()) {
            builder.queryParam("idDominio", dominio);
        }
        addIfPresent(builder, "iuv", query.iuv());
        addIfPresent(builder, "ccp", query.ccp());
        addIfPresent(builder, "idA2A", query.idA2A());
        addIfPresent(builder, "idPendenza", query.idPendenza());
        addIfPresent(builder, "componente", query.componente());
        addIfPresent(builder, "categoriaEvento", query.categoria());
        addIfPresent(builder, "esito", query.esito());
        addIfPresent(builder, "ruolo", query.ruolo());
        addIfPresent(builder, "tipoEvento", query.tipoEvento());
        addIfPresent(builder, "sottotipoEvento", query.sottotipoEvento());
        addIfPresent(builder, "severitaDa", query.severitaDa());
        addIfPresent(builder, "severitaA", query.severitaA());
        addIfPresent(builder, "messaggi", query.messaggi());
        return builder.toUriString();
    }

    private void addIfPresent(UriComponentsBuilder builder, String name, Object value) {
        if (value != null) {
            builder.queryParam(name, value);
        }
    }

    private void addIstante(UriComponentsBuilder builder, String name, OffsetDateTime value) {
        if (value != null) {
            builder.queryParam(name, istanteUtc(value));
        }
    }

    /**
     * Gli istanti si inviano a GDE sempre normalizzati in UTC, quindi resi con
     * la {@code Z} finale. Non e' una preferenza di formato: reso con un offset
     * diverso da UTC, {@link OffsetDateTime#toString()} produce un {@code +}
     * (es. {@code +02:00}) che e' un sub-delimiter legale in una query string,
     * e come tale {@link UriComponentsBuilder} non lo percent-encoda. Lato GDE
     * Tomcat decodifica quel {@code +} come uno spazio, il binding su
     * {@code OffsetDateTime} fallisce e la richiesta torna 400 — che qui
     * diventa un 502 opaco. L'istante resta lo stesso, cambia solo il
     * rappresentante scelto per scriverlo.
     */
    private static String istanteUtc(OffsetDateTime value) {
        return value.withOffsetSameInstant(ZoneOffset.UTC).toString();
    }
}
