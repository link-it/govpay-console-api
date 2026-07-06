package it.govpay.console.ruolo;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import it.govpay.console.audit.AuditService;
import it.govpay.console.common.DirittiCodec;
import it.govpay.console.entity.Acl;
import it.govpay.console.intermediario.JsonPatchApplier;
import it.govpay.console.model.JsonPatchOperation;
import it.govpay.console.model.ListRuoli200Response;
import it.govpay.console.model.Pagination;
import it.govpay.console.model.RuoloCreate;
import it.govpay.console.model.RuoloReplace;
import it.govpay.console.repository.AclRepository;
import it.govpay.console.security.CurrentOperatorService;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.web.BadRequestException;
import it.govpay.console.web.ConflictException;
import it.govpay.console.web.IfMatchMismatchException;
import it.govpay.console.web.NotFoundException;
import it.govpay.console.web.PreconditionRequiredException;
import it.govpay.console.web.RepresentationEtag;
import it.govpay.console.web.UnprocessableEntityException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * CRUD dei ruoli. Un ruolo e' un aggregato di righe {@code acl} che condividono
 * lo stesso {@code ruolo} con {@code id_utenza IS NULL} (righe di definizione).
 * Create/replace applicano la strategia delete-and-reinsert delle righe ACL.
 */
@Service
public class RuoloService {

    private static final Logger log = LoggerFactory.getLogger(RuoloService.class);

    public static final String AZIONE_AUDIT_CREATE = "RUOLO_CREATE";
    public static final String AZIONE_AUDIT_MODIFICA = "RUOLO_MODIFICA";

