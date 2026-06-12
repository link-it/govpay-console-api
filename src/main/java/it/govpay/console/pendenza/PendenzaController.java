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
import it.govpay.console.web.BadRequestException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
public class PendenzaController implements PendenzeApi {

    private static final Set<String> LIST_PENDENZE_QUERY_PARAMS = Set.of(
            "page", "limit", "sort", "total",
            "idPendenza", "numeroAvviso", "idDominio", "identificativoDebitore");

    private static final Set<String> GET_PENDENZA_QUERY_PARAMS = Set.of("expand");

    private static final Set<String> GET_AVVISO_QUERY_PARAMS = Set.of("linguaSecondaria");

    private final PendenzaService service;
    private final AvvisoService avvisoService;

    @Autowired(required = false)
    private HttpServletRequest currentRequest;

    @Autowired(required = false)
    private HttpServletResponse currentResponse;

    public PendenzaController(PendenzaService service, AvvisoService avvisoService) {
        this.service = service;
        this.avvisoService = avvisoService;
    }

    @Override
    public ResponseEntity<ListPendenze200Response> listPendenze(Integer page,
                                                                Integer limit,
                                                                String sort,
                                                                Boolean total,
                                                                String idPendenza,
                                                                String numeroAvviso,
                                                                String idDominio,
                                                                String identificativoDebitore) {
        rejectUnsupportedQueryParams(currentRequest, LIST_PENDENZE_QUERY_PARAMS);
        PendenzaListQuery query = new PendenzaListQuery(
                page == null ? 1 : page,
                limit == null ? 25 : limit,
                sort,
                total,
                idPendenza,
                numeroAvviso,
                idDominio,
                identificativoDebitore);
        return ResponseEntity.ok(service.list(query, currentRequest));
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
