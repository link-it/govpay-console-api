package it.govpay.console.sla;

import java.time.LocalDate;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import it.govpay.console.api.MetricheApi;
import it.govpay.console.model.AclServizio;
import it.govpay.console.model.SlaResponse;
import it.govpay.console.security.AclAuthorizer;

@RestController
public class SlaController implements MetricheApi {

    private final SlaService slaService;
    private final AclAuthorizer aclAuthorizer;

    public SlaController(SlaService slaService, AclAuthorizer aclAuthorizer) {
        this.slaService = slaService;
        this.aclAuthorizer = aclAuthorizer;
    }

    @Override
    public ResponseEntity<SlaResponse> getMetricheSla(LocalDate dataDa, LocalDate dataA) {
        aclAuthorizer.requireLettura(AclServizio.CONFIGURAZIONE_E_MANUTENZIONE);
        return ResponseEntity.ok(slaService.calcola(dataDa, dataA));
    }
}
