package it.govpay.console.impostazioni;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import it.govpay.console.api.ImpostazioniAppIoTemplatePromemoriaApi;
import it.govpay.console.model.ImpostazioniAppIoTemplatePromemoria;
import it.govpay.console.model.JsonPatchOperation;
import jakarta.servlet.http.HttpServletRequest;

@RestController
public class AppIoPromemoriaController implements ImpostazioniAppIoTemplatePromemoriaApi {

    private final AppIoPromemoriaService service;

    @Autowired(required = false)
    private HttpServletRequest currentRequest;

    public AppIoPromemoriaController(AppIoPromemoriaService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ImpostazioniAppIoTemplatePromemoria> getImpostazioniAppIoTemplatePromemoria() {
        return service.get();
    }

    @Override
    public ResponseEntity<ImpostazioniAppIoTemplatePromemoria> replaceImpostazioniAppIoTemplatePromemoria(
            String ifMatch, ImpostazioniAppIoTemplatePromemoria impostazioniAppIoTemplatePromemoria) {
        return service.replace(impostazioniAppIoTemplatePromemoria, ifMatch, currentRequest);
    }

    @Override
    public ResponseEntity<ImpostazioniAppIoTemplatePromemoria> patchImpostazioniAppIoTemplatePromemoria(
            String ifMatch, List<JsonPatchOperation> jsonPatchOperation) {
        return service.patch(jsonPatchOperation, ifMatch, currentRequest);
    }
}
