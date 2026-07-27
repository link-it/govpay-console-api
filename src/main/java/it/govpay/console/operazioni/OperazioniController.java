package it.govpay.console.operazioni;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import it.govpay.console.api.ManutenzioneOperazioniApi;
import it.govpay.console.model.Operazione;

@RestController
public class OperazioniController implements ManutenzioneOperazioniApi {

    private final OperazioniService service;

    public OperazioniController(OperazioniService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<List<Operazione>> listOperazioni() {
        return ResponseEntity.ok(service.getCatalogo());
    }
}
