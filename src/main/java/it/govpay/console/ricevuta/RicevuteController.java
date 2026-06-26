package it.govpay.console.ricevuta;

import java.time.LocalDate;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

import java.util.Map;

import it.govpay.console.api.RicevuteApi;
import it.govpay.console.model.ListRicevute200Response;
import it.govpay.console.model.Ricevuta;
import it.govpay.console.web.ListQueryValidator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
public class RicevuteController implements RicevuteApi {

    private static final Set<String> LIST_RICEVUTE_QUERY_PARAMS = Set.of(
            "page", "limit", "sort", "total", "cursor",
            "iuv", "idDominio", "idRicevuta", "dataDa", "dataA");

    private static final Set<String> GET_RICEVUTA_QUERY_PARAMS = Set.of();

    private static final String CURSOR_FIXED_SORT = "dataPagamento DESC, id DESC";

    private final RicevutaSearchService searchService;
    private final RicevutaService ricevutaService;

    @Autowired(required = false)
    private HttpServletRequest currentRequest;

    @Autowired(required = false)
    private HttpServletResponse currentResponse;

    public RicevuteController(RicevutaSearchService searchService,
                              RicevutaService ricevutaService) {
        this.searchService = searchService;
        this.ricevutaService = ricevutaService;
    }

    @Override
    public ResponseEntity<ListRicevute200Response> listRicevute(Integer page,
                                                                Integer limit,
                                                                String sort,
                                                                Boolean total,
                                                                String cursor,
                                                                String iuv,
                                                                String idDominio,
                                                                String idRicevuta,
                                                                LocalDate dataDa,
                                                                LocalDate dataA) {
        ListQueryValidator.rejectUnsupported(currentRequest, LIST_RICEVUTE_QUERY_PARAMS);
        boolean cursorMode = ListQueryValidator.isCursorMode(currentRequest);
        if (cursorMode) {
            ListQueryValidator.rejectCursorIncompatible(currentRequest, CURSOR_FIXED_SORT);
        }
        RicevutaListQuery query = new RicevutaListQuery(
                page == null ? 1 : page,
                limit == null ? 25 : limit,
                sort,
                total,
                cursorMode ? (cursor != null ? cursor : "") : null,
                iuv,
                idDominio,
                idRicevuta,
                dataDa,
                dataA);
        return ResponseEntity.ok(searchService.search(query));
    }

    @Override
    public ResponseEntity<Ricevuta> getRicevuta(String idDominio, String iuv, String idRicevuta) {
        ListQueryValidator.rejectUnsupported(currentRequest, GET_RICEVUTA_QUERY_PARAMS);
        Ricevuta dto = ricevutaService.getDetail(idDominio, iuv, idRicevuta, currentRequest);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS).cachePrivate())
                .body(dto);
    }

    @Override
    public ResponseEntity<Map<String, Object>> getRicevutaRpt(String idDominio, String iuv,
                                                              String idRicevuta) {
        ListQueryValidator.rejectUnsupported(currentRequest, GET_RICEVUTA_QUERY_PARAMS);
        ResponseEntity<Map<String, Object>> response =
                ricevutaService.getRpt(idDominio, iuv, idRicevuta, currentRequest, currentResponse);
        return response != null ? response : ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Map<String, Object>> getRicevutaRt(String idDominio, String iuv,
                                                             String idRicevuta) {
        ListQueryValidator.rejectUnsupported(currentRequest, GET_RICEVUTA_QUERY_PARAMS);
        ResponseEntity<Map<String, Object>> response =
                ricevutaService.getRt(idDominio, iuv, idRicevuta, currentRequest, currentResponse);
        return response != null ? response : ResponseEntity.ok().build();
    }
}
