package it.govpay.console.impostazioni;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.audit.AuditService;
import it.govpay.console.entity.ImpostazioniAppIoPromemoria;
import it.govpay.console.intermediario.JsonPatchApplier;
import it.govpay.console.model.AclServizio;
import it.govpay.console.model.ImpostazioniAppIoTemplatePromemoria;
import it.govpay.console.model.JsonPatchOperation;
import it.govpay.console.repository.ImpostazioniAppIoPromemoriaRepository;
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
 * Gestisce i template FreeMarker dei promemoria spediti via notifica push
 * App IO, 3 righe fisse (una per tipo) su {@code impostazioni_appio_promemoria}.
 */
@Service
public class AppIoPromemoriaService {

    private static final Set<String> TIPI = Set.of(
            ImpostazioniAppIoPromemoria.AVVISO, ImpostazioniAppIoPromemoria.RICEVUTA, ImpostazioniAppIoPromemoria.SCADENZA);

    public static final String AZIONE_AUDIT_MODIFICA = "IMPOSTAZIONI_APP_IO_PROMEMORIA_MODIFICA";

    private final ImpostazioniAppIoPromemoriaRepository repository;
    private final AppIoPromemoriaMapper mapper;
    private final ObjectMapper objectMapper;
    private final AclAuthorizer aclAuthorizer;
    private final CurrentOperatorService currentOperatorService;
    private final AuditService auditService;

    public AppIoPromemoriaService(ImpostazioniAppIoPromemoriaRepository repository,
                                  AppIoPromemoriaMapper mapper,
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
    public ResponseEntity<ImpostazioniAppIoTemplatePromemoria> get() {
        aclAuthorizer.requireLettura(AclServizio.CONFIGURAZIONE_E_MANUTENZIONE);
        return ok(currentDto());
    }

    @Transactional
    public ResponseEntity<ImpostazioniAppIoTemplatePromemoria> replace(ImpostazioniAppIoTemplatePromemoria body,
                                                                        String ifMatch, HttpServletRequest request) {
        aclAuthorizer.requireScrittura(AclServizio.CONFIGURAZIONE_E_MANUTENZIONE);
        checkIfMatch(ifMatch, currentDto());

        persist(body);
        audit(request);
        return ok(currentDto());
    }

    @Transactional
    public ResponseEntity<ImpostazioniAppIoTemplatePromemoria> patch(List<JsonPatchOperation> operations,
                                                                      String ifMatch, HttpServletRequest request) {
        aclAuthorizer.requireScrittura(AclServizio.CONFIGURAZIONE_E_MANUTENZIONE);
        ImpostazioniAppIoTemplatePromemoria current = currentDto();
        checkIfMatch(ifMatch, current);

        ObjectNode node = objectMapper.valueToTree(current);
        ObjectNode patched = JsonPatchApplier.apply(node, operations, objectMapper);

        ImpostazioniAppIoTemplatePromemoria body;
        try {
            body = objectMapper.treeToValue(patched, ImpostazioniAppIoTemplatePromemoria.class);
        } catch (RuntimeException e) {
            throw new BadRequestException("La rappresentazione risultante dal PATCH non e' valida: " + e.getMessage());
        }

        persist(body);
        audit(request);
        return ok(currentDto());
    }

    private void persist(ImpostazioniAppIoTemplatePromemoria body) {
        Map<String, ImpostazioniAppIoPromemoria> entities = mapper.toEntities(body);
        repository.saveAll(entities.values());
    }

    private ImpostazioniAppIoTemplatePromemoria currentDto() {
        Map<String, ImpostazioniAppIoPromemoria> byTipo = repository.findAllById(TIPI).stream()
                .collect(java.util.stream.Collectors.toMap(ImpostazioniAppIoPromemoria::getTipoPromemoria, e -> e));
        return mapper.toDto(byTipo);
    }

    private ResponseEntity<ImpostazioniAppIoTemplatePromemoria> ok(ImpostazioniAppIoTemplatePromemoria dto) {
        return ResponseEntity.ok()
                .eTag(RepresentationEtag.of(dto, objectMapper))
                .body(dto);
    }

    private void checkIfMatch(String ifMatch, ImpostazioniAppIoTemplatePromemoria current) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new PreconditionRequiredException(
                    "Header 'If-Match' obbligatorio per le operazioni di modifica.");
        }
        if (!RepresentationEtag.matches(ifMatch, current, objectMapper)) {
            throw new IfMatchMismatchException(
                    "L'header 'If-Match' non corrisponde alla configurazione corrente dei template.");
        }
    }

    private void audit(HttpServletRequest request) {
        OperatoreCorrente operatore = currentOperatorService.get();
        Map<String, Object> dettaglio = new HashMap<>();
        auditService.registra(AZIONE_AUDIT_MODIFICA, 0L, dettaglio, operatore, request);
    }
}
