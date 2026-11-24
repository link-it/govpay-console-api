package it.govpay.console.pendenza;

import java.util.Set;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import it.govpay.console.api.PosizioniDebitorieApi;
import it.govpay.console.avviso.PosizioneAvvisoService;
import it.govpay.console.model.PosizioneDebitoria;
import it.govpay.console.web.ListQueryValidator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
public class PosizioneController implements PosizioniDebitorieApi {

    private static final Set<String> GET_POSIZIONE_QUERY_PARAMS = Set.of();

    private static final Set<String> GET_AVVISO_QUERY_PARAMS = Set.of("idDebitore");

    private final PosizioneService service;
    private final PosizioneAvvisoService avvisoService;
    private final HttpServletRequest currentRequest;
    private final HttpServletResponse currentResponse;

    public PosizioneController(PosizioneService service, PosizioneAvvisoService avvisoService,
                               HttpServletRequest currentRequest, HttpServletResponse currentResponse) {
        this.service = service;
        this.avvisoService = avvisoService;
        this.currentRequest = currentRequest;
        this.currentResponse = currentResponse;
    }

    @Override
    public ResponseEntity<PosizioneDebitoria> getPosizioneDebitoria(String idA2A, String idPosizioneDebitoria) {
        ListQueryValidator.rejectUnsupported(currentRequest, GET_POSIZIONE_QUERY_PARAMS);
        return ResponseEntity.ok(service.get(idA2A, idPosizioneDebitoria, currentRequest));
    }

    /**
     * Il PDF e' scritto in streaming dal servizio direttamente sulla risposta (evitando i
     * {@code HttpMessageConverter}): il controller risponde solo con lo status.
     */
    @Override
    public ResponseEntity<Resource> getAvvisoPosizioneDebitoria(String idA2A, String idPosizioneDebitoria,
                                                                String idDebitore) {
        ListQueryValidator.rejectUnsupported(currentRequest, GET_AVVISO_QUERY_PARAMS);
        avvisoService.stampa(idA2A, idPosizioneDebitoria, idDebitore, currentRequest, currentResponse);
        return ResponseEntity.ok().build();
    }
}
