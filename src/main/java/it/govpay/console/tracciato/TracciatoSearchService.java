package it.govpay.console.tracciato;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.entity.Tracciato;
import it.govpay.console.model.ListTracciatiPendenze200Response;
import it.govpay.console.model.Pagination;
import it.govpay.console.model.TracciatoPendenze;
import it.govpay.console.pagination.CursorCodec;
import it.govpay.console.repository.TracciatoRepository;
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

/**
 * {@code GET /pendenze/tracciati} (lista, cursor/offset) e
 * {@code GET /pendenze/tracciati/{id}} (dettaglio). Stesso pattern di
 * {@code PendenzaService}: {@code Specification} composte, cursor keyset su
 * {@code (dataCaricamento DESC, id DESC)}.
 */
@Service
public class TracciatoSearchService {

    private final TracciatoRepository repository;
    private final TracciatoMapper mapper;
    private final CurrentOperatorService currentOperatorService;

    @PersistenceContext
    private EntityManager entityManager;

    public TracciatoSearchService(TracciatoRepository repository,
                                  TracciatoMapper mapper,
                                  CurrentOperatorService currentOperatorService) {
        this.repository = repository;
        this.mapper = mapper;
        this.currentOperatorService = currentOperatorService;
    }

    @Transactional(readOnly = true)
    public TracciatoPendenze get(Long id) {
        OperatoreCorrente operatore = currentOperatorService.get();
        Tracciato tracciato = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Tracciato non trovato: " + id));
        if (!it.govpay.console.security.DominioVisibilita.isVisibile(tracciato.getDominio().getId(), operatore)) {
            // 404 anti-leak: indistinguibile da "id sconosciuto".
            throw new NotFoundException("Tracciato non trovato: " + id);
        }
        return mapper.toDto(tracciato);
    }

    @Transactional(readOnly = true)
    public ListTracciatiPendenze200Response list(TracciatoListQuery query) {
        OperatoreCorrente operatore = currentOperatorService.get();

        Specification<Tracciato> spec = Specification.allOf(
                java.util.stream.Stream.of(
                        TracciatoSpecifications.idDominioExact(query.idDominio()),
                        TracciatoSpecifications.statoExact(query.stato()),
                        TracciatoSpecifications.dataDaInclusive(query.dataDa()),
                        TracciatoSpecifications.dataAInclusive(query.dataA()),
                        TracciatoSpecifications.operatoreMittentePartial(query.operatoreMittente()),
                        TracciatoSpecifications.formatoExact(query.formatoRichiesta()),
                        TracciatoSpecifications.visibiliPerOperatore(operatore))
                .filter(java.util.Objects::nonNull)
                .toList());

        ListTracciatiPendenze200Response response = new ListTracciatiPendenze200Response();
        List<Tracciato> rows = query.cursor() != null
                ? listCursorMode(spec, query, response)
                : listOffsetMode(spec, query, response);

        response.setResults(rows.stream().map(mapper::toDto).toList());
        return response;
    }

    private List<Tracciato> listOffsetMode(Specification<Tracciato> spec,
                                           TracciatoListQuery query,
                                           ListTracciatiPendenze200Response response) {
        Sort sort;
        try {
            sort = TracciatoSortParser.parse(query.sort());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }

        int page = query.page();
        int limit = query.limit();
        boolean wantTotal = Boolean.TRUE.equals(query.total());

        Pagination pagination = new Pagination(page, limit, false);
        List<Tracciato> rows;
        if (wantTotal) {
            Page<Tracciato> p = repository.findAll(spec, PageRequest.of(page - 1, limit, sort));
            rows = p.getContent();
            pagination.setHasNextPage(p.hasNext());
            pagination.setTotalResults(p.getTotalElements());
            pagination.setTotalPages(p.getTotalPages());
        } else {
            List<Tracciato> sliced = findSlice(spec, sort, (page - 1) * limit, limit + 1);
            boolean hasNext = sliced.size() > limit;
            rows = hasNext ? sliced.subList(0, limit) : sliced;
            pagination.setHasNextPage(hasNext);
        }
        response.setPagination(pagination);
        return rows;
    }

    private List<Tracciato> listCursorMode(Specification<Tracciato> spec,
                                           TracciatoListQuery query,
                                           ListTracciatiPendenze200Response response) {
        CursorCodec.Cursor cursor = query.cursor().isBlank() ? null : CursorCodec.decode(query.cursor());
        int limit = query.limit();

        List<Tracciato> sliced = findByCursor(spec, cursor, limit + 1);
        boolean hasNext = sliced.size() > limit;
        List<Tracciato> rows = hasNext ? sliced.subList(0, limit) : sliced;

        if (hasNext && !rows.isEmpty()) {
            Tracciato last = rows.get(rows.size() - 1);
            response.setNextCursor(CursorCodec.encode(last.getDataCaricamento(), last.getId()));
        }
        return rows;
    }

    private List<Tracciato> findByCursor(Specification<Tracciato> spec, CursorCodec.Cursor cursor, int maxResults) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tracciato> q = cb.createQuery(Tracciato.class);
        Root<Tracciato> root = q.from(Tracciato.class);

        Predicate specPredicate = spec.toPredicate(root, q, cb);
        Path<OffsetDateTime> dataPath = root.get("dataCaricamento");
        Path<Long> idPath = root.get("id");

        Predicate where;
        if (cursor != null) {
            Predicate keyset = cb.or(
                    cb.lessThan(dataPath, cursor.timestamp()),
                    cb.and(cb.equal(dataPath, cursor.timestamp()), cb.lessThan(idPath, cursor.id())));
            where = specPredicate != null ? cb.and(specPredicate, keyset) : keyset;
        } else {
            where = specPredicate;
        }
        if (where != null) {
            q.where(where);
        }
        q.orderBy(cb.desc(dataPath), cb.desc(idPath));

        return entityManager.createQuery(q).setMaxResults(maxResults).getResultList();
    }

    private List<Tracciato> findSlice(Specification<Tracciato> spec, Sort sort, int offset, int maxResults) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tracciato> q = cb.createQuery(Tracciato.class);
        Root<Tracciato> root = q.from(Tracciato.class);
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
        TypedQuery<Tracciato> typed = entityManager.createQuery(q).setFirstResult(offset).setMaxResults(maxResults);
        return typed.getResultList();
    }
}
