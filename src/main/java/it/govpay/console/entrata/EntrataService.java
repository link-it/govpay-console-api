package it.govpay.console.entrata;

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
import it.govpay.console.entity.TipoTributo;
import it.govpay.console.intermediario.JsonPatchApplier;
import it.govpay.console.model.Entrata;
import it.govpay.console.model.EntrataCreate;
import it.govpay.console.model.EntrataReplace;
import it.govpay.console.model.JsonPatchOperation;
import it.govpay.console.model.ListEntrate200Response;
import it.govpay.console.model.Pagination;
import it.govpay.console.model.TipoContabilita;
import it.govpay.console.repository.TipoTributoRepository;
import it.govpay.console.security.CurrentOperatorService;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.web.BadRequestException;
import it.govpay.console.web.ConflictException;
import it.govpay.console.web.IfMatchMismatchException;
import it.govpay.console.web.NotFoundException;
import it.govpay.console.web.PreconditionRequiredException;
import it.govpay.console.web.RepresentationEtag;
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
public class EntrataService {

    private static final Logger log = LoggerFactory.getLogger(EntrataService.class);

    public static final String AZIONE_AUDIT_CREATE = "ENTRATA_CREATE";
    public static final String AZIONE_AUDIT_MODIFICA = "ENTRATA_MODIFICA";

    private static final int MAX_DESCRIZIONE = 255;
    private static final int MAX_COD_CONTABILITA = 255;

