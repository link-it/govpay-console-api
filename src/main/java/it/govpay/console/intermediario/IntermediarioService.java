package it.govpay.console.intermediario;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import it.govpay.console.audit.AuditService;
import it.govpay.console.entity.Intermediario;
import it.govpay.console.model.IntermediarioCreate;
import it.govpay.console.model.IntermediarioReplace;
import it.govpay.console.model.JsonPatchOperation;
import it.govpay.console.model.ListIntermediari200Response;
import it.govpay.console.model.Pagination;
import it.govpay.console.repository.IntermediarioRepository;
import it.govpay.console.security.CurrentOperatorService;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.web.BadRequestException;
import it.govpay.console.web.ConflictException;
import it.govpay.console.web.IfMatchMismatchException;
import it.govpay.console.web.NotFoundException;
import it.govpay.console.web.PreconditionRequiredException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class IntermediarioService {

    private static final Logger log = LoggerFactory.getLogger(IntermediarioService.class);

    public static final String AZIONE_AUDIT_CREATE = "INTERMEDIARIO_CREATE";
    public static final String AZIONE_AUDIT_MODIFICA = "INTERMEDIARIO_MODIFICA";

    private static final int MAX_DENOMINAZIONE = 255;
    private static final int MAX_PRINCIPAL = 4000;

    private final IntermediarioRepository repository;
    private final IntermediarioMapper mapper;
    private final CurrentOperatorService currentOperatorService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    public IntermediarioService(IntermediarioRepository repository,
                                IntermediarioMapper mapper,
                                CurrentOperatorService currentOperatorService,
                                AuditService auditService,
                                ObjectMapper objectMapper) {
        this.repository = repository;
        this.mapper = mapper;
        this.currentOperatorService = currentOperatorService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ListIntermediari200Response list(IntermediarioListQuery query) {
        log.debug("listIntermediari filtri[codIntermediario={}, denominazione={}, abilitato={}], "
                        + "page={}, limit={}, sort={}, total={}",
                query.codIntermediario(), query.denominazione(), query.abilitato(),
                query.page(), query.limit(), query.sort(), query.total());

        Specification<Intermediario> spec = Specification.allOf(
                Stream.of(
                        IntermediarioSpecifications.codIntermediarioPartial(query.codIntermediario()),
                        IntermediarioSpecifications.denominazionePartial(query.denominazione()),
                        IntermediarioSpecifications.abilitatoExact(query.abilitato()))
                .filter(Objects::nonNull)
                .toList());

        Sort sort;
        try {
            sort = IntermediarioSortParser.parse(query.sort());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }

        int page = query.page();
        int limit = query.limit();
        boolean wantTotal = Boolean.TRUE.equals(query.total());

        Pagination pagination = new Pagination(page, limit, false);
        List<Intermediario> rows;
        if (wantTotal) {
            Page<Intermediario> p = repository.findAll(spec, PageRequest.of(page - 1, limit, sort));
            rows = p.getContent();
            pagination.setHasNextPage(p.hasNext());
            pagination.setTotalResults(p.getTotalElements());
            pagination.setTotalPages(p.getTotalPages());
        } else {
            List<Intermediario> sliced = findSlice(spec, sort, (page - 1) * limit, limit + 1);
            boolean hasNext = sliced.size() > limit;
            rows = hasNext ? sliced.subList(0, limit) : sliced;
            pagination.setHasNextPage(hasNext);
        }

        ListIntermediari200Response response = new ListIntermediari200Response();
        response.setResults(rows.stream().map(mapper::toSummary).toList());
        response.setPagination(pagination);
        return response;
    }

    @Transactional(readOnly = true)
    public ResponseEntity<it.govpay.console.model.Intermediario> get(String idIntermediario) {
        Intermediario entity = load(idIntermediario);
        return ok(entity);
    }

    @Transactional
    public ResponseEntity<it.govpay.console.model.Intermediario> create(IntermediarioCreate body,
                                                                        HttpServletRequest request) {
        if (repository.existsByCodIntermediario(body.getIdIntermediario())) {
            throw new ConflictException(
                    "Esiste gia' un intermediario con idIntermediario '" + body.getIdIntermediario() + "'.");
        }
        Intermediario entity = new Intermediario();
        entity.setCodIntermediario(body.getIdIntermediario());
        entity.setDenominazione(body.getDenominazione());
        entity.setPrincipal(body.getPrincipalPagoPa());
        entity.setPrincipalOriginale(body.getPrincipalPagoPa());
        entity.setCodConnettorePdd(body.getCodConnettorePagoPa());
        entity.setAbilitato(body.getAbilitato());
        Intermediario saved = repository.save(entity);

        audit(AZIONE_AUDIT_CREATE, saved, request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(saved.getCodIntermediario())
                .toUri();
        return ResponseEntity.created(location)
                .eTag(IntermediarioEtag.compute(saved))
                .body(mapper.toDetail(saved));
    }

    @Transactional
    public ResponseEntity<it.govpay.console.model.Intermediario> replace(String idIntermediario,
                                                                         IntermediarioReplace body,
                                                                         String ifMatch,
                                                                         HttpServletRequest request) {
        Intermediario entity = load(idIntermediario);
        checkIfMatch(ifMatch, entity);

        entity.setDenominazione(body.getDenominazione());
        entity.setPrincipal(body.getPrincipalPagoPa());
        entity.setPrincipalOriginale(body.getPrincipalPagoPa());
        entity.setCodConnettorePdd(body.getCodConnettorePagoPa());
        entity.setAbilitato(body.getAbilitato());
        Intermediario saved = repository.save(entity);

        audit(AZIONE_AUDIT_MODIFICA, saved, request);
        return ok(saved);
    }

    @Transactional
    public ResponseEntity<it.govpay.console.model.Intermediario> patch(String idIntermediario,
                                                                       List<JsonPatchOperation> operations,
                                                                       String ifMatch,
                                                                       HttpServletRequest request) {
        Intermediario entity = load(idIntermediario);
        checkIfMatch(ifMatch, entity);

        ObjectNode current = objectMapper.valueToTree(mapper.toDetail(entity));
        ObjectNode patched = JsonPatchApplier.apply(current, operations, objectMapper);

        String patchedId = text(patched, "idIntermediario");
        if (!idIntermediario.equals(patchedId)) {
            throw new BadRequestException(
                    "Il campo 'idIntermediario' non puo' essere modificato tramite PATCH.");
        }

        String denominazione = requireText(patched, "denominazione", MAX_DENOMINAZIONE);
        String principalPagoPa = requireText(patched, "principalPagoPa", MAX_PRINCIPAL);
        Boolean abilitato = requireBoolean(patched, "abilitato");

        entity.setDenominazione(denominazione);
        entity.setPrincipal(principalPagoPa);
        entity.setPrincipalOriginale(principalPagoPa);
        entity.setAbilitato(abilitato);
        Intermediario saved = repository.save(entity);

        audit(AZIONE_AUDIT_MODIFICA, saved, request);
        return ok(saved);
    }

    private ResponseEntity<it.govpay.console.model.Intermediario> ok(Intermediario entity) {
        return ResponseEntity.ok()
                .eTag(IntermediarioEtag.compute(entity))
                .body(mapper.toDetail(entity));
    }

    private Intermediario load(String idIntermediario) {
        return repository.findByCodIntermediario(idIntermediario)
                .orElseThrow(() -> new NotFoundException(
                        "Intermediario non trovato: " + idIntermediario));
    }

    private void checkIfMatch(String ifMatch, Intermediario entity) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new PreconditionRequiredException(
                    "Header 'If-Match' obbligatorio per le operazioni di modifica.");
        }
        if (!IntermediarioEtag.matches(ifMatch, entity)) {
            throw new IfMatchMismatchException(
                    "L'header 'If-Match' non corrisponde alla versione corrente dell'intermediario.");
        }
    }

    private void audit(String azione, Intermediario entity, HttpServletRequest request) {
        OperatoreCorrente operatore = currentOperatorService.get();
        Map<String, Object> dettaglio = new HashMap<>();
        dettaglio.put("idIntermediario", entity.getCodIntermediario());
        auditService.registra(azione, entity.getId(), dettaglio, operatore, request);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    private static String requireText(ObjectNode node, String field, int maxLength) {
        String value = text(node, field);
        if (value == null || value.isBlank()) {
            throw new BadRequestException(
                    "La rappresentazione risultante dal PATCH ha il campo '" + field + "' mancante o vuoto.");
        }
        if (value.length() > maxLength) {
            throw new BadRequestException(
                    "Il campo '" + field + "' supera la lunghezza massima di " + maxLength + " caratteri.");
        }
        return value;
    }

    private static Boolean requireBoolean(ObjectNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isBoolean()) {
            throw new BadRequestException(
                    "La rappresentazione risultante dal PATCH ha il campo '" + field + "' mancante o non booleano.");
        }
        return value.asBoolean();
    }

    private List<Intermediario> findSlice(Specification<Intermediario> spec, Sort sort,
                                          int offset, int maxResults) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Intermediario> q = cb.createQuery(Intermediario.class);
        Root<Intermediario> root = q.from(Intermediario.class);
        Predicate predicate = spec.toPredicate(root, q, cb);
        if (predicate != null) {
            q.where(predicate);
        }
        if (sort != null && sort.isSorted()) {
            List<Order> orders = new ArrayList<>();
            for (Sort.Order o : sort) {
                Path<Object> path = root.get(o.getProperty());
                orders.add(o.isAscending() ? cb.asc(path) : cb.desc(path));
            }
            q.orderBy(orders);
        }
        TypedQuery<Intermediario> typed = entityManager.createQuery(q)
                .setFirstResult(offset)
                .setMaxResults(maxResults);
        return typed.getResultList();
    }
}
