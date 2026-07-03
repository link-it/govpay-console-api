package it.govpay.console.connettoreintegrazione;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import it.govpay.console.api.ConnettoreIntegrazioneApi;
import it.govpay.console.model.ConnettoreCredenziali;
import it.govpay.console.model.ConnettoreIntegrazioneApplicazione;
import jakarta.servlet.http.HttpServletRequest;

@RestController
public class ConnettoreIntegrazioneController implements ConnettoreIntegrazioneApi {

    private final ConnettoreIntegrazioneService service;

    @Autowired(required = false)
    private HttpServletRequest currentRequest;

    public ConnettoreIntegrazioneController(ConnettoreIntegrazioneService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ConnettoreIntegrazioneApplicazione> getConnettoreIntegrazione(String idA2A) {
        return service.get(idA2A);
    }

    @Override
    public ResponseEntity<ConnettoreIntegrazioneApplicazione> replaceConnettoreIntegrazione(
            String idA2A, String ifMatch, ConnettoreIntegrazioneApplicazione connettoreIntegrazioneApplicazione) {
        return service.replace(idA2A, connettoreIntegrazioneApplicazione, ifMatch, currentRequest);
    }

    @Override
    public ResponseEntity<Void> putCredenzialiConnettoreIntegrazione(
            String idA2A, ConnettoreCredenziali connettoreCredenziali) {
        return service.putCredenziali(idA2A, connettoreCredenziali, currentRequest);
    }
}
