package it.govpay.console.impostazioni;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import it.govpay.console.api.ImpostazioniApi;
import it.govpay.console.model.ConnettoreCredenziali;
import it.govpay.console.model.ImpostazioniServizioGDE;
import jakarta.servlet.http.HttpServletRequest;

@RestController
public class ServizioGdeController implements ImpostazioniApi {

    private final ServizioGdeService service;

    @Autowired(required = false)
    private HttpServletRequest currentRequest;

    public ServizioGdeController(ServizioGdeService service) {
        this.service = service;
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
