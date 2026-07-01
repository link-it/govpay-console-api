package it.govpay.console.contoaccredito;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import it.govpay.console.api.ContiAccreditoApi;
import it.govpay.console.model.ContoAccredito;
import it.govpay.console.model.ContoAccreditoCreate;
import it.govpay.console.model.ContoAccreditoReplace;
import it.govpay.console.model.JsonPatchOperation;
import it.govpay.console.model.ListContiAccredito200Response;
import jakarta.servlet.http.HttpServletRequest;

@RestController
public class ContoAccreditoController implements ContiAccreditoApi {

    private final ContoAccreditoService service;

    @Autowired(required = false)
    private HttpServletRequest currentRequest;

    public ContoAccreditoController(ContoAccreditoService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ListContiAccredito200Response> listContiAccredito(String idDominio,
                                                                            Integer page,
                                                                            Integer limit,
                                                                            String sort,
                                                                            Boolean total,
                                                                            String descrizione,
                                                                            Boolean abilitato) {
        ContoAccreditoListQuery query = new ContoAccreditoListQuery(
                page == null ? 1 : page,
                limit == null ? 25 : limit,
                sort,
                total,
                descrizione,
                abilitato);
        return ResponseEntity.ok(service.list(idDominio, query));
    }

    @Override
    public ResponseEntity<ContoAccredito> getContoAccredito(String idDominio, String ibanAccredito) {
        return service.get(idDominio, ibanAccredito);
    }

    @Override
    public ResponseEntity<ContoAccredito> createContoAccredito(String idDominio,
                                                               ContoAccreditoCreate contoAccreditoCreate) {
        return service.create(idDominio, contoAccreditoCreate, currentRequest);
    }

    @Override
    public ResponseEntity<ContoAccredito> replaceContoAccredito(String idDominio,
                                                                String ibanAccredito,
                                                                String ifMatch,
                                                                ContoAccreditoReplace contoAccreditoReplace) {
        return service.replace(idDominio, ibanAccredito, contoAccreditoReplace, ifMatch, currentRequest);
    }

    @Override
    public ResponseEntity<ContoAccredito> patchContoAccredito(String idDominio,
                                                              String ibanAccredito,
                                                              String ifMatch,
                                                              List<JsonPatchOperation> jsonPatchOperation) {
        return service.patch(idDominio, ibanAccredito, jsonPatchOperation, ifMatch, currentRequest);
    }
}
