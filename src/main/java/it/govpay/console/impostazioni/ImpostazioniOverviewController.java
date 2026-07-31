package it.govpay.console.impostazioni;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import it.govpay.console.api.ImpostazioniApi;
import it.govpay.console.model.ImpostazioniOverview;

@RestController
public class ImpostazioniOverviewController implements ImpostazioniApi {

    private final ImpostazioniOverviewService service;

    public ImpostazioniOverviewController(ImpostazioniOverviewService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ImpostazioniOverview> getImpostazioniOverview() {
        return service.get();
    }
}
