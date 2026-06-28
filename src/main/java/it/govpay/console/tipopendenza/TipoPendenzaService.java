package it.govpay.console.tipopendenza;

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
import it.govpay.console.entity.TipoVersamento;
import it.govpay.console.intermediario.JsonPatchApplier;
import it.govpay.console.model.JsonPatchOperation;
import it.govpay.console.model.ListTipiPendenza200Response;
import it.govpay.console.model.Pagination;
import it.govpay.console.model.TipoPendenza;
import it.govpay.console.model.TipoPendenzaCreate;
import it.govpay.console.model.TipoPendenzaReplace;
import it.govpay.console.repository.TipoVersamentoRepository;
import it.govpay.console.security.CurrentOperatorService;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.web.BadRequestException;
import it.govpay.console.web.ConflictException;
import it.govpay.console.web.IfMatchMismatchException;
import it.govpay.console.web.NotFoundException;
import it.govpay.console.web.PreconditionRequiredException;
import it.govpay.console.web.RepresentationEtag;
import it.govpay.console.web.RepresentationValidator;
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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class TipoPendenzaService {

    private static final Logger log = LoggerFactory.getLogger(TipoPendenzaService.class);

    public static final String AZIONE_AUDIT_CREATE = "TIPO_PENDENZA_CREATE";
    public static final String AZIONE_AUDIT_MODIFICA = "TIPO_PENDENZA_MODIFICA";

    private static final int MAX_DESCRIZIONE = 255;

    private final TipoVersamentoRepository repository;
    private final TipoPendenzaMapper mapper;
    private final RepresentationValidator representationValidator;
    private final CurrentOperatorService currentOperatorService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    public TipoPendenzaService(TipoVersamentoRepository repository,
                               TipoPendenzaMapper mapper,
                               RepresentationValidator representationValidator,
                               CurrentOperatorService currentOperatorService,
                               AuditService auditService,
                               ObjectMapper objectMapper) {
        this.repository = repository;
        this.mapper = mapper;
        this.representationValidator = representationValidator;
        this.currentOperatorService = currentOperatorService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ListTipiPendenza200Response list(TipoPendenzaListQuery query) {
        log.debug("listTipiPendenza filtri[idTipoPendenza={}, descrizione={}, abilitato={}], "
                        + "page={}, limit={}, sort={}, total={}",
                query.idTipoPendenza(), query.descrizione(), query.abilitato(),
                query.page(), query.limit(), query.sort(), query.total());

        Specification<TipoVersamento> spec = Specification.allOf(
                Stream.of(
                        TipoPendenzaSpecifications.codTipoVersamentoPartial(query.idTipoPendenza()),
                        TipoPendenzaSpecifications.descrizionePartial(query.descrizione()),
                        TipoPendenzaSpecifications.abilitatoExact(query.abilitato()))
                .filter(Objects::nonNull)
                .toList());

        Sort sort;
        try {
            sort = TipoPendenzaSortParser.parse(query.sort());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }

        int page = query.page();
        int limit = query.limit();
        boolean wantTotal = Boolean.TRUE.equals(query.total());

        Pagination pagination = new Pagination(page, limit, false);
        List<TipoVersamento> rows;
        if (wantTotal) {
            Page<TipoVersamento> p = repository.findAll(spec, PageRequest.of(page - 1, limit, sort));
            rows = p.getContent();
            pagination.setHasNextPage(p.hasNext());
            pagination.setTotalResults(p.getTotalElements());
            pagination.setTotalPages(p.getTotalPages());
        } else {
            List<TipoVersamento> sliced = findSlice(spec, sort, (page - 1) * limit, limit + 1);
            boolean hasNext = sliced.size() > limit;
            rows = hasNext ? sliced.subList(0, limit) : sliced;
            pagination.setHasNextPage(hasNext);
        }

        ListTipiPendenza200Response response = new ListTipiPendenza200Response();
        response.setResults(rows.stream().map(mapper::toSummary).toList());
        response.setPagination(pagination);
        return response;
    }

    @Transactional(readOnly = true)
    public ResponseEntity<TipoPendenza> get(String idTipoPendenza) {
        return ok(load(idTipoPendenza));
    }

    @Transactional
    public ResponseEntity<TipoPendenza> create(TipoPendenzaCreate body, HttpServletRequest request) {
        if (repository.existsByCodTipoVersamento(body.getIdTipoPendenza())) {
            throw new ConflictException(
                    "Esiste gia' una tipologia di pendenza con idTipoPendenza '"
                            + body.getIdTipoPendenza() + "'.");
        }
        TipoVersamento entity = new TipoVersamento();
        entity.setCodTipoVersamento(body.getIdTipoPendenza());
        mapper.applyWritable(entity, body.getDescrizione(), body.getCodificaIUV(),
                body.getPagaTerzi(), body.getAbilitato(),
                body.getPortaleBackoffice(), body.getPortalePagamento(),
                body.getAvvisaturaMail(), body.getAvvisaturaAppIO(),
                body.getVisualizzazione(), body.getTracciatoCsv());
        TipoVersamento saved = repository.save(entity);

        audit(AZIONE_AUDIT_CREATE, saved, request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(saved.getCodTipoVersamento())
                .toUri();
        TipoPendenza dto = mapper.toDetail(saved);
        return ResponseEntity.created(location)
                .eTag(RepresentationEtag.of(dto, objectMapper))
                .body(dto);
    }

    @Transactional
    public ResponseEntity<TipoPendenza> replace(String idTipoPendenza, TipoPendenzaReplace body,
                                                String ifMatch, HttpServletRequest request) {
        TipoVersamento entity = load(idTipoPendenza);
        checkIfMatch(ifMatch, entity);

        mapper.applyWritable(entity, body.getDescrizione(), body.getCodificaIUV(),
                body.getPagaTerzi(), body.getAbilitato(),
                body.getPortaleBackoffice(), body.getPortalePagamento(),
                body.getAvvisaturaMail(), body.getAvvisaturaAppIO(),
                body.getVisualizzazione(), body.getTracciatoCsv());
        TipoVersamento saved = repository.save(entity);

        audit(AZIONE_AUDIT_MODIFICA, saved, request);
        return ok(saved);
    }

    @Transactional
    public ResponseEntity<TipoPendenza> patch(String idTipoPendenza, List<JsonPatchOperation> operations,
                                              String ifMatch, HttpServletRequest request) {
        TipoVersamento entity = load(idTipoPendenza);
        checkIfMatch(ifMatch, entity);

        ObjectNode current = objectMapper.valueToTree(mapper.toDetail(entity));
        ObjectNode patched = JsonPatchApplier.apply(current, operations, objectMapper);

        TipoPendenza result;
        try {
            result = objectMapper.treeToValue(patched, TipoPendenza.class);
        } catch (JacksonException e) {
            throw new BadRequestException(
                    "La rappresentazione risultante dal PATCH non e' una tipologia di pendenza valida: "
                            + e.getOriginalMessage());
        }

        if (!idTipoPendenza.equals(result.getIdTipoPendenza())) {
            throw new BadRequestException(
                    "Il campo 'idTipoPendenza' non puo' essere modificato tramite PATCH.");
        }
        if (result.getDescrizione() == null || result.getDescrizione().isBlank()) {
            throw new BadRequestException(
                    "La rappresentazione risultante dal PATCH ha il campo 'descrizione' mancante o vuoto.");
        }
        if (result.getDescrizione().length() > MAX_DESCRIZIONE) {
            throw new BadRequestException(
                    "Il campo 'descrizione' supera la lunghezza massima di " + MAX_DESCRIZIONE + " caratteri.");
        }
        // Vincoli OpenAPI residui (pattern codificaIUV, lunghezze, nested) sul risultato.
        representationValidator.validate(result);

        mapper.applyWritable(entity, result.getDescrizione(), result.getCodificaIUV(),
                result.getPagaTerzi(), result.getAbilitato(),
                result.getPortaleBackoffice(), result.getPortalePagamento(),
                result.getAvvisaturaMail(), result.getAvvisaturaAppIO(),
                result.getVisualizzazione(), result.getTracciatoCsv());
        TipoVersamento saved = repository.save(entity);

        audit(AZIONE_AUDIT_MODIFICA, saved, request);
        return ok(saved);
    }

    private ResponseEntity<TipoPendenza> ok(TipoVersamento entity) {
        TipoPendenza dto = mapper.toDetail(entity);
        return ResponseEntity.ok()
                .eTag(RepresentationEtag.of(dto, objectMapper))
                .body(dto);
    }

    private TipoVersamento load(String idTipoPendenza) {
        return repository.findByCodTipoVersamento(idTipoPendenza)
                .orElseThrow(() -> new NotFoundException("Tipologia di pendenza non trovata: " + idTipoPendenza));
    }

    private void checkIfMatch(String ifMatch, TipoVersamento entity) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new PreconditionRequiredException(
                    "Header 'If-Match' obbligatorio per le operazioni di modifica.");
        }
        if (!RepresentationEtag.matches(ifMatch, mapper.toDetail(entity), objectMapper)) {
            throw new IfMatchMismatchException(
                    "L'header 'If-Match' non corrisponde alla versione corrente della tipologia di pendenza.");
        }
    }

    private void audit(String azione, TipoVersamento entity, HttpServletRequest request) {
        OperatoreCorrente operatore = currentOperatorService.get();
        Map<String, Object> dettaglio = new HashMap<>();
        dettaglio.put("idTipoPendenza", entity.getCodTipoVersamento());
        auditService.registra(azione, entity.getId(), dettaglio, operatore, request);
    }

    private List<TipoVersamento> findSlice(Specification<TipoVersamento> spec, Sort sort,
                                           int offset, int maxResults) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<TipoVersamento> q = cb.createQuery(TipoVersamento.class);
        Root<TipoVersamento> root = q.from(TipoVersamento.class);
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
        TypedQuery<TipoVersamento> typed = entityManager.createQuery(q)
                .setFirstResult(offset)
                .setMaxResults(maxResults);
        return typed.getResultList();
    }
}
