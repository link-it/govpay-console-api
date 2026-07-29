package it.govpay.console.entecreditore;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.audit.AuditService;
import it.govpay.console.entity.EnteCreditoreCache;
import it.govpay.console.model.EnteCreditore;
import it.govpay.console.model.ListEntiCreditori200Response;
import it.govpay.console.model.Pagination;
import it.govpay.console.repository.EnteCreditoreCacheRepository;
import it.govpay.console.security.CurrentOperatorService;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.web.BadRequestException;
import it.govpay.console.web.NotFoundException;
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

@Service
public class EnteCreditoreService {

    private static final Logger log = LoggerFactory.getLogger(EnteCreditoreService.class);

    public static final String AZIONE_AUDIT_RICERCA = "ENTI_CREDITORI_RICERCA";
    public static final String AZIONE_AUDIT_VISUALIZZA = "ENTE_CREDITORE_VISUALIZZA";

    private final EnteCreditoreCacheRepository repository;
    private final EnteCreditoreMapper mapper;
    private final CurrentOperatorService currentOperatorService;
    private final AuditService auditService;

    @PersistenceContext
    private EntityManager entityManager;

    public EnteCreditoreService(EnteCreditoreCacheRepository repository,
                                EnteCreditoreMapper mapper,
                                CurrentOperatorService currentOperatorService,
                                AuditService auditService) {
        this.repository = repository;
        this.mapper = mapper;
        this.currentOperatorService = currentOperatorService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public ListEntiCreditori200Response list(EnteCreditoreListQuery query, HttpServletRequest request) {
        log.debug("listEntiCreditori filtro[search={}], page={}, limit={}, sort={}, total={}",
                query.search(), query.page(), query.limit(), query.sort(), query.total());

        Specification<EnteCreditoreCache> spec = Specification.allOf(
                Stream.of(EnteCreditoreSpecifications.searchPartial(query.search()))
                        .filter(Objects::nonNull)
                        .toList());

        Sort sort;
        try {
            sort = EnteCreditoreSortParser.parse(query.sort());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }

        int page = query.page();
        int limit = query.limit();
        boolean wantTotal = Boolean.TRUE.equals(query.total());

        Pagination pagination = new Pagination(page, limit, false);
        List<EnteCreditoreCache> rows;
        if (wantTotal) {
            Page<EnteCreditoreCache> p = repository.findAll(spec, PageRequest.of(page - 1, limit, sort));
            rows = p.getContent();
            pagination.setHasNextPage(p.hasNext());
            pagination.setTotalResults(p.getTotalElements());
            pagination.setTotalPages(p.getTotalPages());
        } else {
            List<EnteCreditoreCache> sliced = findSlice(spec, sort, (page - 1) * limit, limit + 1);
            boolean hasNext = sliced.size() > limit;
            rows = hasNext ? sliced.subList(0, limit) : sliced;
            pagination.setHasNextPage(hasNext);
        }

        ListEntiCreditori200Response response = new ListEntiCreditori200Response();
        response.setResults(rows.stream().map(mapper::toSummary).toList());
        response.setPagination(pagination);

        OperatoreCorrente operatore = currentOperatorService.get();
        Map<String, Object> dettaglio = new HashMap<>();
        dettaglio.put("search", query.search());
        dettaglio.put("totaleRisultati", response.getResults().size());
        auditService.registra(AZIONE_AUDIT_RICERCA, 0L, dettaglio, operatore, request);

        return response;
    }

    @Transactional(readOnly = true)
    public EnteCreditore get(String taxCode, HttpServletRequest request) {
        EnteCreditoreCache entity = repository.findByCodFiscale(taxCode)
                .orElseThrow(() -> new NotFoundException(
                        "Nessun ente creditore trovato per il codice fiscale '" + taxCode
                                + "' (dato non presente nella cache di sincronizzazione pagoPA)."));

        EnteCreditore dto = mapper.toDetail(entity);

        OperatoreCorrente operatore = currentOperatorService.get();
        Map<String, Object> dettaglio = new HashMap<>();
        dettaglio.put("taxCode", taxCode);
        auditService.registra(AZIONE_AUDIT_VISUALIZZA, entity.getId(), dettaglio, operatore, request);

        return dto;
    }

    private List<EnteCreditoreCache> findSlice(Specification<EnteCreditoreCache> spec, Sort sort,
                                               int offset, int maxResults) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<EnteCreditoreCache> q = cb.createQuery(EnteCreditoreCache.class);
        Root<EnteCreditoreCache> root = q.from(EnteCreditoreCache.class);
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
        TypedQuery<EnteCreditoreCache> typed = entityManager.createQuery(q)
                .setFirstResult(offset)
                .setMaxResults(maxResults);
        return typed.getResultList();
    }
}
