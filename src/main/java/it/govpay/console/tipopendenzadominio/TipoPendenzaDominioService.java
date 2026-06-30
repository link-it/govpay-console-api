package it.govpay.console.tipopendenzadominio;

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
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.TipoVersamento;
import it.govpay.console.entity.TipoVersamentoDominio;
import it.govpay.console.intermediario.JsonPatchApplier;
import it.govpay.console.model.JsonPatchOperation;
import it.govpay.console.model.ListTipiPendenzaDominio200Response;
import it.govpay.console.model.Pagination;
import it.govpay.console.model.TipoPendenzaDominio;
import it.govpay.console.model.TipoPendenzaDominioCreate;
import it.govpay.console.model.TipoPendenzaDominioReplace;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.repository.TipoVersamentoDominioRepository;
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
import it.govpay.console.web.UnprocessableEntityException;
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
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class TipoPendenzaDominioService {

    private static final Logger log = LoggerFactory.getLogger(TipoPendenzaDominioService.class);

    public static final String AZIONE_AUDIT_CREATE = "TIPO_PENDENZA_DOMINIO_CREATE";
    public static final String AZIONE_AUDIT_MODIFICA = "TIPO_PENDENZA_DOMINIO_MODIFICA";

    private final TipoVersamentoDominioRepository repository;
    private final DominioRepository dominioRepository;
    private final TipoVersamentoRepository tipoVersamentoRepository;
    private final TipoPendenzaDominioMapper mapper;
    private final RepresentationValidator representationValidator;
    private final CurrentOperatorService currentOperatorService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    public TipoPendenzaDominioService(TipoVersamentoDominioRepository repository,
                                      DominioRepository dominioRepository,
                                      TipoVersamentoRepository tipoVersamentoRepository,
                                      TipoPendenzaDominioMapper mapper,
                                      RepresentationValidator representationValidator,
                                      CurrentOperatorService currentOperatorService,
                                      AuditService auditService,
                                      ObjectMapper objectMapper) {
        this.repository = repository;
        this.dominioRepository = dominioRepository;
        this.tipoVersamentoRepository = tipoVersamentoRepository;
        this.mapper = mapper;
        this.representationValidator = representationValidator;
        this.currentOperatorService = currentOperatorService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ListTipiPendenzaDominio200Response list(String idDominio, TipoPendenzaDominioListQuery query) {
        Dominio parent = loadDominio(idDominio);
        log.debug("listTipiPendenzaDominio dominio={} filtri[idTipoPendenza={}, descrizione={}, abilitato={}], page={}, limit={}, sort={}, total={}",
                idDominio, query.idTipoPendenza(), query.descrizione(), query.abilitato(),
                query.page(), query.limit(), query.sort(), query.total());

        Specification<TipoVersamentoDominio> spec = Specification.allOf(
                Stream.of(
                        TipoPendenzaDominioSpecifications.byDominioId(parent.getId()),
                        TipoPendenzaDominioSpecifications.idTipoPendenzaPartial(query.idTipoPendenza()),
                        TipoPendenzaDominioSpecifications.descrizionePartial(query.descrizione()),
                        TipoPendenzaDominioSpecifications.abilitatoExact(query.abilitato()))
                .filter(Objects::nonNull)
                .toList());

        Sort sort;
        try {
            sort = TipoPendenzaDominioSortParser.parse(query.sort());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }

        int page = query.page();
        int limit = query.limit();
        boolean wantTotal = Boolean.TRUE.equals(query.total());

        Pagination pagination = new Pagination(page, limit, false);
        List<TipoVersamentoDominio> rows;
        if (wantTotal) {
            Page<TipoVersamentoDominio> p = repository.findAll(spec, PageRequest.of(page - 1, limit, sort));
            rows = p.getContent();
            pagination.setHasNextPage(p.hasNext());
            pagination.setTotalResults(p.getTotalElements());
            pagination.setTotalPages(p.getTotalPages());
        } else {
            List<TipoVersamentoDominio> sliced = findSlice(spec, sort, (page - 1) * limit, limit + 1);
            boolean hasNext = sliced.size() > limit;
            rows = hasNext ? sliced.subList(0, limit) : sliced;
            pagination.setHasNextPage(hasNext);
        }

        ListTipiPendenzaDominio200Response response = new ListTipiPendenzaDominio200Response();
        response.setResults(rows.stream().map(mapper::toSummary).toList());
        response.setPagination(pagination);
        return response;
    }

    @Transactional(readOnly = true)
    public ResponseEntity<TipoPendenzaDominio> get(String idDominio, String idTipoPendenza) {
        return ok(load(idDominio, idTipoPendenza));
    }

    @Transactional
    public ResponseEntity<TipoPendenzaDominio> create(String idDominio,
                                                      TipoPendenzaDominioCreate body,
                                                      HttpServletRequest request) {
        Dominio parent = loadDominio(idDominio);
        TipoVersamento tipoVersamento = tipoVersamentoRepository.findByCodTipoVersamento(body.getIdTipoPendenza())
                .orElseThrow(() -> new UnprocessableEntityException(
                        "Il tipo pendenza globale '" + body.getIdTipoPendenza() + "' non esiste."));
        if (repository.existsByDominio_IdAndTipoVersamento_CodTipoVersamento(parent.getId(), body.getIdTipoPendenza())) {
            throw new ConflictException("Esiste gia' il tipo pendenza '" + body.getIdTipoPendenza()
                    + "' per il dominio '" + idDominio + "'.");
        }

        TipoVersamentoDominio entity = new TipoVersamentoDominio();
        entity.setDominio(parent);
        entity.setTipoVersamento(tipoVersamento);
        mapper.applyWritable(entity, body.getCodificaIUV(), body.getPagaTerzi(), body.getAbilitato(),
                body.getPortaleBackoffice(), body.getPortalePagamento(), body.getAvvisaturaMail(),
                body.getAvvisaturaAppIO(), body.getVisualizzazione(), body.getTracciatoCsv());
        TipoVersamentoDominio saved = repository.save(entity);

        audit(AZIONE_AUDIT_CREATE, saved, request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(saved.getTipoVersamento().getCodTipoVersamento())
                .toUri();
        TipoPendenzaDominio dto = mapper.toDetail(saved);
        return ResponseEntity.created(location)
                .eTag(RepresentationEtag.of(dto, objectMapper))
                .body(dto);
    }

    @Transactional
    public ResponseEntity<TipoPendenzaDominio> replace(String idDominio, String idTipoPendenza,
                                                       TipoPendenzaDominioReplace body, String ifMatch,
                                                       HttpServletRequest request) {
        TipoVersamentoDominio entity = load(idDominio, idTipoPendenza);
        checkIfMatch(ifMatch, entity);

        mapper.applyWritable(entity, body.getCodificaIUV(), body.getPagaTerzi(), body.getAbilitato(),
                body.getPortaleBackoffice(), body.getPortalePagamento(), body.getAvvisaturaMail(),
                body.getAvvisaturaAppIO(), body.getVisualizzazione(), body.getTracciatoCsv());
        TipoVersamentoDominio saved = repository.save(entity);

        audit(AZIONE_AUDIT_MODIFICA, saved, request);
        return ok(saved);
    }

    @Transactional
    public ResponseEntity<TipoPendenzaDominio> patch(String idDominio, String idTipoPendenza,
                                                     List<JsonPatchOperation> operations, String ifMatch,
                                                     HttpServletRequest request) {
        TipoVersamentoDominio entity = load(idDominio, idTipoPendenza);
        checkIfMatch(ifMatch, entity);

        ObjectNode current = objectMapper.valueToTree(mapper.toDetail(entity));
        ObjectNode patched = JsonPatchApplier.apply(current, operations, objectMapper);

        TipoPendenzaDominio result;
        try {
            result = objectMapper.treeToValue(patched, TipoPendenzaDominio.class);
        } catch (JacksonException e) {
            throw new BadRequestException(
                    "La rappresentazione risultante dal PATCH non e' un tipo pendenza valido: " + e.getOriginalMessage());
        }

        if (!idTipoPendenza.equals(result.getIdTipoPendenza())) {
            throw new BadRequestException("Il campo 'idTipoPendenza' non puo' essere modificato tramite PATCH.");
        }
        if (!Objects.equals(current.get("tipoPendenza"), patched.get("tipoPendenza"))) {
            throw new BadRequestException(
                    "Il campo 'tipoPendenza' e' di sola lettura e non puo' essere modificato tramite PATCH.");
        }
        representationValidator.validate(result);

        mapper.applyWritable(entity, result.getCodificaIUV(), result.getPagaTerzi(), result.getAbilitato(),
                result.getPortaleBackoffice(), result.getPortalePagamento(), result.getAvvisaturaMail(),
                result.getAvvisaturaAppIO(), result.getVisualizzazione(), result.getTracciatoCsv());
        TipoVersamentoDominio saved = repository.save(entity);

        audit(AZIONE_AUDIT_MODIFICA, saved, request);
        return ok(saved);
    }

    // ---- helpers ----------------------------------------------------------

    private ResponseEntity<TipoPendenzaDominio> ok(TipoVersamentoDominio entity) {
        TipoPendenzaDominio dto = mapper.toDetail(entity);
        return ResponseEntity.ok()
                .eTag(RepresentationEtag.of(dto, objectMapper))
                .body(dto);
    }

    private Dominio loadDominio(String idDominio) {
        return dominioRepository.findByCodDominio(idDominio)
                .orElseThrow(() -> new NotFoundException("Dominio non trovato: " + idDominio));
    }

    private TipoVersamentoDominio load(String idDominio, String idTipoPendenza) {
        Dominio parent = loadDominio(idDominio);
        return repository.findByDominio_IdAndTipoVersamento_CodTipoVersamento(parent.getId(), idTipoPendenza)
                .orElseThrow(() -> new NotFoundException("Tipo pendenza non trovato: " + idTipoPendenza));
    }

    private void checkIfMatch(String ifMatch, TipoVersamentoDominio entity) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new PreconditionRequiredException(
                    "Header 'If-Match' obbligatorio per le operazioni di modifica.");
        }
        if (!RepresentationEtag.matches(ifMatch, mapper.toDetail(entity), objectMapper)) {
            throw new IfMatchMismatchException(
                    "L'header 'If-Match' non corrisponde alla versione corrente del tipo pendenza.");
        }
    }

    private void audit(String azione, TipoVersamentoDominio entity, HttpServletRequest request) {
        OperatoreCorrente operatore = currentOperatorService.get();
        Map<String, Object> dettaglio = new HashMap<>();
        dettaglio.put("idDominio", entity.getDominio().getCodDominio());
        dettaglio.put("idTipoPendenza", entity.getTipoVersamento().getCodTipoVersamento());
        auditService.registra(azione, entity.getId(), dettaglio, operatore, request);
    }

    private List<TipoVersamentoDominio> findSlice(Specification<TipoVersamentoDominio> spec, Sort sort,
                                                  int offset, int maxResults) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<TipoVersamentoDominio> q = cb.createQuery(TipoVersamentoDominio.class);
        Root<TipoVersamentoDominio> root = q.from(TipoVersamentoDominio.class);
        Predicate predicate = spec.toPredicate(root, q, cb);
        if (predicate != null) {
            q.where(predicate);
        }
        if (sort != null && sort.isSorted()) {
            List<Order> orders = new ArrayList<>();
            for (Sort.Order o : sort) {
                Path<Object> path = resolvePath(root, o.getProperty());
                orders.add(o.isAscending() ? cb.asc(path) : cb.desc(path));
            }
            q.orderBy(orders);
        }
        TypedQuery<TipoVersamentoDominio> typed = entityManager.createQuery(q)
                .setFirstResult(offset)
                .setMaxResults(maxResults);
        return typed.getResultList();
    }

    /** Risolve un path eventualmente annidato (es. {@code tipoVersamento.codTipoVersamento}). */
    private static Path<Object> resolvePath(Root<TipoVersamentoDominio> root, String property) {
        Path<Object> path = null;
        for (String part : property.split("\\.")) {
            path = path == null ? root.get(part) : path.get(part);
        }
        return path;
    }
}
