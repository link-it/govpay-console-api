package it.govpay.console.impostazioni;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.audit.AuditService;
import it.govpay.console.connettore.ConnettoreProprietaKeys;
import it.govpay.console.connettore.ConnettoreStore;
import it.govpay.console.intermediario.JsonPatchApplier;
import it.govpay.console.model.AclServizio;
import it.govpay.console.model.ConnettoreCredenziali;
import it.govpay.console.model.ImpostazioniAppIoServer;
import it.govpay.console.model.JsonPatchOperation;
import it.govpay.console.security.AclAuthorizer;
import it.govpay.console.security.CurrentOperatorService;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.web.BadRequestException;
import it.govpay.console.web.IfMatchMismatchException;
import it.govpay.console.web.PreconditionRequiredException;
import it.govpay.console.web.RepresentationEtag;
import it.govpay.console.web.RepresentationValidator;
import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Gestisce l'unico connettore verso il servizio App IO, memorizzato come
 * proprieta' EAV su {@code connettori} sotto il codice fisso
 * {@code govpay_app_io_api} — stessa infrastruttura di {@link ServizioGdeService}
 * (V1 {@code AppIOBatch extends Connettore}, stesso auth completo).
 */
@Service
public class AppIoServerService {

    public static final String COD_CONNETTORE_APP_IO = "govpay_app_io_api";

    public static final String AZIONE_AUDIT_MODIFICA = "IMPOSTAZIONI_APP_IO_SERVER_MODIFICA";
    public static final String AZIONE_AUDIT_CREDENZIALI = "IMPOSTAZIONI_APP_IO_SERVER_CREDENZIALI";

    private final ConnettoreStore store;
    private final AppIoServerMapper mapper;
    private final ObjectMapper objectMapper;
    private final AclAuthorizer aclAuthorizer;
    private final CurrentOperatorService currentOperatorService;
    private final AuditService auditService;
    private final RepresentationValidator representationValidator;

    public AppIoServerService(ConnettoreStore store,
                              AppIoServerMapper mapper,
                              ObjectMapper objectMapper,
                              AclAuthorizer aclAuthorizer,
                              CurrentOperatorService currentOperatorService,
                              AuditService auditService,
                              RepresentationValidator representationValidator) {
        this.store = store;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.aclAuthorizer = aclAuthorizer;
        this.currentOperatorService = currentOperatorService;
        this.auditService = auditService;
        this.representationValidator = representationValidator;
    }

    @Transactional(readOnly = true)
    public ResponseEntity<ImpostazioniAppIoServer> get() {
        aclAuthorizer.requireLettura(AclServizio.CONFIGURAZIONE_E_MANUTENZIONE);
        ImpostazioniAppIoServer dto = mapper.toDto(store.read(COD_CONNETTORE_APP_IO));
        return ResponseEntity.ok()
                .eTag(RepresentationEtag.of(dto, objectMapper))
                .body(dto);
    }

    @Transactional
    public ResponseEntity<ImpostazioniAppIoServer> replace(ImpostazioniAppIoServer body, String ifMatch,
                                                           HttpServletRequest request) {
        aclAuthorizer.requireScrittura(AclServizio.CONFIGURAZIONE_E_MANUTENZIONE);
        checkIfMatch(ifMatch, mapper.toDto(store.read(COD_CONNETTORE_APP_IO)));

        store.upsert(COD_CONNETTORE_APP_IO, mapper.toConfigMap(body), ConnettoreProprietaKeys.CONFIG_KEYS);

        audit(AZIONE_AUDIT_MODIFICA, request);

        ImpostazioniAppIoServer updated = mapper.toDto(store.read(COD_CONNETTORE_APP_IO));
        return ResponseEntity.ok()
                .eTag(RepresentationEtag.of(updated, objectMapper))
                .body(updated);
    }

    @Transactional
    public ResponseEntity<ImpostazioniAppIoServer> patch(List<JsonPatchOperation> operations, String ifMatch,
                                                          HttpServletRequest request) {
        aclAuthorizer.requireScrittura(AclServizio.CONFIGURAZIONE_E_MANUTENZIONE);
        ImpostazioniAppIoServer current = mapper.toDto(store.read(COD_CONNETTORE_APP_IO));
        checkIfMatch(ifMatch, current);

        ObjectNode node = objectMapper.valueToTree(current);
        ObjectNode patched = JsonPatchApplier.apply(node, operations, objectMapper);

        ImpostazioniAppIoServer body;
        try {
            body = objectMapper.treeToValue(patched, ImpostazioniAppIoServer.class);
        } catch (RuntimeException e) {
            throw new BadRequestException("La rappresentazione risultante dal PATCH non e' valida: " + e.getMessage());
        }
        representationValidator.validate(body);

        store.upsert(COD_CONNETTORE_APP_IO, mapper.toConfigMap(body), ConnettoreProprietaKeys.CONFIG_KEYS);
        audit(AZIONE_AUDIT_MODIFICA, request);

        ImpostazioniAppIoServer updated = mapper.toDto(store.read(COD_CONNETTORE_APP_IO));
        return ResponseEntity.ok()
                .eTag(RepresentationEtag.of(updated, objectMapper))
                .body(updated);
    }

    @Transactional
    public ResponseEntity<Void> putCredenziali(ConnettoreCredenziali credenziali, HttpServletRequest request) {
        aclAuthorizer.requireScrittura(AclServizio.CONFIGURAZIONE_E_MANUTENZIONE);

        store.upsert(COD_CONNETTORE_APP_IO, mapper.toCredenzialiMap(credenziali), ConnettoreProprietaKeys.CREDENTIAL_KEYS);

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
        dettaglio.put("connettore", COD_CONNETTORE_APP_IO);
        auditService.registra(azione, 0L, dettaglio, operatore, request);
    }
}
