package it.govpay.console.impostazioni;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.audit.AuditService;
import it.govpay.console.entity.ImpostazioniHardening;
import it.govpay.console.intermediario.JsonPatchApplier;
import it.govpay.console.model.AclServizio;
import it.govpay.console.model.ImpostazioniHardeningCredenziali;
import it.govpay.console.model.JsonPatchOperation;
import it.govpay.console.repository.ImpostazioniHardeningRepository;
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
 * Gestisce la configurazione Google reCAPTCHA, riga singola
 * {@link ImpostazioniHardening#ID_SINGLETON}.
 */
@Service
public class HardeningService {

    public static final String AZIONE_AUDIT_MODIFICA = "IMPOSTAZIONI_HARDENING_MODIFICA";
    public static final String AZIONE_AUDIT_CREDENZIALI = "IMPOSTAZIONI_HARDENING_CREDENZIALI";

    private final ImpostazioniHardeningRepository repository;
    private final HardeningMapper mapper;
    private final ObjectMapper objectMapper;
    private final AclAuthorizer aclAuthorizer;
    private final CurrentOperatorService currentOperatorService;
    private final AuditService auditService;

    public HardeningService(ImpostazioniHardeningRepository repository,
                            HardeningMapper mapper,
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
    public ResponseEntity<it.govpay.console.model.ImpostazioniHardening> get() {
        aclAuthorizer.requireLettura(AclServizio.CONFIGURAZIONE_E_MANUTENZIONE);
        return ok(currentDto());
    }

    @Transactional
    public ResponseEntity<it.govpay.console.model.ImpostazioniHardening> replace(
            it.govpay.console.model.ImpostazioniHardening body, String ifMatch, HttpServletRequest request) {
        aclAuthorizer.requireScrittura(AclServizio.CONFIGURAZIONE_E_MANUTENZIONE);
        checkIfMatch(ifMatch, currentDto());

        ImpostazioniHardening entity = loadOrCreate();
        mapper.applyConfig(entity, body);
        repository.save(entity);

        audit(AZIONE_AUDIT_MODIFICA, request);
        return ok(currentDto());
    }

    @Transactional
    public ResponseEntity<it.govpay.console.model.ImpostazioniHardening> patch(
            List<JsonPatchOperation> operations, String ifMatch, HttpServletRequest request) {
        aclAuthorizer.requireScrittura(AclServizio.CONFIGURAZIONE_E_MANUTENZIONE);
        it.govpay.console.model.ImpostazioniHardening current = currentDto();
        checkIfMatch(ifMatch, current);

        ObjectNode node = objectMapper.valueToTree(current);
        ObjectNode patched = JsonPatchApplier.apply(node, operations, objectMapper);

        it.govpay.console.model.ImpostazioniHardening body;
        try {
            body = objectMapper.treeToValue(patched, it.govpay.console.model.ImpostazioniHardening.class);
        } catch (RuntimeException e) {
            throw new BadRequestException("La rappresentazione risultante dal PATCH non e' valida: " + e.getMessage());
        }

        ImpostazioniHardening entity = loadOrCreate();
        mapper.applyConfig(entity, body);
        repository.save(entity);

        audit(AZIONE_AUDIT_MODIFICA, request);
        return ok(currentDto());
    }

    @Transactional
    public ResponseEntity<Void> putCredenziali(ImpostazioniHardeningCredenziali credenziali, HttpServletRequest request) {
        aclAuthorizer.requireScrittura(AclServizio.CONFIGURAZIONE_E_MANUTENZIONE);

        ImpostazioniHardening entity = loadOrCreate();
        mapper.applyCredenziali(entity, credenziali);
        repository.save(entity);

        audit(AZIONE_AUDIT_CREDENZIALI, request);
        return ResponseEntity.noContent().build();
    }

    private ImpostazioniHardening loadOrCreate() {
        return repository.findById(ImpostazioniHardening.ID_SINGLETON)
                .orElseGet(ImpostazioniHardening::new);
    }

    private it.govpay.console.model.ImpostazioniHardening currentDto() {
        return mapper.toDto(loadOrCreate());
    }

    private ResponseEntity<it.govpay.console.model.ImpostazioniHardening> ok(
            it.govpay.console.model.ImpostazioniHardening dto) {
        return ResponseEntity.ok()
                .eTag(RepresentationEtag.of(dto, objectMapper))
                .body(dto);
    }

    private void checkIfMatch(String ifMatch, it.govpay.console.model.ImpostazioniHardening current) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new PreconditionRequiredException(
                    "Header 'If-Match' obbligatorio per le operazioni di modifica.");
        }
        if (!RepresentationEtag.matches(ifMatch, current, objectMapper)) {
            throw new IfMatchMismatchException(
                    "L'header 'If-Match' non corrisponde alla configurazione corrente dell'hardening.");
        }
    }

    private void audit(String azione, HttpServletRequest request) {
        OperatoreCorrente operatore = currentOperatorService.get();
        Map<String, Object> dettaglio = new HashMap<>();
        auditService.registra(azione, ImpostazioniHardening.ID_SINGLETON, dettaglio, operatore, request);
    }
}