    private final AclRepository aclRepository;
    private final RuoloMapper mapper;
    private final CurrentOperatorService currentOperatorService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    public RuoloService(AclRepository aclRepository,
                        RuoloMapper mapper,
                        CurrentOperatorService currentOperatorService,
                        AuditService auditService,
                        ObjectMapper objectMapper) {
        this.aclRepository = aclRepository;
        this.mapper = mapper;
        this.currentOperatorService = currentOperatorService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ListRuoli200Response list(RuoloListQuery query) {
        log.debug("listRuoli filtro[idRuolo={}], page={}, limit={}, sort={}, total={}",
                query.idRuolo(), query.page(), query.limit(), query.sort(), query.total());

        Comparator<String> comparator;
        try {
            comparator = RuoloSortParser.parse(query.sort());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }

        List<String> ruoli = new ArrayList<>(aclRepository.findRuoliCatalogo());
        if (query.idRuolo() != null && !query.idRuolo().isBlank()) {
            String needle = query.idRuolo().toLowerCase();
            ruoli.removeIf(r -> !r.toLowerCase().contains(needle));
        }
        ruoli.sort(comparator);

        int page = query.page();
        int limit = query.limit();
        int from = Math.min((page - 1) * limit, ruoli.size());
        int to = Math.min(from + limit, ruoli.size());
        List<String> pageItems = ruoli.subList(from, to);
        boolean hasNext = to < ruoli.size();

        Pagination pagination = new Pagination(page, limit, false);
        pagination.setHasNextPage(hasNext);
        if (Boolean.TRUE.equals(query.total())) {
            int totalResults = ruoli.size();
            pagination.setTotalResults((long) totalResults);
            pagination.setTotalPages(limit == 0 ? 0 : (int) Math.ceil((double) totalResults / limit));
        }

        ListRuoli200Response response = new ListRuoli200Response();
        response.setResults(pageItems.stream().map(mapper::toSummary).toList());
        response.setPagination(pagination);
        return response;
    }

    @Transactional(readOnly = true)
    public ResponseEntity<it.govpay.console.model.Ruolo> get(String idRuolo) {
        return ok(idRuolo, load(idRuolo));
    }

    @Transactional
    public ResponseEntity<it.govpay.console.model.Ruolo> create(RuoloCreate body, HttpServletRequest request) {
        String idRuolo = body.getIdRuolo();
        if (idRuolo == null || idRuolo.isBlank()) {
            throw new BadRequestException("Il campo 'idRuolo' e' obbligatorio.");
        }
        if (aclRepository.existsByRuoloAndIdUtenzaIsNull(idRuolo)) {
            throw new ConflictException("Esiste gia' un ruolo con idRuolo '" + idRuolo + "'.");
        }
        List<Acl> rows = buildAclEntities(body.getAcl(), idRuolo);
        aclRepository.saveAll(rows);

        audit(AZIONE_AUDIT_CREATE, idRuolo, rows, request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(idRuolo)
                .toUri();
        it.govpay.console.model.Ruolo dto = mapper.toDetail(idRuolo, rows);
        return ResponseEntity.created(location)
                .eTag(RepresentationEtag.of(dto, objectMapper))
                .body(dto);
    }

    @Transactional
    public ResponseEntity<it.govpay.console.model.Ruolo> replace(String idRuolo, RuoloReplace body,
                                                                 String ifMatch, HttpServletRequest request) {
        List<Acl> current = load(idRuolo);
        checkIfMatch(ifMatch, idRuolo, current);
        return doReplace(idRuolo, body.getAcl(), request);
    }

    @Transactional
    public ResponseEntity<it.govpay.console.model.Ruolo> patch(String idRuolo, List<JsonPatchOperation> operations,
                                                               String ifMatch, HttpServletRequest request) {
        List<Acl> current = load(idRuolo);
        checkIfMatch(ifMatch, idRuolo, current);

        ObjectNode node = objectMapper.valueToTree(mapper.toDetail(idRuolo, current));
        ObjectNode patched = JsonPatchApplier.apply(node, operations, objectMapper);

        String patchedId = text(patched, "idRuolo");
        if (!idRuolo.equals(patchedId)) {
            throw new BadRequestException("Il campo 'idRuolo' non puo' essere modificato tramite PATCH.");
        }
        patched.remove("idRuolo");

        RuoloReplace body;
        try {
            body = objectMapper.treeToValue(patched, RuoloReplace.class);
        } catch (RuntimeException e) {
            throw new BadRequestException("La rappresentazione risultante dal PATCH non e' valida: " + e.getMessage());
        }
        return doReplace(idRuolo, body.getAcl(), request);
    }

    private ResponseEntity<it.govpay.console.model.Ruolo> doReplace(String idRuolo,
                                                                    List<it.govpay.console.model.Acl> acl,
                                                                    HttpServletRequest request) {
        List<Acl> rows = buildAclEntities(acl, idRuolo);
        aclRepository.deleteByRuoloAndIdUtenzaIsNull(idRuolo);
        entityManager.flush();
        aclRepository.saveAll(rows);

        audit(AZIONE_AUDIT_MODIFICA, idRuolo, rows, request);
        return ok(idRuolo, rows);
    }

    private List<Acl> buildAclEntities(List<it.govpay.console.model.Acl> aclList, String idRuolo) {
        if (aclList == null || aclList.isEmpty()) {
            throw new UnprocessableEntityException("Un ruolo deve avere almeno una ACL.");
        }
        List<Acl> out = new ArrayList<>();
        for (it.govpay.console.model.Acl a : aclList) {
            if (a.getServizio() == null) {
                throw new UnprocessableEntityException("Ogni elemento di 'acl' deve avere 'servizio' valorizzato.");
            }
            if (a.getAutorizzazioni() == null || a.getAutorizzazioni().isEmpty()) {
                throw new UnprocessableEntityException(
                        "Ogni elemento di 'acl' deve avere almeno un'autorizzazione (R e/o W).");
            }
            Acl entity = new Acl();
            entity.setServizio(a.getServizio().getValue());
            entity.setDiritti(DirittiCodec.serialize(a.getAutorizzazioni()));
            entity.setRuolo(idRuolo);
            entity.setIdUtenza(null);
            out.add(entity);
        }
        return out;
    }

    private ResponseEntity<it.govpay.console.model.Ruolo> ok(String idRuolo, List<Acl> rows) {
        it.govpay.console.model.Ruolo dto = mapper.toDetail(idRuolo, rows);
        return ResponseEntity.ok()
                .eTag(RepresentationEtag.of(dto, objectMapper))
                .body(dto);
    }

    private List<Acl> load(String idRuolo) {
        List<Acl> rows = aclRepository.findByRuoloAndIdUtenzaIsNull(idRuolo);
        if (rows.isEmpty()) {
            throw new NotFoundException("Ruolo non trovato: " + idRuolo);
        }
        return rows;
    }

    private void checkIfMatch(String ifMatch, String idRuolo, List<Acl> rows) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new PreconditionRequiredException(
                    "Header 'If-Match' obbligatorio per le operazioni di modifica.");
        }
        if (!RepresentationEtag.matches(ifMatch, mapper.toDetail(idRuolo, rows), objectMapper)) {
            throw new IfMatchMismatchException(
                    "L'header 'If-Match' non corrisponde alla versione corrente del ruolo.");
        }
    }

    private void audit(String azione, String idRuolo, List<Acl> rows, HttpServletRequest request) {
        OperatoreCorrente operatore = currentOperatorService.get();
        Map<String, Object> dettaglio = new HashMap<>();
        dettaglio.put("idRuolo", idRuolo);
        long idOggetto = rows.stream().map(Acl::getId).filter(java.util.Objects::nonNull)
                .mapToLong(Long::longValue).min().orElse(0L);
        auditService.registra(azione, idOggetto, dettaglio, operatore, request);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }
}
