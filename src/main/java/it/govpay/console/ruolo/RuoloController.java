package it.govpay.console.ruolo;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import it.govpay.console.api.RuoliApi;
import it.govpay.console.model.JsonPatchOperation;
import it.govpay.console.model.ListRuoli200Response;
import it.govpay.console.model.Ruolo;
import it.govpay.console.model.RuoloCreate;
import it.govpay.console.model.RuoloReplace;
import jakarta.servlet.http.HttpServletRequest;

@RestController
public class RuoloController implements RuoliApi {

    private final RuoloService service;

    @Autowired(required = false)
    private HttpServletRequest currentRequest;

    public RuoloController(RuoloService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ListRuoli200Response> listRuoli(Integer page, Integer limit, String sort,
                                                          Boolean total, String idRuolo) {
        RuoloListQuery query = new RuoloListQuery(
                page == null ? 1 : page,
                limit == null ? 25 : limit,
                sort,
                total,
                idRuolo);
        return ResponseEntity.ok(service.list(query));
    }

    @Override
    public ResponseEntity<Ruolo> getRuolo(String idRuolo) {
        return service.get(idRuolo);
    }

    @Override
    public ResponseEntity<Ruolo> createRuolo(RuoloCreate ruoloCreate) {
        return service.create(ruoloCreate, currentRequest);
    }

    @Override
    public ResponseEntity<Ruolo> replaceRuolo(String idRuolo, String ifMatch, RuoloReplace ruoloReplace) {
        return service.replace(idRuolo, ruoloReplace, ifMatch, currentRequest);
    }

    @Override
    public ResponseEntity<Ruolo> patchRuolo(String idRuolo, String ifMatch,
                                            List<JsonPatchOperation> jsonPatchOperation) {
        return service.patch(idRuolo, jsonPatchOperation, ifMatch, currentRequest);
    }
}
