package it.govpay.console.entrata;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import it.govpay.console.api.EntrateApi;
import it.govpay.console.model.Entrata;
import it.govpay.console.model.EntrataCreate;
import it.govpay.console.model.EntrataReplace;
import it.govpay.console.model.JsonPatchOperation;
import it.govpay.console.model.ListEntrate200Response;
import jakarta.servlet.http.HttpServletRequest;

@RestController
public class EntrataController implements EntrateApi {

    private final EntrataService service;

    @Autowired(required = false)
    private HttpServletRequest currentRequest;

    public EntrataController(EntrataService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ListEntrate200Response> listEntrate(Integer page,
                                                              Integer limit,
                                                              String sort,
                                                              Boolean total,
                                                              String idEntrata,
                                                              String descrizione) {
        EntrataListQuery query = new EntrataListQuery(
                page == null ? 1 : page,
                limit == null ? 25 : limit,
                sort,
                total,
                idEntrata,
                descrizione);
        return ResponseEntity.ok(service.list(query));
    }

    @Override
    public ResponseEntity<Entrata> getEntrata(String idEntrata) {
        return service.get(idEntrata);
    }

    @Override
    public ResponseEntity<Entrata> createEntrata(EntrataCreate entrataCreate) {
        return service.create(entrataCreate, currentRequest);
    }

    @Override
    public ResponseEntity<Entrata> replaceEntrata(String idEntrata,
                                                  String ifMatch,
                                                  EntrataReplace entrataReplace) {
        return service.replace(idEntrata, entrataReplace, ifMatch, currentRequest);
    }

    @Override
    public ResponseEntity<Entrata> patchEntrata(String idEntrata,
                                                String ifMatch,
                                                List<JsonPatchOperation> jsonPatchOperation) {
        return service.patch(idEntrata, jsonPatchOperation, ifMatch, currentRequest);
    }
}
