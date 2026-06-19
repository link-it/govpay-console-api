package it.govpay.console.intermediario;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import it.govpay.console.api.IntermediariApi;
import it.govpay.console.model.Intermediario;
import it.govpay.console.model.IntermediarioCreate;
import it.govpay.console.model.IntermediarioReplace;
import it.govpay.console.model.JsonPatchOperation;
import it.govpay.console.model.ListIntermediari200Response;
import jakarta.servlet.http.HttpServletRequest;

@RestController
public class IntermediarioController implements IntermediariApi {

    private final IntermediarioService service;

    @Autowired(required = false)
    private HttpServletRequest currentRequest;

    public IntermediarioController(IntermediarioService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ListIntermediari200Response> listIntermediari(Integer page,
                                                                        Integer limit,
                                                                        String sort,
                                                                        Boolean total,
                                                                        String codIntermediario,
                                                                        String denominazione,
                                                                        Boolean abilitato) {
        IntermediarioListQuery query = new IntermediarioListQuery(
                page == null ? 1 : page,
                limit == null ? 25 : limit,
                sort,
                total,
                codIntermediario,
                denominazione,
                abilitato);
        return ResponseEntity.ok(service.list(query));
    }

    @Override
    public ResponseEntity<Intermediario> getIntermediario(String idIntermediario) {
        return service.get(idIntermediario);
    }

    @Override
    public ResponseEntity<Intermediario> createIntermediario(IntermediarioCreate intermediarioCreate) {
        return service.create(intermediarioCreate, currentRequest);
    }

    @Override
    public ResponseEntity<Intermediario> replaceIntermediario(String idIntermediario,
                                                              String ifMatch,
                                                              IntermediarioReplace intermediarioReplace) {
        return service.replace(idIntermediario, intermediarioReplace, ifMatch, currentRequest);
    }

    @Override
    public ResponseEntity<Intermediario> patchIntermediario(String idIntermediario,
                                                            String ifMatch,
                                                            List<JsonPatchOperation> jsonPatchOperation) {
        return service.patch(idIntermediario, jsonPatchOperation, ifMatch, currentRequest);
    }
}
