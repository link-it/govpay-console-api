package it.govpay.console.pendenza;

import java.util.Collections;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import it.govpay.console.api.PendenzeApi;
import it.govpay.console.avviso.AvvisoService;
import it.govpay.console.model.Avviso;
import it.govpay.console.model.LinguaSecondaria;
import it.govpay.console.model.ListPendenze200Response;
import it.govpay.console.model.Pendenza;
import it.govpay.console.model.PendenzaExpand;
import it.govpay.console.model.Ricevuta;
import it.govpay.console.model.Soggetto;
import it.govpay.console.ricevuta.RicevutaService;
import it.govpay.console.soggetto.InformazioniDebitoreService;
import it.govpay.console.web.BadRequestException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
public class PendenzaController implements PendenzeApi {

    private static final Set<String> LIST_PENDENZE_QUERY_PARAMS = Set.of(
            "page", "limit", "sort", "total", "cursor",
            "idPendenza", "numeroAvviso", "idDominio", "identificativoDebitore");

    private static final Set<String> GET_PENDENZA_QUERY_PARAMS = Set.of("expand");

    private static final Set<String> GET_AVVISO_QUERY_PARAMS = Set.of("linguaSecondaria");

    private static final Set<String> GET_RICEVUTA_QUERY_PARAMS = Set.of();

    private static final Set<String> GET_INFO_DEBITORE_QUERY_PARAMS = Set.of();

    private final PendenzaService service;
    private final AvvisoService avvisoService;
    private final RicevutaService ricevutaService;
    private final InformazioniDebitoreService informazioniDebitoreService;

    @Autowired(required = false)
    private HttpServletRequest currentRequest;

    @Autowired(required = false)
    private HttpServletResponse currentResponse;

    public PendenzaController(PendenzaService service,
                              AvvisoService avvisoService,
                              RicevutaService ricevutaService,
                              InformazioniDebitoreService informazioniDebitoreService) {
        this.service = service;
        this.avvisoService = avvisoService;
        this.ricevutaService = ricevutaService;
        this.informazioniDebitoreService = informazioniDebitoreService;
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
                                                                String identificativoDebitore) {
        rejectUnsupportedQueryParams(currentRequest, LIST_PENDENZE_QUERY_PARAMS);
        // cursor mode attivo se ?cursor=... e' presente nella query string,
        // anche con valore vuoto ("prima pagina cursor-mode", scope G issue #9).
        if (currentRequest != null && currentRequest.getParameterMap().containsKey("cursor")) {
            rejectCursorIncompatibleParams(currentRequest);
        }
        boolean cursorMode = currentRequest != null
                && currentRequest.getParameterMap().containsKey("cursor");
        PendenzaListQuery query = new PendenzaListQuery(
                page == null ? 1 : page,
                limit == null ? 25 : limit,
                sort,
                total,
                cursorMode ? (cursor != null ? cursor : "") : null,
                idPendenza,
                numeroAvviso,
                idDominio,
                identificativoDebitore);
        return ResponseEntity.ok(service.list(query, currentRequest));
    }

    /**
     * In modalita' cursor (`?cursor=...`) sono incompatibili:
     * <ul>
     *   <li>{@code page} esplicito (mutua esclusione paginazione: scope G issue #9);</li>
     *   <li>{@code sort} esplicito (l'ordinamento e' fisso
     *       {@code (dataOraUltimoAggiornamento DESC, id DESC)} per il keyset);</li>
     *   <li>{@code total=true} (in cursor mode il conteggio totale non e'
     *       disponibile, {@code nextCursor} lo sostituisce funzionalmente).</li>
     * </ul>
     * Messaggi dei {@code Problem} parlanti per orientare il client.
     */
    private static void rejectCursorIncompatibleParams(HttpServletRequest request) {
        if (request == null) {
            return;
        }
        if (isExplicit(request, "page")) {
            throw new BadRequestException(
                    "Parametri 'page' e 'cursor' mutuamente esclusivi: usa solo uno dei due "
                            + "(cursor per paginazione keyset, page per paginazione offset).");
        }
        if (isExplicit(request, "sort")) {
            throw new BadRequestException(
                    "In modalita' cursor (?cursor=...) l'ordinamento e' fisso "
                            + "(dataOraUltimoAggiornamento DESC, id DESC): non specificare ?sort=.");
        }
        if (isExplicit(request, "total")) {
            throw new BadRequestException(
                    "In modalita' cursor (?cursor=...) il conteggio totale non e' disponibile; "
                            + "?total=true non e' compatibile. Usa la presenza di 'nextCursor' "
                            + "in risposta per sapere se ci sono altre pagine.");
        }
    }

    private static boolean isExplicit(HttpServletRequest request, String name) {
        String[] values = request.getParameterMap().get(name);
        if (values == null || values.length == 0) {
            return false;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ResponseEntity<Pendenza> getPendenza(String idA2A,
                                                String idPendenza,
                                                Set<PendenzaExpand> expand) {
        rejectUnsupportedQueryParams(currentRequest, GET_PENDENZA_QUERY_PARAMS);
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
        rejectUnsupportedQueryParams(currentRequest, GET_AVVISO_QUERY_PARAMS);
        ResponseEntity<Avviso> response = avvisoService.get(
                idA2A, idPendenza, linguaSecondaria, currentRequest, currentResponse);
        return response != null ? response : ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Soggetto> getInformazioniDebitore(String idA2A, String idPendenza) {
        rejectUnsupportedQueryParams(currentRequest, GET_INFO_DEBITORE_QUERY_PARAMS);
        return ResponseEntity.ok(
                informazioniDebitoreService.get(idA2A, idPendenza, currentRequest));
    }

    /**
     * Analogo a {@link #getPendenzaAvviso}: stesso pattern di delega allo
     * scrivere direttamente sulla {@link HttpServletResponse} per le varianti
     * binarie (xml, pdf) — il service ritorna {@code null} in quei casi e il
     * controller risponde con {@code ResponseEntity.ok().build()}.
     */
    @Override
    public ResponseEntity<Ricevuta> getPendenzaRicevuta(String idA2A, String idPendenza) {
        rejectUnsupportedQueryParams(currentRequest, GET_RICEVUTA_QUERY_PARAMS);
        ResponseEntity<Ricevuta> response = ricevutaService.get(
                idA2A, idPendenza, currentRequest, currentResponse);
        return response != null ? response : ResponseEntity.ok().build();
    }

    private static void rejectUnsupportedQueryParams(HttpServletRequest request,
                                                     Set<String> allowed) {
        if (request == null) {
            return;
        }
        for (String name : Collections.list(request.getParameterNames())) {
            if (!allowed.contains(name)) {
                throw new BadRequestException(
                        "Filtro non supportato in Fase 1: " + name);
            }
        }
    }
}
