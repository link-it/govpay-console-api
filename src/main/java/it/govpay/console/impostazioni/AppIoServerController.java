package it.govpay.console.impostazioni;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import it.govpay.console.api.ImpostazioniAppIoServerApi;
import it.govpay.console.model.ConnettoreCredenziali;
import it.govpay.console.model.ImpostazioniAppIoServer;
import it.govpay.console.model.JsonPatchOperation;
import jakarta.servlet.http.HttpServletRequest;

@RestController
public class AppIoServerController implements ImpostazioniAppIoServerApi {

    private final AppIoServerService service;

    @Autowired(required = false)
    private HttpServletRequest currentRequest;

    public AppIoServerController(AppIoServerService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ImpostazioniAppIoServer> getImpostazioniAppIoServer() {
        return service.get();
    }

    @Override
    public ResponseEntity<ImpostazioniAppIoServer> replaceImpostazioniAppIoServer(
            String ifMatch, ImpostazioniAppIoServer impostazioniAppIoServer) {
        return service.replace(impostazioniAppIoServer, ifMatch, currentRequest);
    }

    @Override
    public ResponseEntity<ImpostazioniAppIoServer> patchImpostazioniAppIoServer(
            String ifMatch, List<JsonPatchOperation> jsonPatchOperation) {
        return service.patch(jsonPatchOperation, ifMatch, currentRequest);
    }

    @Override
    public ResponseEntity<Void> putCredenzialiImpostazioniAppIoServer(ConnettoreCredenziali connettoreCredenziali) {
        return service.putCredenziali(connettoreCredenziali, currentRequest);
    }
}
