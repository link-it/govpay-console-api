package it.govpay.console.impostazioni;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import it.govpay.console.api.ImpostazioniHardeningApi;
import it.govpay.console.model.ImpostazioniHardening;
import it.govpay.console.model.ImpostazioniHardeningCredenziali;
import it.govpay.console.model.JsonPatchOperation;
import jakarta.servlet.http.HttpServletRequest;

@RestController
public class HardeningController implements ImpostazioniHardeningApi {

    private final HardeningService service;
    private final HttpServletRequest currentRequest;

    public HardeningController(HardeningService service,
                               HttpServletRequest currentRequest) {
        this.service = service;
        this.currentRequest = currentRequest;
    }

    @Override
    public ResponseEntity<ImpostazioniHardening> getImpostazioniHardening() {
        return service.get();
    }

    @Override
    public ResponseEntity<ImpostazioniHardening> replaceImpostazioniHardening(
            String ifMatch, ImpostazioniHardening impostazioniHardening) {
        return service.replace(impostazioniHardening, ifMatch, currentRequest);
    }

    @Override
    public ResponseEntity<ImpostazioniHardening> patchImpostazioniHardening(
            String ifMatch, List<JsonPatchOperation> jsonPatchOperation) {
        return service.patch(jsonPatchOperation, ifMatch, currentRequest);
    }

    @Override
    public ResponseEntity<Void> putCredenzialiImpostazioniHardening(
            ImpostazioniHardeningCredenziali impostazioniHardeningCredenziali) {
        return service.putCredenziali(impostazioniHardeningCredenziali, currentRequest);
    }
}
