package it.govpay.console.pendenza;

import java.util.Collections;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import it.govpay.console.api.PendenzeApi;
import it.govpay.console.model.ListPendenze200Response;
import it.govpay.console.model.Pendenza;
import it.govpay.console.model.PendenzaExpand;
import it.govpay.console.web.BadRequestException;
import jakarta.servlet.http.HttpServletRequest;

@RestController
public class PendenzaController implements PendenzeApi {

    private static final Set<String> LIST_PENDENZE_QUERY_PARAMS = Set.of(
            "page", "limit", "sort", "total",
            "idPendenza", "numeroAvviso", "idDominio", "identificativoDebitore");

    private static final Set<String> GET_PENDENZA_QUERY_PARAMS = Set.of("expand");

    private final PendenzaService service;

    @Autowired(required = false)
    private HttpServletRequest currentRequest;

    public PendenzaController(PendenzaService service) {
        this.service = service;
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
