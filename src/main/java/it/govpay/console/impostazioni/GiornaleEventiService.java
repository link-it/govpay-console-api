package it.govpay.console.impostazioni;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.common.configurazione.ConfigurazioneKeys;
import it.govpay.common.configurazione.model.Giornale;
import it.govpay.console.audit.AuditService;
import it.govpay.console.intermediario.JsonPatchApplier;
import it.govpay.console.model.AclServizio;
import it.govpay.console.model.ImpostazioniGiornaleEventi;
import it.govpay.console.model.ImpostazioniGiornaleEventiLinks;
import it.govpay.console.model.JsonPatchOperation;
import it.govpay.console.model.Link;
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
 * Gestisce la politica di logging verso il Giornale degli Eventi, riga
 * {@code giornale_eventi} della tabella {@code configurazione}. Il connettore
 * GDE vero e proprio (url/auth) e' la sotto-risorsa indipendente
 * {@link ServizioGdeService}, referenziata via {@code _links}.
 */
@Service
public class GiornaleEventiService {

    public static final String AZIONE_AUDIT_MODIFICA = "IMPOSTAZIONI_GIORNALE_EVENTI_MODIFICA";

    private final ConfigurazioneBlobStore blobStore;
    private final GiornaleEventiMapper mapper;
    private final ObjectMapper objectMapper;
    private final AclAuthorizer aclAuthorizer;
    private final CurrentOperatorService currentOperatorService;
    private final AuditService auditService;

    public GiornaleEventiService(ConfigurazioneBlobStore blobStore,
                                 GiornaleEventiMapper mapper,
                                 ObjectMapper objectMapper,
                                 AclAuthorizer aclAuthorizer,
                                 CurrentOperatorService currentOperatorService,
                                 AuditService auditService) {
        this.blobStore = blobStore;
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

        blobStore.write(ConfigurazioneKeys.KEY_GIORNALE_EVENTI, mapper.toCommon(body));
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

        blobStore.write(ConfigurazioneKeys.KEY_GIORNALE_EVENTI, mapper.toCommon(body));
        audit(request);
        return ok(currentDto());
    }

    private ImpostazioniGiornaleEventi currentDto() {
        Giornale giornale = blobStore.read(ConfigurazioneKeys.KEY_GIORNALE_EVENTI, Giornale.class, Giornale::new);
        ImpostazioniGiornaleEventi dto = mapper.toDto(giornale);
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
