package it.govpay.console.impostazioni;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import it.govpay.console.api.ImpostazioniGiornaleEventiApi;
import it.govpay.console.model.ImpostazioniGiornaleEventi;
import it.govpay.console.model.JsonPatchOperation;
import jakarta.servlet.http.HttpServletRequest;

@RestController
public class GiornaleEventiController implements ImpostazioniGiornaleEventiApi {

    private final GiornaleEventiService service;
    private final HttpServletRequest currentRequest;

    public GiornaleEventiController(GiornaleEventiService service,
                                    HttpServletRequest currentRequest) {
        this.service = service;
        this.currentRequest = currentRequest;
    }

    @Override
    public ResponseEntity<ImpostazioniGiornaleEventi> getImpostazioniGiornaleEventi() {
        return service.get();
    }

    @Override
    public ResponseEntity<ImpostazioniGiornaleEventi> replaceImpostazioniGiornaleEventi(
            String ifMatch, ImpostazioniGiornaleEventi impostazioniGiornaleEventi) {
        return service.replace(impostazioniGiornaleEventi, ifMatch, currentRequest);
    }

    @Override
    public ResponseEntity<ImpostazioniGiornaleEventi> patchImpostazioniGiornaleEventi(
            String ifMatch, List<JsonPatchOperation> jsonPatchOperation) {
        return service.patch(jsonPatchOperation, ifMatch, currentRequest);
    }
}
