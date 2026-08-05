package it.govpay.console.eventi;

import java.time.OffsetDateTime;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import it.govpay.console.api.GiornaleEventiApi;
import it.govpay.console.model.CategoriaEvento;
import it.govpay.console.model.ComponenteEvento;
import it.govpay.console.model.EsitoEvento;
import it.govpay.console.model.Evento;
import it.govpay.console.model.EventoRichiesta;
import it.govpay.console.model.EventoRisposta;
import it.govpay.console.model.ListEventi200Response;
import it.govpay.console.model.RuoloEvento;
import it.govpay.console.web.ListQueryValidator;
import jakarta.servlet.http.HttpServletRequest;

@RestController
public class EventoController implements GiornaleEventiApi {

    private static final Set<String> LIST_QUERY_PARAMS = Set.of(
            "page", "limit", "total", "cursor",
            "dataDa", "dataA", "idDominio", "iuv", "ccp", "idA2A", "idPendenza",
            "componente", "categoria", "esito", "ruolo", "tipoEvento", "sottotipoEvento",
            "severitaDa", "severitaA", "messaggi");

    private static final Set<String> GET_QUERY_PARAMS = Set.of();

    private static final Set<String> SUB_RESOURCE_QUERY_PARAMS = Set.of("unmask");

    private static final String CURSOR_FIXED_SORT = "dataEvento DESC, id DESC";

    private final EventoSearchService searchService;
    private final EventoDetailService detailService;
    private final EventoSubResourceService subResourceService;

    @Autowired(required = false)
    private HttpServletRequest currentRequest;

    public EventoController(EventoSearchService searchService, EventoDetailService detailService,
            EventoSubResourceService subResourceService) {
        this.searchService = searchService;
        this.detailService = detailService;
        this.subResourceService = subResourceService;
    }

    @Override
    public ResponseEntity<ListEventi200Response> listEventi(
            Integer page, Integer limit, Boolean total, String cursor,
            OffsetDateTime dataDa, OffsetDateTime dataA,
            String idDominio, String iuv, String ccp, String idA2A, String idPendenza,
            ComponenteEvento componente, CategoriaEvento categoria, EsitoEvento esito, RuoloEvento ruolo,
            String tipoEvento, String sottotipoEvento,
            Integer severitaDa, Integer severitaA, String messaggi) {
        ListQueryValidator.rejectUnsupported(currentRequest, LIST_QUERY_PARAMS);
        boolean cursorMode = ListQueryValidator.isCursorMode(currentRequest);
        if (cursorMode) {
            ListQueryValidator.rejectCursorIncompatible(currentRequest, CURSOR_FIXED_SORT);
        }
        EventoListQuery query = new EventoListQuery(
                page == null ? 1 : page,
                limit == null ? 25 : limit,
                total,
                cursorMode ? (cursor != null ? cursor : "") : null,
                dataDa, dataA,
                idDominio, iuv, ccp, idA2A, idPendenza,
                componente, categoria, esito, ruolo,
                tipoEvento, sottotipoEvento,
                severitaDa, severitaA, messaggi);
        return ResponseEntity.ok(searchService.search(query));
    }

    @Override
    public ResponseEntity<Evento> getEvento(Long id) {
        ListQueryValidator.rejectUnsupported(currentRequest, GET_QUERY_PARAMS);
        return detailService.get(id);
    }

    @Override
    public ResponseEntity<EventoRichiesta> getEventoRichiesta(Long id, Boolean unmask) {
        ListQueryValidator.rejectUnsupported(currentRequest, SUB_RESOURCE_QUERY_PARAMS);
        return ResponseEntity.ok(subResourceService.getRichiesta(id, Boolean.TRUE.equals(unmask), currentRequest));
    }

    @Override
    public ResponseEntity<EventoRisposta> getEventoRisposta(Long id, Boolean unmask) {
        ListQueryValidator.rejectUnsupported(currentRequest, SUB_RESOURCE_QUERY_PARAMS);
        return ResponseEntity.ok(subResourceService.getRisposta(id, Boolean.TRUE.equals(unmask), currentRequest));
    }
}
