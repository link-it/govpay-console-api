package it.govpay.console.impostazioni;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import it.govpay.console.api.ImpostazioniTracciatiCsvApi;
import it.govpay.console.model.ImpostazioniTracciatiCsv;
import it.govpay.console.model.JsonPatchOperation;
import jakarta.servlet.http.HttpServletRequest;

@RestController
public class TracciatiCsvController implements ImpostazioniTracciatiCsvApi {

    private final TracciatiCsvService service;

    @Autowired(required = false)
    private HttpServletRequest currentRequest;

    public TracciatiCsvController(TracciatiCsvService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ImpostazioniTracciatiCsv> getImpostazioniTracciatiCsv() {
        return service.get();
    }

    @Override
    public ResponseEntity<ImpostazioniTracciatiCsv> replaceImpostazioniTracciatiCsv(
            String ifMatch, ImpostazioniTracciatiCsv impostazioniTracciatiCsv) {
        return service.replace(impostazioniTracciatiCsv, ifMatch, currentRequest);
    }

    @Override
    public ResponseEntity<ImpostazioniTracciatiCsv> patchImpostazioniTracciatiCsv(
            String ifMatch, List<JsonPatchOperation> jsonPatchOperation) {
        return service.patch(jsonPatchOperation, ifMatch, currentRequest);
    }
}
