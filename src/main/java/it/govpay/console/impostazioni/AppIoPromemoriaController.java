package it.govpay.console.impostazioni;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import it.govpay.console.api.ImpostazioniAppIoTemplatePromemoriaApi;
import it.govpay.console.model.ImpostazioniAppIoTemplatePromemoria;
import it.govpay.console.model.JsonPatchOperation;
import jakarta.servlet.http.HttpServletRequest;

@RestController
public class AppIoPromemoriaController implements ImpostazioniAppIoTemplatePromemoriaApi {

    private final AppIoPromemoriaService service;
    private final HttpServletRequest currentRequest;

    public AppIoPromemoriaController(AppIoPromemoriaService service,
                                     HttpServletRequest currentRequest) {
        this.service = service;
        this.currentRequest = currentRequest;
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
