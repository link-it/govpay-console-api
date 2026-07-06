package it.govpay.console.operatore;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import it.govpay.console.api.OperatoriApi;
import it.govpay.console.model.JsonPatchOperation;
import it.govpay.console.model.ListOperatori200Response;
import it.govpay.console.model.Operatore;
import it.govpay.console.model.OperatoreCreate;
import it.govpay.console.model.OperatoreReplace;
import it.govpay.console.model.RichiestaCambioPassword;
import jakarta.servlet.http.HttpServletRequest;

@RestController
public class OperatoreController implements OperatoriApi {

    private final OperatoreService service;

    @Autowired(required = false)
    private HttpServletRequest currentRequest;

    public OperatoreController(OperatoreService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ListOperatori200Response> listOperatori(Integer page, Integer limit, String sort,
                                                                  Boolean total, String principal, String nome,
                                                                  Boolean abilitato) {
        OperatoreListQuery query = new OperatoreListQuery(
                page == null ? 1 : page,
                limit == null ? 25 : limit,
                sort,
                total,
                principal,
                nome,
                abilitato);
        return ResponseEntity.ok(service.list(query));
    }

    @Override
    public ResponseEntity<Operatore> getOperatore(String principal) {
        return service.get(principal);
    }

    @Override
    public ResponseEntity<Operatore> createOperatore(OperatoreCreate operatoreCreate) {
        return service.create(operatoreCreate, currentRequest);
    }

    @Override
    public ResponseEntity<Operatore> replaceOperatore(String principal, String ifMatch,
                                                      OperatoreReplace operatoreReplace) {
        return service.replace(principal, operatoreReplace, ifMatch, currentRequest);
    }

    @Override
    public ResponseEntity<Operatore> patchOperatore(String principal, String ifMatch,
                                                    List<JsonPatchOperation> jsonPatchOperation) {
        return service.patch(principal, jsonPatchOperation, ifMatch, currentRequest);
    }

    @Override
    public ResponseEntity<Void> putPasswordOperatore(String principal,
                                                     RichiestaCambioPassword richiestaCambioPassword) {
        return service.putPassword(principal, richiestaCambioPassword, currentRequest);
    }
}
