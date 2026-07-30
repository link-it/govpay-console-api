package it.govpay.console.impostazioni;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.audit.AuditService;
import it.govpay.console.entity.ImpostazioniMailServer;
import it.govpay.console.intermediario.JsonPatchApplier;
import it.govpay.console.model.AclServizio;
import it.govpay.console.model.ImpostazioniMailServerCredenziali;
import it.govpay.console.model.JsonPatchOperation;
import it.govpay.console.repository.ImpostazioniMailServerRepository;
import it.govpay.console.security.AclAuthorizer;
import it.govpay.console.security.CurrentOperatorService;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.web.BadRequestException;
import it.govpay.console.web.IfMatchMismatchException;
import it.govpay.console.web.PreconditionRequiredException;
import it.govpay.console.web.RepresentationEtag;
import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Gestisce il server SMTP usato per l'invio dei promemoria, riga singola
 * {@link ImpostazioniMailServer#ID_SINGLETON}.
 */
@Service
public class MailServerService {

    public static final String AZIONE_AUDIT_MODIFICA = "IMPOSTAZIONI_MAIL_SERVER_MODIFICA";
    public static final String AZIONE_AUDIT_CREDENZIALI = "IMPOSTAZIONI_MAIL_SERVER_CREDENZIALI";

    private final ImpostazioniMailServerRepository repository;
    private final MailServerMapper mapper;
    private final ObjectMapper objectMapper;
    private final AclAuthorizer aclAuthorizer;
    private final CurrentOperatorService currentOperatorService;
    private final AuditService auditService;

    public MailServerService(ImpostazioniMailServerRepository repository,
                             MailServerMapper mapper,
                             ObjectMapper objectMapper,
                             AclAuthorizer aclAuthorizer,
                             CurrentOperatorService currentOperatorService,
                             AuditService auditService) {
        this.repository = repository;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.aclAuthorizer = aclAuthorizer;
        this.currentOperatorService = currentOperatorService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public ResponseEntity<it.govpay.console.model.ImpostazioniMailServer> get() {
        aclAuthorizer.requireLettura(AclServizio.CONFIGURAZIONE_E_MANUTENZIONE);
        return ok(currentDto());
    }

    @Transactional
    public ResponseEntity<it.govpay.console.model.ImpostazioniMailServer> replace(
            it.govpay.console.model.ImpostazioniMailServer body, String ifMatch, HttpServletRequest request) {
        aclAuthorizer.requireScrittura(AclServizio.CONFIGURAZIONE_E_MANUTENZIONE);
        checkIfMatch(ifMatch, currentDto());

        ImpostazioniMailServer entity = loadOrCreate();
        mapper.applyConfig(entity, body);
        repository.save(entity);

        audit(AZIONE_AUDIT_MODIFICA, request);
        return ok(currentDto());
    }

    @Transactional
    public ResponseEntity<it.govpay.console.model.ImpostazioniMailServer> patch(
            List<JsonPatchOperation> operations, String ifMatch, HttpServletRequest request) {
        aclAuthorizer.requireScrittura(AclServizio.CONFIGURAZIONE_E_MANUTENZIONE);
        it.govpay.console.model.ImpostazioniMailServer current = currentDto();
        checkIfMatch(ifMatch, current);

        ObjectNode node = objectMapper.valueToTree(current);
        node.remove("passwordImpostata");
        ObjectNode patched = JsonPatchApplier.apply(node, operations, objectMapper);
        patched.remove("passwordImpostata");

        it.govpay.console.model.ImpostazioniMailServer body;
        try {
            body = objectMapper.treeToValue(patched, it.govpay.console.model.ImpostazioniMailServer.class);
        } catch (RuntimeException e) {
            throw new BadRequestException("La rappresentazione risultante dal PATCH non e' valida: " + e.getMessage());
        }

        ImpostazioniMailServer entity = loadOrCreate();
        mapper.applyConfig(entity, body);
        repository.save(entity);

        audit(AZIONE_AUDIT_MODIFICA, request);
        return ok(currentDto());
    }

    @Transactional
    public ResponseEntity<Void> putPassword(ImpostazioniMailServerCredenziali credenziali, HttpServletRequest request) {
        aclAuthorizer.requireScrittura(AclServizio.CONFIGURAZIONE_E_MANUTENZIONE);

        ImpostazioniMailServer entity = loadOrCreate();
        mapper.applyCredenziali(entity, credenziali);
        repository.save(entity);

        audit(AZIONE_AUDIT_CREDENZIALI, request);
        return ResponseEntity.noContent().build();
    }

    private ImpostazioniMailServer loadOrCreate() {
        return repository.findById(ImpostazioniMailServer.ID_SINGLETON)
                .orElseGet(ImpostazioniMailServer::new);
    }

    private it.govpay.console.model.ImpostazioniMailServer currentDto() {
        return mapper.toDto(loadOrCreate());
    }

    private ResponseEntity<it.govpay.console.model.ImpostazioniMailServer> ok(
            it.govpay.console.model.ImpostazioniMailServer dto) {
        return ResponseEntity.ok()
                .eTag(RepresentationEtag.of(dto, objectMapper))
                .body(dto);
    }

    private void checkIfMatch(String ifMatch, it.govpay.console.model.ImpostazioniMailServer current) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new PreconditionRequiredException(
                    "Header 'If-Match' obbligatorio per le operazioni di modifica.");
        }
        if (!RepresentationEtag.matches(ifMatch, current, objectMapper)) {
            throw new IfMatchMismatchException(
                    "L'header 'If-Match' non corrisponde alla configurazione corrente del server SMTP.");
        }
    }

    private void audit(String azione, HttpServletRequest request) {
        OperatoreCorrente operatore = currentOperatorService.get();
        Map<String, Object> dettaglio = new HashMap<>();
        auditService.registra(azione, ImpostazioniMailServer.ID_SINGLETON, dettaglio, operatore, request);
    }
}
