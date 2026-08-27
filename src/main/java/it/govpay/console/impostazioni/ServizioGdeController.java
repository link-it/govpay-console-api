package it.govpay.console.impostazioni;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import it.govpay.console.api.ImpostazioniServizioGdeApi;
import it.govpay.console.model.ConnettoreCredenziali;
import it.govpay.console.model.ImpostazioniServizioGDE;
import jakarta.servlet.http.HttpServletRequest;

@RestController
public class ServizioGdeController implements ImpostazioniServizioGdeApi {

    private final ServizioGdeService service;
    private final HttpServletRequest currentRequest;

    public ServizioGdeController(ServizioGdeService service,
                                 HttpServletRequest currentRequest) {
        this.service = service;
        this.currentRequest = currentRequest;
    }

    @Override
    public ResponseEntity<ImpostazioniServizioGDE> getImpostazioniServizioGDE() {
        return service.get();
    }

    @Override
    public ResponseEntity<ImpostazioniServizioGDE> replaceImpostazioniServizioGDE(
            String ifMatch, ImpostazioniServizioGDE impostazioniServizioGDE) {
        return service.replace(impostazioniServizioGDE, ifMatch, currentRequest);
    }

    @Override
    public ResponseEntity<Void> putCredenzialiImpostazioniServizioGDE(ConnettoreCredenziali connettoreCredenziali) {
        return service.putCredenziali(connettoreCredenziali, currentRequest);
    }
}
