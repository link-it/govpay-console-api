package it.govpay.console.unitaoperativa;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import it.govpay.console.api.UnitaOperativeApi;
import it.govpay.console.model.JsonPatchOperation;
import it.govpay.console.model.ListUnitaOperative200Response;
import it.govpay.console.model.UnitaOperativa;
import it.govpay.console.model.UnitaOperativaCreate;
import it.govpay.console.model.UnitaOperativaReplace;
import jakarta.servlet.http.HttpServletRequest;

@RestController
public class UnitaOperativaController implements UnitaOperativeApi {

    private final UnitaOperativaService service;

    @Autowired(required = false)
    private HttpServletRequest currentRequest;

    public UnitaOperativaController(UnitaOperativaService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ListUnitaOperative200Response> listUnitaOperative(String idDominio,
                                                                            Integer page,
                                                                            Integer limit,
                                                                            String sort,
                                                                            Boolean total,
                                                                            String ragioneSociale,
                                                                            Boolean abilitato) {
        UnitaOperativaListQuery query = new UnitaOperativaListQuery(
                page == null ? 1 : page,
                limit == null ? 25 : limit,
                sort,
                total,
                ragioneSociale,
                abilitato);
        return ResponseEntity.ok(service.list(idDominio, query));
    }

    @Override
    public ResponseEntity<UnitaOperativa> getUnitaOperativa(String idDominio, String idUnitaOperativa) {
        return service.get(idDominio, idUnitaOperativa);
    }

    @Override
    public ResponseEntity<UnitaOperativa> createUnitaOperativa(String idDominio,
                                                               UnitaOperativaCreate unitaOperativaCreate) {
        return service.create(idDominio, unitaOperativaCreate, currentRequest);
    }

    @Override
    public ResponseEntity<UnitaOperativa> replaceUnitaOperativa(String idDominio,
                                                                String idUnitaOperativa,
                                                                String ifMatch,
                                                                UnitaOperativaReplace unitaOperativaReplace) {
        return service.replace(idDominio, idUnitaOperativa, unitaOperativaReplace, ifMatch, currentRequest);
    }

    @Override
    public ResponseEntity<UnitaOperativa> patchUnitaOperativa(String idDominio,
                                                              String idUnitaOperativa,
                                                              String ifMatch,
                                                              List<JsonPatchOperation> jsonPatchOperation) {
        return service.patch(idDominio, idUnitaOperativa, jsonPatchOperation, ifMatch, currentRequest);
    }
}