    private final TipoTributoRepository repository;
    private final EntrataMapper mapper;
    private final CurrentOperatorService currentOperatorService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    public EntrataService(TipoTributoRepository repository,
                          EntrataMapper mapper,
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
    public ListEntrate200Response list(EntrataListQuery query) {
        log.debug("listEntrate filtri[idEntrata={}, descrizione={}], page={}, limit={}, sort={}, total={}",
                query.idEntrata(), query.descrizione(),
                query.page(), query.limit(), query.sort(), query.total());

        Specification<TipoTributo> spec = Specification.allOf(
                Stream.of(
                        EntrataSpecifications.codTributoPartial(query.idEntrata()),
                        EntrataSpecifications.descrizionePartial(query.descrizione()))
                .filter(Objects::nonNull)
                .toList());

        Sort sort;
        try {
            sort = EntrataSortParser.parse(query.sort());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }

        int page = query.page();
        int limit = query.limit();
        boolean wantTotal = Boolean.TRUE.equals(query.total());

        Pagination pagination = new Pagination(page, limit, false);
        List<TipoTributo> rows;
        if (wantTotal) {
            Page<TipoTributo> p = repository.findAll(spec, PageRequest.of(page - 1, limit, sort));
            rows = p.getContent();
            pagination.setHasNextPage(p.hasNext());
            pagination.setTotalResults(p.getTotalElements());
            pagination.setTotalPages(p.getTotalPages());
        } else {
            List<TipoTributo> sliced = findSlice(spec, sort, (page - 1) * limit, limit + 1);
            boolean hasNext = sliced.size() > limit;
            rows = hasNext ? sliced.subList(0, limit) : sliced;
            pagination.setHasNextPage(hasNext);
        }

        ListEntrate200Response response = new ListEntrate200Response();
        response.setResults(rows.stream().map(mapper::toSummary).toList());
        response.setPagination(pagination);
        return response;
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Entrata> get(String idEntrata) {
        return ok(load(idEntrata));
    }

    @Transactional
    public ResponseEntity<Entrata> create(EntrataCreate body, HttpServletRequest request) {
        if (repository.existsByCodTributo(body.getIdEntrata())) {
            throw new ConflictException(
                    "Esiste gia' una tipologia di entrata con idEntrata '" + body.getIdEntrata() + "'.");
        }
        TipoTributo entity = new TipoTributo();
        entity.setCodTributo(body.getIdEntrata());
        entity.setDescrizione(body.getDescrizione());
        entity.setTipoContabilita(mapper.toCodifica(body.getTipoContabilita()));
        entity.setCodContabilita(body.getCodiceContabilita());
        TipoTributo saved = repository.save(entity);

        audit(AZIONE_AUDIT_CREATE, saved, request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(saved.getCodTributo())
                .toUri();
        Entrata dto = mapper.toDetail(saved);
        return ResponseEntity.created(location)
                .eTag(RepresentationEtag.of(dto, objectMapper))
                .body(dto);
    }

    @Transactional
    public ResponseEntity<Entrata> replace(String idEntrata, EntrataReplace body,
                                           String ifMatch, HttpServletRequest request) {
        TipoTributo entity = load(idEntrata);
        checkIfMatch(ifMatch, entity);

        entity.setDescrizione(body.getDescrizione());
        entity.setTipoContabilita(mapper.toCodifica(body.getTipoContabilita()));
        entity.setCodContabilita(body.getCodiceContabilita());
        TipoTributo saved = repository.save(entity);

        audit(AZIONE_AUDIT_MODIFICA, saved, request);
        return ok(saved);
    }

    @Transactional
    public ResponseEntity<Entrata> patch(String idEntrata, List<JsonPatchOperation> operations,
                                         String ifMatch, HttpServletRequest request) {
        TipoTributo entity = load(idEntrata);
        checkIfMatch(ifMatch, entity);

        ObjectNode current = objectMapper.valueToTree(mapper.toDetail(entity));
        ObjectNode patched = JsonPatchApplier.apply(current, operations, objectMapper);

        String patchedId = text(patched, "idEntrata");
        if (!idEntrata.equals(patchedId)) {
            throw new BadRequestException(
                    "Il campo 'idEntrata' non puo' essere modificato tramite PATCH.");
        }

        String descrizione = requireText(patched, "descrizione", MAX_DESCRIZIONE);
        String codiceContabilita = requireText(patched, "codiceContabilita", MAX_COD_CONTABILITA);
        TipoContabilita tipoContabilita = requireTipoContabilita(patched);

        entity.setDescrizione(descrizione);
        entity.setTipoContabilita(mapper.toCodifica(tipoContabilita));
        entity.setCodContabilita(codiceContabilita);
        TipoTributo saved = repository.save(entity);

        audit(AZIONE_AUDIT_MODIFICA, saved, request);
        return ok(saved);
    }

    private ResponseEntity<Entrata> ok(TipoTributo entity) {
        Entrata dto = mapper.toDetail(entity);
        return ResponseEntity.ok()
                .eTag(RepresentationEtag.of(dto, objectMapper))
                .body(dto);
    }

    private TipoTributo load(String idEntrata) {
        return repository.findByCodTributo(idEntrata)
                .orElseThrow(() -> new NotFoundException("Tipologia di entrata non trovata: " + idEntrata));
    }

    private void checkIfMatch(String ifMatch, TipoTributo entity) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new PreconditionRequiredException(
                    "Header 'If-Match' obbligatorio per le operazioni di modifica.");
        }
        if (!RepresentationEtag.matches(ifMatch, mapper.toDetail(entity), objectMapper)) {
            throw new IfMatchMismatchException(
                    "L'header 'If-Match' non corrisponde alla versione corrente della tipologia di entrata.");
        }
    }

    private void audit(String azione, TipoTributo entity, HttpServletRequest request) {
        OperatoreCorrente operatore = currentOperatorService.get();
        Map<String, Object> dettaglio = new HashMap<>();
        dettaglio.put("idEntrata", entity.getCodTributo());
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

    private static TipoContabilita requireTipoContabilita(ObjectNode node) {
        String value = text(node, "tipoContabilita");
        if (value == null || value.isBlank()) {
            throw new BadRequestException(
                    "La rappresentazione risultante dal PATCH ha il campo 'tipoContabilita' mancante o vuoto.");
        }
        try {
            return TipoContabilita.fromValue(value);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(
                    "Il campo 'tipoContabilita' ha un valore non ammesso: '" + value + "'.");
        }
    }

    private List<TipoTributo> findSlice(Specification<TipoTributo> spec, Sort sort,
                                        int offset, int maxResults) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<TipoTributo> q = cb.createQuery(TipoTributo.class);
        Root<TipoTributo> root = q.from(TipoTributo.class);
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
        TypedQuery<TipoTributo> typed = entityManager.createQuery(q)
                .setFirstResult(offset)
                .setMaxResults(maxResults);
        return typed.getResultList();
    }
}
