package it.govpay.console.rendicontazione;

import java.time.OffsetDateTime;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import it.govpay.console.api.RendicontazioniApi;
import it.govpay.console.model.FlussoRendicontazione;
import it.govpay.console.model.ListFlussiRendicontazione200Response;
import it.govpay.console.model.StatoFlussoRendicontazione;
import it.govpay.console.web.ListQueryValidator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
public class FrController implements RendicontazioniApi {

    private static final Set<String> LIST_QUERY_PARAMS = Set.of(
            "page", "limit", "sort", "total", "cursor",
            "idDominio", "idFlusso", "idPsp", "dataDa", "dataA", "stato", "incassato", "iuv");

    private static final Set<String> GET_QUERY_PARAMS = Set.of();

    private static final String CURSOR_FIXED_SORT = "dataOraFlusso DESC, id DESC";

    private final FrSearchService searchService;
    private final FrDetailService detailService;

    @Autowired(required = false)
    private HttpServletRequest currentRequest;

    @Autowired(required = false)
    private HttpServletResponse currentResponse;

    public FrController(FrSearchService searchService, FrDetailService detailService) {
        this.searchService = searchService;
        this.detailService = detailService;
    }

    @Override
    public ResponseEntity<ListFlussiRendicontazione200Response> listFlussiRendicontazione(
            Integer page, Integer limit, String sort, Boolean total, String cursor,
            String idDominio, String idFlusso, String idPsp,
            OffsetDateTime dataDa, OffsetDateTime dataA,
            StatoFlussoRendicontazione stato, Boolean incassato, String iuv) {
        ListQueryValidator.rejectUnsupported(currentRequest, LIST_QUERY_PARAMS);
        boolean cursorMode = ListQueryValidator.isCursorMode(currentRequest);
        if (cursorMode) {
            ListQueryValidator.rejectCursorIncompatible(currentRequest, CURSOR_FIXED_SORT);
        }
        FrListQuery query = new FrListQuery(
                page == null ? 1 : page,
                limit == null ? 25 : limit,
                sort,
                total,
                cursorMode ? (cursor != null ? cursor : "") : null,
                idDominio,
                idFlusso,
                idPsp,
                dataDa,
                dataA,
                stato,
                incassato,
                iuv);
        return ResponseEntity.ok(searchService.search(query));
    }

    @Override
    public ResponseEntity<FlussoRendicontazione> getFlussoRendicontazione(
            String idDominio, String idFlusso, String idPsp, Long revisione) {
        ListQueryValidator.rejectUnsupported(currentRequest, GET_QUERY_PARAMS);
        ResponseEntity<FlussoRendicontazione> response =
                detailService.get(idDominio, idFlusso, idPsp, revisione, currentRequest, currentResponse);
        return response != null ? response : ResponseEntity.ok().build();
    }
}
