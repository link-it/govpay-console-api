package it.govpay.console.pendenza;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import it.govpay.console.api.PendenzeApi;
import it.govpay.console.avviso.AvvisoService;
import it.govpay.console.model.Avviso;
import it.govpay.console.model.LinguaSecondaria;
import it.govpay.console.model.ListPendenze200Response;
import it.govpay.console.model.Pendenza;
import it.govpay.console.model.PendenzaExpand;
import it.govpay.console.model.RicevutaSummary;
import it.govpay.console.model.Soggetto;
import it.govpay.console.model.StatoPendenza;
import it.govpay.console.ricevuta.RicevutaService;
import it.govpay.console.soggetto.InformazioniDebitoreService;
import it.govpay.console.web.ListQueryValidator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
public class PendenzaController implements PendenzeApi {

    private static final Set<String> LIST_PENDENZE_QUERY_PARAMS = Set.of(
            "page", "limit", "sort", "total", "cursor",
            "idPendenza", "numeroAvviso", "idDominio", "identificativoDebitore",
            "stato", "dataDa", "dataA", "iuv", "direzione", "divisione",
            "idA2A", "idTipoPendenza");

    private static final Set<String> GET_PENDENZA_QUERY_PARAMS = Set.of("expand");

    private static final Set<String> GET_AVVISO_QUERY_PARAMS = Set.of("linguaSecondaria");

    private static final Set<String> GET_RICEVUTE_QUERY_PARAMS = Set.of();

    private static final Set<String> GET_INFO_DEBITORE_QUERY_PARAMS = Set.of();

    private final PendenzaService service;
    private final AvvisoService avvisoService;
    private final RicevutaService ricevutaService;
    private final InformazioniDebitoreService informazioniDebitoreService;
    private final HttpServletRequest currentRequest;
    private final HttpServletResponse currentResponse;

    public PendenzaController(PendenzaService service,
                              AvvisoService avvisoService,
                              RicevutaService ricevutaService,
                              InformazioniDebitoreService informazioniDebitoreService,
                              HttpServletRequest currentRequest,
                              HttpServletResponse currentResponse) {
        this.service = service;
        this.avvisoService = avvisoService;
        this.ricevutaService = ricevutaService;
        this.informazioniDebitoreService = informazioniDebitoreService;
        this.currentRequest = currentRequest;
        this.currentResponse = currentResponse;
    }

    @Override
    public ResponseEntity<ListPendenze200Response> listPendenze(Integer page,
                                                                Integer limit,
                                                                String sort,
                                                                Boolean total,
                                                                String cursor,
                                                                String idPendenza,
                                                                String numeroAvviso,
                                                                String idDominio,
                                                                String identificativoDebitore,
                                                                StatoPendenza stato,
                                                                LocalDate dataDa,
                                                                LocalDate dataA,
                                                                String iuv,
                                                                String direzione,
                                                                String divisione,
                                                                String idA2A,
                                                                List<String> idTipoPendenza) {
        ListQueryValidator.rejectUnsupported(currentRequest, LIST_PENDENZE_QUERY_PARAMS);
        // cursor mode attivo se ?cursor=... e' presente nella query string,
        // anche con valore vuoto ("prima pagina cursor-mode", scope G issue #9).
        boolean cursorMode = ListQueryValidator.isCursorMode(currentRequest);
        if (cursorMode) {
            ListQueryValidator.rejectCursorIncompatible(currentRequest,
                    "dataCreazione DESC, id DESC");
        }
        PendenzaListQuery query = new PendenzaListQuery(
                page == null ? 1 : page,
                limit == null ? 25 : limit,
                sort,
                total,
                ListQueryValidator.cursorValue(cursorMode, cursor),
                idPendenza,
                numeroAvviso,
                idDominio,
                identificativoDebitore,
                stato,
                dataDa,
                dataA,
                iuv,
                direzione,
                divisione,
                idA2A,
                idTipoPendenza);
        return ResponseEntity.ok(service.list(query, currentRequest));
    }

    @Override
    public ResponseEntity<Pendenza> getPendenza(String idA2A,
                                                String idPendenza,
                                                Set<PendenzaExpand> expand) {
        ListQueryValidator.rejectUnsupported(currentRequest, GET_PENDENZA_QUERY_PARAMS);
        return ResponseEntity.ok(service.get(idA2A, idPendenza, expand));
    }

    /**
     * Il return type generato dall'OpenAPI Generator e' {@code ResponseEntity<Avviso>}
     * perche' l'operazione dichiara prima {@code application/json}. Per il branch
     * {@code application/pdf} il service scrive in streaming direttamente sulla
     * {@link HttpServletResponse} (evitando di passare per i {@code HttpMessageConverter}
     * che non saprebbero gestire il body PDF qui) e restituisce {@code null}: in
     * quel caso il controller risponde con {@code ResponseEntity.ok().build()}
     * (status e headers sono gia' stati scritti dal service).
     */
    @Override
    public ResponseEntity<Avviso> getPendenzaAvviso(String idA2A,
                                                    String idPendenza,
                                                    LinguaSecondaria linguaSecondaria) {
        ListQueryValidator.rejectUnsupported(currentRequest, GET_AVVISO_QUERY_PARAMS);
        ResponseEntity<Avviso> response = avvisoService.get(
                idA2A, idPendenza, linguaSecondaria, currentRequest, currentResponse);
        return response != null ? response : ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Soggetto> getInformazioniDebitore(String idA2A, String idPendenza) {
        ListQueryValidator.rejectUnsupported(currentRequest, GET_INFO_DEBITORE_QUERY_PARAMS);
        return ResponseEntity.ok(
                informazioniDebitoreService.get(idA2A, idPendenza, currentRequest));
    }

    @Override
    public ResponseEntity<List<RicevutaSummary>> listPendenzaRicevute(String idA2A, String idPendenza) {
        ListQueryValidator.rejectUnsupported(currentRequest, GET_RICEVUTE_QUERY_PARAMS);
        return ResponseEntity.ok(ricevutaService.listByPendenza(idA2A, idPendenza));
    }
}
