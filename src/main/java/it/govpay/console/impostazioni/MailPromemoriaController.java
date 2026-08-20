package it.govpay.console.impostazioni;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import it.govpay.console.api.ImpostazioniMailTemplatePromemoriaApi;
import it.govpay.console.model.ImpostazioniMailTemplatePromemoria;
import it.govpay.console.model.JsonPatchOperation;
import jakarta.servlet.http.HttpServletRequest;

@RestController
public class MailPromemoriaController implements ImpostazioniMailTemplatePromemoriaApi {

    private final MailPromemoriaService service;

    @Autowired(required = false)
    private HttpServletRequest currentRequest;

    public MailPromemoriaController(MailPromemoriaService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ImpostazioniMailTemplatePromemoria> getImpostazioniMailTemplatePromemoria() {
        return service.get();
    }

    @Override
    public ResponseEntity<ImpostazioniMailTemplatePromemoria> replaceImpostazioniMailTemplatePromemoria(
            String ifMatch, ImpostazioniMailTemplatePromemoria impostazioniMailTemplatePromemoria) {
        return service.replace(impostazioniMailTemplatePromemoria, ifMatch, currentRequest);
    }

    @Override
    public ResponseEntity<ImpostazioniMailTemplatePromemoria> patchImpostazioniMailTemplatePromemoria(
            String ifMatch, List<JsonPatchOperation> jsonPatchOperation) {
        return service.patch(jsonPatchOperation, ifMatch, currentRequest);
    }
}
