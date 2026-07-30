package it.govpay.console.impostazioni;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.audit.AuditService;
import it.govpay.console.entity.GiornaleEventiInterfaccia;
import it.govpay.console.intermediario.JsonPatchApplier;
import it.govpay.console.model.AclServizio;
import it.govpay.console.model.ImpostazioniGiornaleEventi;
import it.govpay.console.model.ImpostazioniGiornaleEventiLinks;
import it.govpay.console.model.JsonPatchOperation;
import it.govpay.console.model.Link;
import it.govpay.console.repository.GiornaleEventiInterfacciaRepository;
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
 * Gestisce la politica di logging verso il Giornale degli Eventi, 8 righe
 * fisse (una per interfaccia API) su {@code giornale_eventi_interfacce}.
 * Il connettore GDE vero e proprio (url/auth) e' la sotto-risorsa
 * indipendente {@link ServizioGdeService}, referenziata via {@code _links}.
 */
@Service
public class GiornaleEventiService {

    public static final String AZIONE_AUDIT_MODIFICA = "IMPOSTAZIONI_GIORNALE_EVENTI_MODIFICA";

    private final GiornaleEventiInterfacciaRepository repository;
    private final GiornaleEventiMapper mapper;
    private final ObjectMapper objectMapper;
    private final AclAuthorizer aclAuthorizer;
    private final CurrentOperatorService currentOperatorService;
    private final AuditService auditService;

    public GiornaleEventiService(GiornaleEventiInterfacciaRepository repository,
                                 GiornaleEventiMapper mapper,
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
    public ResponseEntity<ImpostazioniGiornaleEventi> get() {
        aclAuthorizer.requireLettura(AclServizio.CONFIGURAZIONE_E_MANUTENZIONE);
        return ok(currentDto());
    }

    @Transactional
    public ResponseEntity<ImpostazioniGiornaleEventi> replace(ImpostazioniGiornaleEventi body, String ifMatch,
                                                              HttpServletRequest request) {
        aclAuthorizer.requireScrittura(AclServizio.CONFIGURAZIONE_E_MANUTENZIONE);
        checkIfMatch(ifMatch, currentDto());

        persist(body);
        audit(request);
        return ok(currentDto());
    }

    @Transactional
    public ResponseEntity<ImpostazioniGiornaleEventi> patch(List<JsonPatchOperation> operations, String ifMatch,
                                                             HttpServletRequest request) {
        aclAuthorizer.requireScrittura(AclServizio.CONFIGURAZIONE_E_MANUTENZIONE);
        ImpostazioniGiornaleEventi current = currentDto();
        checkIfMatch(ifMatch, current);

        ObjectNode node = objectMapper.valueToTree(current);
        node.remove("_links");
        ObjectNode patched = JsonPatchApplier.apply(node, operations, objectMapper);
        patched.remove("_links");

        ImpostazioniGiornaleEventi body;
        try {
            body = objectMapper.treeToValue(patched, ImpostazioniGiornaleEventi.class);
        } catch (RuntimeException e) {
            throw new BadRequestException("La rappresentazione risultante dal PATCH non e' valida: " + e.getMessage());
        }

        persist(body);
        audit(request);
        return ok(currentDto());
    }

    private void persist(ImpostazioniGiornaleEventi body) {
        Map<String, GiornaleEventiInterfaccia> entities = mapper.toEntities(body);
        repository.saveAll(entities.values());
    }

    private ImpostazioniGiornaleEventi currentDto() {
        Map<String, GiornaleEventiInterfaccia> byNome = repository.findAllById(GiornaleEventiMapper.NOMI_INTERFACCE).stream()
                .collect(Collectors.toMap(GiornaleEventiInterfaccia::getNomeInterfaccia, e -> e));
        ImpostazioniGiornaleEventi dto = mapper.toDto(byNome);
        dto.setLinks(buildLinks());
        return dto;
    }

    private static ImpostazioniGiornaleEventiLinks buildLinks() {
        ImpostazioniGiornaleEventiLinks links = new ImpostazioniGiornaleEventiLinks();
        links.setServizioGDE(new Link("/impostazioni/servizioGDE"));
        return links;
    }

    private ResponseEntity<ImpostazioniGiornaleEventi> ok(ImpostazioniGiornaleEventi dto) {
        return ResponseEntity.ok()
                .eTag(RepresentationEtag.of(dto, objectMapper))
                .body(dto);
    }

    private void checkIfMatch(String ifMatch, ImpostazioniGiornaleEventi current) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new PreconditionRequiredException(
                    "Header 'If-Match' obbligatorio per le operazioni di modifica.");
        }
        if (!RepresentationEtag.matches(ifMatch, current, objectMapper)) {
            throw new IfMatchMismatchException(
                    "L'header 'If-Match' non corrisponde alla configurazione corrente del giornale eventi.");
        }
    }

    private void audit(HttpServletRequest request) {
        OperatoreCorrente operatore = currentOperatorService.get();
        Map<String, Object> dettaglio = new HashMap<>();
        auditService.registra(AZIONE_AUDIT_MODIFICA, 0L, dettaglio, operatore, request);
    }
}
