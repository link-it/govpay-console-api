package it.govpay.console.impostazioni;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.audit.AuditService;
import it.govpay.console.connettore.ConnettoreProprietaKeys;
import it.govpay.console.connettore.ConnettoreStore;
import it.govpay.console.model.AclServizio;
import it.govpay.console.model.ConnettoreCredenziali;
import it.govpay.console.model.ImpostazioniServizioGDE;
import it.govpay.console.security.AclAuthorizer;
import it.govpay.console.security.CurrentOperatorService;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.web.IfMatchMismatchException;
import it.govpay.console.web.PreconditionRequiredException;
import it.govpay.console.web.RepresentationEtag;
import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.ObjectMapper;

/**
 * Gestisce l'unico connettore verso il Giornale degli Eventi (GDE), memorizzato
 * come proprieta' EAV su {@code connettori} sotto il codice fisso
 * {@code govpay_gde_api} (stessa costante V1 {@code Configurazione.COD_CONNETTORE_GDE}).
 * A differenza dei connettori di intermediario/dominio/applicazione, GDE e' un
 * singleton globale: nessuna entity "owner" da caricare, il codice connettore
 * non va ne' generato ne' risolto.
 */
@Service
public class ServizioGdeService {

    public static final String COD_CONNETTORE_GDE = "govpay_gde_api";

    public static final String AZIONE_AUDIT_MODIFICA = "IMPOSTAZIONI_SERVIZIO_GDE_MODIFICA";
    public static final String AZIONE_AUDIT_CREDENZIALI = "IMPOSTAZIONI_SERVIZIO_GDE_CREDENZIALI";

    private final ConnettoreStore store;
    private final ServizioGdeMapper mapper;
    private final ObjectMapper objectMapper;
    private final AclAuthorizer aclAuthorizer;
    private final CurrentOperatorService currentOperatorService;
    private final AuditService auditService;

    public ServizioGdeService(ConnettoreStore store,
                              ServizioGdeMapper mapper,
                              ObjectMapper objectMapper,
                              AclAuthorizer aclAuthorizer,
                              CurrentOperatorService currentOperatorService,
                              AuditService auditService) {
        this.store = store;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.aclAuthorizer = aclAuthorizer;
        this.currentOperatorService = currentOperatorService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public ResponseEntity<ImpostazioniServizioGDE> get() {
        aclAuthorizer.requireLettura(AclServizio.CONFIGURAZIONE_E_MANUTENZIONE);
        ImpostazioniServizioGDE dto = mapper.toDto(store.read(COD_CONNETTORE_GDE));
        return ResponseEntity.ok()
                .eTag(RepresentationEtag.of(dto, objectMapper))
                .body(dto);
    }

    @Transactional
    public ResponseEntity<ImpostazioniServizioGDE> replace(ImpostazioniServizioGDE body, String ifMatch,
                                                           HttpServletRequest request) {
        aclAuthorizer.requireScrittura(AclServizio.CONFIGURAZIONE_E_MANUTENZIONE);
        checkIfMatch(ifMatch, mapper.toDto(store.read(COD_CONNETTORE_GDE)));

        store.upsert(COD_CONNETTORE_GDE, mapper.toConfigMap(body), ConnettoreProprietaKeys.CONFIG_KEYS);

        audit(AZIONE_AUDIT_MODIFICA, request);

        ImpostazioniServizioGDE updated = mapper.toDto(store.read(COD_CONNETTORE_GDE));
        return ResponseEntity.ok()
                .eTag(RepresentationEtag.of(updated, objectMapper))
                .body(updated);
    }

    @Transactional
    public ResponseEntity<Void> putCredenziali(ConnettoreCredenziali credenziali, HttpServletRequest request) {
        aclAuthorizer.requireScrittura(AclServizio.CONFIGURAZIONE_E_MANUTENZIONE);

        store.upsert(COD_CONNETTORE_GDE, mapper.toCredenzialiMap(credenziali), ConnettoreProprietaKeys.CREDENTIAL_KEYS);

        audit(AZIONE_AUDIT_CREDENZIALI, request);
        return ResponseEntity.noContent().build();
    }

    private void checkIfMatch(String ifMatch, Object dto) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new PreconditionRequiredException(
                    "Header 'If-Match' obbligatorio per le operazioni di modifica.");
        }
        if (!RepresentationEtag.matches(ifMatch, dto, objectMapper)) {
            throw new IfMatchMismatchException(
                    "L'header 'If-Match' non corrisponde alla configurazione corrente del connettore.");
        }
    }

    private void audit(String azione, HttpServletRequest request) {
        OperatoreCorrente operatore = currentOperatorService.get();
        Map<String, Object> dettaglio = new HashMap<>();
        dettaglio.put("connettore", COD_CONNETTORE_GDE);
        auditService.registra(azione, 0L, dettaglio, operatore, request);
    }
}
