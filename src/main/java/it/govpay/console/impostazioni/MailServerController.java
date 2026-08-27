package it.govpay.console.impostazioni;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import it.govpay.console.api.ImpostazioniMailServerApi;
import it.govpay.console.model.ImpostazioniMailServer;
import it.govpay.console.model.ImpostazioniMailServerCredenziali;
import it.govpay.console.model.JsonPatchOperation;
import jakarta.servlet.http.HttpServletRequest;

@RestController
public class MailServerController implements ImpostazioniMailServerApi {

    private final MailServerService service;
    private final HttpServletRequest currentRequest;

    public MailServerController(MailServerService service,
                                HttpServletRequest currentRequest) {
        this.service = service;
        this.currentRequest = currentRequest;
    }

    @Override
    public ResponseEntity<ImpostazioniMailServer> getImpostazioniMailServer() {
        return service.get();
    }

    @Override
    public ResponseEntity<ImpostazioniMailServer> replaceImpostazioniMailServer(
            String ifMatch, ImpostazioniMailServer impostazioniMailServer) {
        return service.replace(impostazioniMailServer, ifMatch, currentRequest);
    }

    @Override
    public ResponseEntity<ImpostazioniMailServer> patchImpostazioniMailServer(
            String ifMatch, List<JsonPatchOperation> jsonPatchOperation) {
        return service.patch(jsonPatchOperation, ifMatch, currentRequest);
    }

    @Override
    public ResponseEntity<Void> putPasswordImpostazioniMailServer(
            ImpostazioniMailServerCredenziali impostazioniMailServerCredenziali) {
        return service.putPassword(impostazioniMailServerCredenziali, currentRequest);
    }
}
