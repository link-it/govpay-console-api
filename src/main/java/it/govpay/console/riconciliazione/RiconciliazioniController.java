package it.govpay.console.riconciliazione;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import it.govpay.console.api.RiconciliazioniApi;
import it.govpay.console.model.ListRiconciliazioni200Response;
import it.govpay.console.model.NuovaRiconciliazione;
import it.govpay.console.model.Riconciliazione;
import it.govpay.console.model.StatoRiconciliazione;
import it.govpay.console.model.TipoRiscossione;
import it.govpay.console.web.ListQueryValidator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
public class RiconciliazioniController implements RiconciliazioniApi {

    private static final Set<String> LIST_QUERY_PARAMS = Set.of(
            "page", "limit", "sort", "total", "cursor",
            "idDominio", "dataDa", "dataA", "stato", "sct", "idFlusso", "iuv");

    private static final Set<String> GET_QUERY_PARAMS = Set.of("tipoRiscossione");

    private static final String CURSOR_FIXED_SORT = "data DESC, id DESC";

    private final RiconciliazioneSearchService searchService;
    private final RiconciliazioneDetailService detailService;
    private final RiconciliazioneWriteService writeService;
    private final HttpServletRequest currentRequest;
    private final HttpServletResponse currentResponse;

    public RiconciliazioniController(RiconciliazioneSearchService searchService,
                                     RiconciliazioneDetailService detailService,
                                     RiconciliazioneWriteService writeService,
                                     HttpServletRequest currentRequest,
                                     HttpServletResponse currentResponse) {
        this.searchService = searchService;
        this.detailService = detailService;
        this.writeService = writeService;
        this.currentRequest = currentRequest;
        this.currentResponse = currentResponse;
    }

    @Override
    public ResponseEntity<ListRiconciliazioni200Response> listRiconciliazioni(
            Integer page, Integer limit, String sort, Boolean total, String cursor,
            String idDominio, OffsetDateTime dataDa, OffsetDateTime dataA,
            StatoRiconciliazione stato, String sct, String idFlusso, String iuv) {
        ListQueryValidator.rejectUnsupported(currentRequest, LIST_QUERY_PARAMS);
        boolean cursorMode = ListQueryValidator.isCursorMode(currentRequest);
        if (cursorMode) {
            ListQueryValidator.rejectCursorIncompatible(currentRequest, CURSOR_FIXED_SORT);
        }
        RiconciliazioneListQuery query = new RiconciliazioneListQuery(
                page == null ? 1 : page,
                limit == null ? 25 : limit,
                sort,
                total,
                cursorMode ? (cursor != null ? cursor : "") : null,
                idDominio,
                dataDa,
                dataA,
                stato,
                sct,
                idFlusso,
                iuv);
        return ResponseEntity.ok(searchService.search(query));
    }

    @Override
    public ResponseEntity<Riconciliazione> getRiconciliazione(
            String idDominio, String id, List<TipoRiscossione> tipoRiscossione) {
        ListQueryValidator.rejectUnsupported(currentRequest, GET_QUERY_PARAMS);
        return detailService.get(idDominio, id, tipoRiscossione);
    }

    @Override
    public ResponseEntity<Riconciliazione> registraRiconciliazione(
            String idDominio, String id, NuovaRiconciliazione nuovaRiconciliazione) {
        return writeService.put(idDominio, id, nuovaRiconciliazione, currentRequest);
    }
}
