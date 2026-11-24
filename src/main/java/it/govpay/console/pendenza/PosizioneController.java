package it.govpay.console.pendenza;

import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import it.govpay.console.api.PosizioniDebitorieApi;
import it.govpay.console.model.PosizioneDebitoria;
import it.govpay.console.web.ListQueryValidator;
import jakarta.servlet.http.HttpServletRequest;

@RestController
public class PosizioneController implements PosizioniDebitorieApi {

    private static final Set<String> GET_POSIZIONE_QUERY_PARAMS = Set.of();

    private final PosizioneService service;
    private final HttpServletRequest currentRequest;

    public PosizioneController(PosizioneService service, HttpServletRequest currentRequest) {
        this.service = service;
        this.currentRequest = currentRequest;
    }

    @Override
    public ResponseEntity<PosizioneDebitoria> getPosizioneDebitoria(String idA2A, String idPosizioneDebitoria) {
        ListQueryValidator.rejectUnsupported(currentRequest, GET_POSIZIONE_QUERY_PARAMS);
        return ResponseEntity.ok(service.get(idA2A, idPosizioneDebitoria, currentRequest));
    }
}
