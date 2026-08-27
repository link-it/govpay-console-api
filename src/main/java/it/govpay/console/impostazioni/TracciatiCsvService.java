package it.govpay.console.impostazioni;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.common.configurazione.ConfigurazioneKeys;
import it.govpay.common.configurazione.model.TracciatoCsv;
import it.govpay.console.audit.AuditService;
import it.govpay.console.intermediario.JsonPatchApplier;
import it.govpay.console.model.AclServizio;
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
 * Gestisce i template FreeMarker dei tracciati CSV di risposta, riga
 * {@code tracciato_csv} della tabella {@code configurazione}.
 */
@Service
public class TracciatiCsvService {

    public static final String AZIONE_AUDIT_MODIFICA = "IMPOSTAZIONI_TRACCIATI_CSV_MODIFICA";

    private final ConfigurazioneBlobStore blobStore;
    private final TracciatiCsvMapper mapper;
    private final ObjectMapper objectMapper;
    private final AclAuthorizer aclAuthorizer;
    private final CurrentOperatorService currentOperatorService;
    private final AuditService auditService;
    private final RepresentationValidator representationValidator;

    public TracciatiCsvService(ConfigurazioneBlobStore blobStore,
                              TracciatiCsvMapper mapper,
                              ObjectMapper objectMapper,
                              AclAuthorizer aclAuthorizer,
                              CurrentOperatorService currentOperatorService,
                              AuditService auditService,
                              RepresentationValidator representationValidator) {
        this.blobStore = blobStore;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.aclAuthorizer = aclAuthorizer;
        this.currentOperatorService = currentOperatorService;
        this.auditService = auditService;
        this.representationValidator = representationValidator;
    }

    @Transactional(readOnly = true)
    public ResponseEntity<it.govpay.console.model.ImpostazioniTracciatiCsv> get() {
        aclAuthorizer.requireLettura(AclServizio.CONFIGURAZIONE_E_MANUTENZIONE);
        return ok(currentDto());
    }

    @Transactional
    public ResponseEntity<it.govpay.console.model.ImpostazioniTracciatiCsv> replace(
            it.govpay.console.model.ImpostazioniTracciatiCsv body, String ifMatch, HttpServletRequest request) {
        aclAuthorizer.requireScrittura(AclServizio.CONFIGURAZIONE_E_MANUTENZIONE);
        checkIfMatch(ifMatch, currentDto());

        TracciatoCsv tracciato = loadOrCreate();
        mapper.applyConfig(tracciato, body);
        blobStore.write(ConfigurazioneKeys.KEY_TRACCIATO_CSV, tracciato);

        audit(request);
        return ok(currentDto());
    }

    @Transactional
    public ResponseEntity<it.govpay.console.model.ImpostazioniTracciatiCsv> patch(
            List<JsonPatchOperation> operations, String ifMatch, HttpServletRequest request) {
        aclAuthorizer.requireScrittura(AclServizio.CONFIGURAZIONE_E_MANUTENZIONE);
        it.govpay.console.model.ImpostazioniTracciatiCsv current = currentDto();
        checkIfMatch(ifMatch, current);

        ObjectNode node = objectMapper.valueToTree(current);
        ObjectNode patched = JsonPatchApplier.apply(node, operations, objectMapper);

        it.govpay.console.model.ImpostazioniTracciatiCsv body;
        try {
            body = objectMapper.treeToValue(patched, it.govpay.console.model.ImpostazioniTracciatiCsv.class);
        } catch (RuntimeException e) {
            throw new BadRequestException("La rappresentazione risultante dal PATCH non e' valida: " + e.getMessage());
        }
        representationValidator.validate(body);

        TracciatoCsv tracciato = loadOrCreate();
        mapper.applyConfig(tracciato, body);
        blobStore.write(ConfigurazioneKeys.KEY_TRACCIATO_CSV, tracciato);

        audit(request);
        return ok(currentDto());
    }

    private TracciatoCsv loadOrCreate() {
        return blobStore.read(ConfigurazioneKeys.KEY_TRACCIATO_CSV, TracciatoCsv.class, TracciatoCsv::new);
    }

    private it.govpay.console.model.ImpostazioniTracciatiCsv currentDto() {
        return mapper.toDto(loadOrCreate());
    }

    private ResponseEntity<it.govpay.console.model.ImpostazioniTracciatiCsv> ok(
            it.govpay.console.model.ImpostazioniTracciatiCsv dto) {
        return ResponseEntity.ok()
                .eTag(RepresentationEtag.of(dto, objectMapper))
                .body(dto);
    }

    private void checkIfMatch(String ifMatch, it.govpay.console.model.ImpostazioniTracciatiCsv current) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new PreconditionRequiredException(
                    "Header 'If-Match' obbligatorio per le operazioni di modifica.");
        }
        if (!RepresentationEtag.matches(ifMatch, current, objectMapper)) {
            throw new IfMatchMismatchException(
                    "L'header 'If-Match' non corrisponde alla configurazione corrente dei tracciati CSV.");
        }
    }

    private void audit(HttpServletRequest request) {
        OperatoreCorrente operatore = currentOperatorService.get();
        Map<String, Object> dettaglio = new HashMap<>();
        auditService.registra(AZIONE_AUDIT_MODIFICA, 0L, dettaglio, operatore, request);
    }
}
