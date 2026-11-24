package it.govpay.console.soggetto;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.audit.AuditService;
import it.govpay.console.model.Soggetto;
import it.govpay.console.pendenza.PendenzaLettura;
import it.govpay.console.security.CurrentOperatorService;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.pendenze.entity.Pendenza;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Servizio dell'endpoint {@code GET /pendenze/{idA2A}/{idPendenza}/informazioniDebitore}.
 *
 * <p>Espone i dati anagrafici/contatti del debitore associato a una pendenza.
 *
 * <p>Ogni 200 viene tracciato in {@code gp_audit} con azione
 * {@link #AZIONE_AUDIT_VISUALIZZA}. L'audit viene scritto <i>dopo</i> il mapping
 * (prima del return) per non sporcare la tabella in caso di errori a monte.
 *
 * <p>ACL: 404 anti-leak, la stessa lettura del dettaglio della pendenza.
 */
@Service
public class InformazioniDebitoreService {

    public static final String AZIONE_AUDIT_VISUALIZZA = "PENDENZA_VISUALIZZA_DEBITORE";

    private static final Logger log = LoggerFactory.getLogger(InformazioniDebitoreService.class);

    private final PendenzaLettura lettura;
    private final SoggettoMapper soggettoMapper;
    private final AuditService auditService;
    private final CurrentOperatorService currentOperatorService;

    public InformazioniDebitoreService(PendenzaLettura lettura,
                                       SoggettoMapper soggettoMapper,
                                       AuditService auditService,
                                       CurrentOperatorService currentOperatorService) {
        this.lettura = lettura;
        this.soggettoMapper = soggettoMapper;
        this.auditService = auditService;
        this.currentOperatorService = currentOperatorService;
    }

    @Transactional(readOnly = true)
    public Soggetto get(String idA2A, String idPendenza, HttpServletRequest request) {
        OperatoreCorrente operatore = currentOperatorService.get();
        log.debug("getInformazioniDebitore idA2A={} idPendenza={} operatore={}",
                idA2A, idPendenza, operatore.principal());

        Pendenza pendenza = lettura.leggiVisibile(idA2A, idPendenza, operatore).pendenza();

        // Il debitore della pendenza e' quello della sua opzione di pagamento.
        Soggetto soggetto = soggettoMapper.toSoggetto(pendenza.getOpzionePagamento().getPdDebitore().getDebitore());

        Map<String, Object> dettaglio = new HashMap<>();
        dettaglio.put("idA2A", idA2A);
        dettaglio.put("idPendenza", idPendenza);
        dettaglio.put("identificativoDebitore", soggetto.getIdentificativo());
        auditService.registra(AZIONE_AUDIT_VISUALIZZA, pendenza.getId(), dettaglio, operatore, request);

        return soggetto;
    }
}
