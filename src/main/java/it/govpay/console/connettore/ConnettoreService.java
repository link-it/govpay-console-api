package it.govpay.console.connettore;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.audit.AuditService;
import it.govpay.console.entity.Intermediario;
import it.govpay.console.model.ConnettoreCredenziali;
import it.govpay.console.repository.IntermediarioRepository;
import it.govpay.console.security.CurrentOperatorService;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.web.IfMatchMismatchException;
import it.govpay.console.web.NotFoundException;
import it.govpay.console.web.PreconditionRequiredException;
import it.govpay.console.web.RepresentationEtag;
import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class ConnettoreService {

    public static final String AZIONE_AUDIT_MODIFICA = "CONNETTORE_MODIFICA";
    public static final String AZIONE_AUDIT_CREDENZIALI = "CONNETTORE_CREDENZIALI";

    private final IntermediarioRepository intermediarioRepository;
    private final ConnettoreStore store;
    private final ConnettoreMapper mapper;
    private final ObjectMapper objectMapper;
    private final CurrentOperatorService currentOperatorService;
    private final AuditService auditService;

    public ConnettoreService(IntermediarioRepository intermediarioRepository,
                             ConnettoreStore store,
                             ConnettoreMapper mapper,
                             ObjectMapper objectMapper,
                             CurrentOperatorService currentOperatorService,
                             AuditService auditService) {
        this.intermediarioRepository = intermediarioRepository;
        this.store = store;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.currentOperatorService = currentOperatorService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public <T> ResponseEntity<T> get(String idIntermediario, ConnettoreCanale canale, Class<T> dtoClass) {
        Intermediario intermediario = loadIntermediario(idIntermediario);
        T dto = toDto(readConfig(canale.codConnettore(intermediario)), canale, dtoClass);
        return ResponseEntity.ok()
                .eTag(RepresentationEtag.of(dto, objectMapper))
                .body(dto);
    }

    @Transactional
    public <T> ResponseEntity<T> replace(String idIntermediario, ConnettoreCanale canale,
                                         T dto, String ifMatch, Class<T> dtoClass,
                                         HttpServletRequest request) {
        Intermediario intermediario = loadIntermediario(idIntermediario);
        String cod = canale.codConnettore(intermediario);
        checkIfMatch(ifMatch, toDto(readConfig(cod), canale, dtoClass));

        cod = ensureCodConnettore(intermediario, canale, cod);
        Map<String, String> desired = mapper.toConfigMap(objectMapper.valueToTree(dto), canale);
        store.upsert(cod, desired, ConnettoreProprietaKeys.CONFIG_KEYS);

        audit(AZIONE_AUDIT_MODIFICA, intermediario, canale, request);

        T updated = toDto(store.read(cod), canale, dtoClass);
        return ResponseEntity.ok()
                .eTag(RepresentationEtag.of(updated, objectMapper))
                .body(updated);
    }

    @Transactional
    public ResponseEntity<Void> putCredenziali(String idIntermediario, ConnettoreCanale canale,
                                               ConnettoreCredenziali credenziali, HttpServletRequest request) {
        Intermediario intermediario = loadIntermediario(idIntermediario);
        String cod = ensureCodConnettore(intermediario, canale, canale.codConnettore(intermediario));

        Map<String, String> desired = mapper.toCredenzialiMap(objectMapper.valueToTree(credenziali));
        store.upsert(cod, desired, ConnettoreProprietaKeys.CREDENTIAL_KEYS);

        audit(AZIONE_AUDIT_CREDENZIALI, intermediario, canale, request);
        return ResponseEntity.noContent().build();
    }

    private Map<String, String> readConfig(String codConnettore) {
        return codConnettore == null ? new HashMap<>() : store.read(codConnettore);
    }

    private <T> T toDto(Map<String, String> config, ConnettoreCanale canale, Class<T> dtoClass) {
        ObjectNode node = mapper.toDtoNode(config, canale);
        return objectMapper.treeToValue(node, dtoClass);
    }

    private String ensureCodConnettore(Intermediario intermediario, ConnettoreCanale canale, String cod) {
        if (cod != null) {
            return cod;
        }
        String generated = canale.generaCodConnettore(intermediario);
        canale.setCodConnettore(intermediario, generated);
        intermediarioRepository.save(intermediario);
        return generated;
    }

    private Intermediario loadIntermediario(String idIntermediario) {
        return intermediarioRepository.findByCodIntermediario(idIntermediario)
                .orElseThrow(() -> new NotFoundException("Intermediario non trovato: " + idIntermediario));
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

    private void audit(String azione, Intermediario intermediario, ConnettoreCanale canale,
                       HttpServletRequest request) {
        OperatoreCorrente operatore = currentOperatorService.get();
        Map<String, Object> dettaglio = new HashMap<>();
        dettaglio.put("idIntermediario", intermediario.getCodIntermediario());
        dettaglio.put("canale", canale.name());
        auditService.registra(azione, intermediario.getId(), dettaglio, operatore, request);
    }
}
