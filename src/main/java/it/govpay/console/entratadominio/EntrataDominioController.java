package it.govpay.console.entratadominio;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import it.govpay.console.api.EntrateDominioApi;
import it.govpay.console.model.EntrataDominio;
import it.govpay.console.model.EntrataDominioCreate;
import it.govpay.console.model.EntrataDominioReplace;
import it.govpay.console.model.JsonPatchOperation;
import it.govpay.console.model.ListEntrateDominio200Response;
import jakarta.servlet.http.HttpServletRequest;

@RestController
public class EntrataDominioController implements EntrateDominioApi {

    private final EntrataDominioService service;

    @Autowired(required = false)
    private HttpServletRequest currentRequest;

    public EntrataDominioController(EntrataDominioService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ListEntrateDominio200Response> listEntrateDominio(String idDominio,
                                                                            Integer page,
                                                                            Integer limit,
                                                                            String sort,
                                                                            Boolean total,
                                                                            String idEntrata,
                                                                            String descrizione,
                                                                            Boolean abilitato) {
        EntrataDominioListQuery query = new EntrataDominioListQuery(
                page == null ? 1 : page,
                limit == null ? 25 : limit,
                sort,
                total,
                idEntrata,
                descrizione,
                abilitato);
        return ResponseEntity.ok(service.list(idDominio, query));
    }

    @Override
    public ResponseEntity<EntrataDominio> getEntrataDominio(String idDominio, String idEntrata) {
        return service.get(idDominio, idEntrata);
    }

    @Override
    public ResponseEntity<EntrataDominio> createEntrataDominio(String idDominio,
                                                               EntrataDominioCreate entrataDominioCreate) {
        return service.create(idDominio, entrataDominioCreate, currentRequest);
    }

    @Override
    public ResponseEntity<EntrataDominio> replaceEntrataDominio(String idDominio,
                                                                String idEntrata,
                                                                String ifMatch,
                                                                EntrataDominioReplace entrataDominioReplace) {
        return service.replace(idDominio, idEntrata, entrataDominioReplace, ifMatch, currentRequest);
    }

    @Override
    public ResponseEntity<EntrataDominio> patchEntrataDominio(String idDominio,
                                                              String idEntrata,
                                                              String ifMatch,
                                                              List<JsonPatchOperation> jsonPatchOperation) {
        return service.patch(idDominio, idEntrata, jsonPatchOperation, ifMatch, currentRequest);
    }
}
