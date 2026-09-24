package it.govpay.console.logging;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.govpay.common.logging.level.AbstractLogLevelController;
import it.govpay.common.logging.level.DynamicLogLevelService;
import it.govpay.common.logging.level.LivelloLogRequest;
import it.govpay.common.logging.level.LivelloLoggerInfo;
import it.govpay.console.model.AclServizio;
import it.govpay.console.security.AclAuthorizer;

/**
 * Gestione a runtime dei livelli di log (BP-LOG-1).
 * <p>
 * La modifica ha effetto immediato su questo nodo ed e' persistita sulla riga
 * {@code log_level} della tabella {@code configurazione}: gli altri nodi del
 * cluster la applicano al proprio refresh periodico.
 * <p>
 * Gli endpoint non fanno parte del contratto OpenAPI della console: sono di
 * amministrazione e, oltre all'autenticazione garantita dalla catena di
 * sicurezza (default deny), richiedono il diritto ACL sul servizio
 * {@code Configurazione e manutenzione} (BP-SEC-2) — lettura per le
 * consultazioni, scrittura per le modifiche.
 */
@RestController
@RequestMapping("/admin/logging")
public class LogLevelController extends AbstractLogLevelController {

    private static final AclServizio SERVIZIO = AclServizio.CONFIGURAZIONE_E_MANUTENZIONE;

    private final AclAuthorizer aclAuthorizer;

    public LogLevelController(DynamicLogLevelService logLevelService, AclAuthorizer aclAuthorizer) {
        super(logLevelService);
        this.aclAuthorizer = aclAuthorizer;
    }

    @GetMapping("/loggers")
    public ResponseEntity<List<LivelloLoggerInfo>> loggers() {
        aclAuthorizer.requireLettura(SERVIZIO);
        return getLoggers();
    }

    @GetMapping("/loggers/dinamici")
    public ResponseEntity<Map<String, String>> dinamici() {
        aclAuthorizer.requireLettura(SERVIZIO);
        return getLivelliDinamici();
    }

    @PutMapping("/loggers/{logger}")
    public ResponseEntity<Object> impostaLivello(@PathVariable String logger,
            @RequestBody LivelloLogRequest richiesta) {
        aclAuthorizer.requireScrittura(SERVIZIO);
        return setLivello(logger, richiesta);
    }

    @DeleteMapping("/loggers/{logger}")
    public ResponseEntity<Object> ripristinaLivello(@PathVariable String logger) {
        aclAuthorizer.requireScrittura(SERVIZIO);
        return rimuoviLivello(logger);
    }

    @DeleteMapping("/loggers")
    public ResponseEntity<Object> ripristinaTutti() {
        aclAuthorizer.requireScrittura(SERVIZIO);
        return resetLivelli();
    }

    @PostMapping("/loggers/refresh")
    public ResponseEntity<Map<String, String>> refresh() {
        aclAuthorizer.requireScrittura(SERVIZIO);
        return refreshLivelli();
    }
}
