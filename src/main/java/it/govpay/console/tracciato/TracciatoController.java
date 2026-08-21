package it.govpay.console.tracciato;

import java.time.OffsetDateTime;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import it.govpay.console.api.PendenzeTracciatiApi;
import it.govpay.console.model.FormatoTracciato;
import it.govpay.console.model.ListTracciatiPendenze200Response;
import it.govpay.console.model.StatoTracciatoPendenza;
import it.govpay.console.model.TracciatoPendenze;
import it.govpay.console.web.ListQueryValidator;
import jakarta.servlet.http.HttpServletRequest;

@RestController
public class TracciatoController implements PendenzeTracciatiApi {

    private static final Set<String> UPLOAD_QUERY_PARAMS = Set.of(
            "idDominio", "idTipoPendenza", "stampaAvvisi", "formato");

    private static final Set<String> LIST_QUERY_PARAMS = Set.of(
            "page", "limit", "sort", "total", "cursor",
            "idDominio", "stato", "dataDa", "dataA", "operatoreMittente", "formatoRichiesta");

    private final TracciatoUploadService uploadService;
    private final TracciatoSearchService searchService;

    @Autowired(required = false)
    private HttpServletRequest currentRequest;

    public TracciatoController(TracciatoUploadService uploadService, TracciatoSearchService searchService) {
        this.uploadService = uploadService;
        this.searchService = searchService;
    }

    @Override
    public ResponseEntity<TracciatoPendenze> uploadTracciatoPendenze(String idDominio,
                                                                      String idTipoPendenza,
                                                                      Boolean stampaAvvisi,
                                                                      FormatoTracciato formato,
                                                                      MultipartFile file) {
        ListQueryValidator.rejectUnsupported(currentRequest, UPLOAD_QUERY_PARAMS);
        TracciatoPendenze created = uploadService.upload(currentRequest, file, idDominio, idTipoPendenza, stampaAvvisi, formato);
        return ResponseEntity.accepted()
                .header("Location", "/pendenze/tracciati/" + created.getId())
                .body(created);
    }

    @Override
    public ResponseEntity<ListTracciatiPendenze200Response> listTracciatiPendenze(Integer page,
                                                                                   Integer limit,
                                                                                   String sort,
                                                                                   Boolean total,
                                                                                   String cursor,
                                                                                   String idDominio,
                                                                                   StatoTracciatoPendenza stato,
                                                                                   OffsetDateTime dataDa,
                                                                                   OffsetDateTime dataA,
                                                                                   String operatoreMittente,
                                                                                   FormatoTracciato formatoRichiesta) {
        ListQueryValidator.rejectUnsupported(currentRequest, LIST_QUERY_PARAMS);
        boolean cursorMode = ListQueryValidator.isCursorMode(currentRequest);
        if (cursorMode) {
            ListQueryValidator.rejectCursorIncompatible(currentRequest, "dataOraCaricamento DESC, id DESC");
        }
        TracciatoListQuery query = new TracciatoListQuery(
                page == null ? 1 : page,
                limit == null ? 25 : limit,
                sort,
                total,
                cursorMode ? (cursor != null ? cursor : "") : null,
                idDominio,
                stato,
                dataDa,
                dataA,
                operatoreMittente,
                formatoRichiesta);
        return ResponseEntity.ok(searchService.list(query));
    }

    @Override
    public ResponseEntity<TracciatoPendenze> getTracciatoPendenze(Long id) {
        TracciatoPendenze dto = searchService.get(id);
        return ResponseEntity.ok()
                .header("Cache-Control", "private, max-age=60")
                .body(dto);
    }
}
