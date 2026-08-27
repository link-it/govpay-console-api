package it.govpay.console.tracciato;

import java.time.OffsetDateTime;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import it.govpay.console.api.PendenzeTracciatiApi;
import it.govpay.console.model.FormatoTracciato;
import it.govpay.console.model.ListTracciatiPendenze200Response;
import it.govpay.console.model.ListTracciatoPendenzeOperazioni200Response;
import it.govpay.console.model.OperazionePendenza;
import it.govpay.console.model.StatoOperazionePendenza;
import it.govpay.console.model.StatoTracciatoPendenza;
import it.govpay.console.model.TipoOperazionePendenza;
import it.govpay.console.model.TracciatoPendenze;
import it.govpay.console.model.TracciatoPendenzePost;
import it.govpay.console.model.TracciatoPendenzeEsito;
import it.govpay.console.web.ListQueryValidator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
public class TracciatoController implements PendenzeTracciatiApi {

    private static final Set<String> UPLOAD_QUERY_PARAMS = Set.of(
            "idDominio", "idTipoPendenza", "stampaAvvisi", "formato");

    private static final Set<String> LIST_QUERY_PARAMS = Set.of(
            "page", "limit", "sort", "total", "cursor",
            "idDominio", "stato", "dataDa", "dataA", "operatoreMittente", "formatoRichiesta");

    private static final Set<String> OPERAZIONI_LIST_QUERY_PARAMS = Set.of(
            "limit", "cursor", "stato", "tipoOperazione");

    private final TracciatoUploadService uploadService;
    private final TracciatoSearchService searchService;
    private final TracciatoContentService contentService;
    private final TracciatoStampeService stampeService;
    private final OperazioneSearchService operazioneSearchService;
    private final HttpServletRequest currentRequest;
    private final HttpServletResponse currentResponse;

    public TracciatoController(TracciatoUploadService uploadService,
                               TracciatoSearchService searchService,
                               TracciatoContentService contentService,
                               TracciatoStampeService stampeService,
                               OperazioneSearchService operazioneSearchService,
                               HttpServletRequest currentRequest,
                               HttpServletResponse currentResponse) {
        this.uploadService = uploadService;
        this.searchService = searchService;
        this.contentService = contentService;
        this.stampeService = stampeService;
        this.operazioneSearchService = operazioneSearchService;
        this.currentRequest = currentRequest;
        this.currentResponse = currentResponse;
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

    /**
     * Il service scrive il body direttamente su {@link HttpServletResponse}
     * (streaming, evita di passare per gli {@code HttpMessageConverter} che
     * non saprebbero gestire indifferentemente JSON/CSV sullo stesso metodo).
     * Stesso pattern di {@code PendenzaController#getPendenzaAvviso}.
     */
    @Override
    public ResponseEntity<TracciatoPendenzePost> getTracciatoPendenzeRichiesta(Long id) {
        contentService.richiesta(id, currentRequest, currentResponse);
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<TracciatoPendenzeEsito> getTracciatoPendenzeEsito(Long id) {
        contentService.esito(id, currentRequest, currentResponse);
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<org.springframework.core.io.Resource> getTracciatoPendenzeStampe(Long id) {
        return stampeService.get(id);
    }

    @Override
    public ResponseEntity<ListTracciatoPendenzeOperazioni200Response> listTracciatoPendenzeOperazioni(
            Long id, Integer limit, String cursor, StatoOperazionePendenza stato, TipoOperazionePendenza tipoOperazione) {
        ListQueryValidator.rejectUnsupported(currentRequest, OPERAZIONI_LIST_QUERY_PARAMS);
        return ResponseEntity.ok(operazioneSearchService.list(id, limit, cursor, stato, tipoOperazione));
    }

    @Override
    public ResponseEntity<OperazionePendenza> getTracciatoPendenzeOperazione(Long id, Long numero) {
        return ResponseEntity.ok(operazioneSearchService.get(id, numero, currentRequest));
    }
}
