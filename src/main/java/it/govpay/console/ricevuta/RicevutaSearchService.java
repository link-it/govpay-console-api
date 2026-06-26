package it.govpay.console.ricevuta;

import java.time.OffsetDateTime;
import java.util.List;
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

import it.govpay.console.entity.Rpt;
import it.govpay.console.model.ListRicevute200Response;
import it.govpay.console.model.Pagination;
import it.govpay.console.model.RicevutaSummary;
import it.govpay.console.pagination.CursorCodec;
import it.govpay.console.repository.RptRepository;
import it.govpay.console.security.CurrentOperatorService;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.web.BadRequestException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * Ricerca paginata della collection top-level {@code GET /ricevute}. Espone solo
 * {@link RicevutaSummary} (metadata-only): nessun dato personale, quindi nessun
 * audit GDPR sulla lista.
 *
 * <p>Due modalità mutuamente esclusive (come {@code GET /pendenze}): offset
 * (Slice di default, Page con {@code total=true}) e cursor keyset opt-in con
 * ordinamento fisso {@code (dataMsgRicevuta DESC, id DESC)}. La visibilità ACL è
 * sempre spinta nella query.
 */
@Service
public class RicevutaSearchService {

    private static final Logger log = LoggerFactory.getLogger(RicevutaSearchService.class);

    private final RptRepository rptRepository;
    private final RicevutaMapper mapper;
    private final CurrentOperatorService currentOperatorService;

    @PersistenceContext
    private EntityManager entityManager;

    public RicevutaSearchService(RptRepository rptRepository,
                                 RicevutaMapper mapper,
                                 CurrentOperatorService currentOperatorService) {
        this.rptRepository = rptRepository;
        this.mapper = mapper;
        this.currentOperatorService = currentOperatorService;
    }

    @Transactional(readOnly = true)
    public ListRicevute200Response search(RicevutaListQuery query) {
        OperatoreCorrente operatore = currentOperatorService.get();
        log.debug("listRicevute filtri[iuv={}, idDominio={}, idRicevuta={}, dataDa={}, dataA={}], "
                        + "page={}, limit={}, sort={}, total={}, cursor={}, operatore={}",
                query.iuv(), query.idDominio(), query.idRicevuta(), query.dataDa(), query.dataA(),
                query.page(), query.limit(), query.sort(), query.total(),
                query.cursor() != null, operatore.principal());

        Specification<Rpt> spec = Specification.allOf(
                Stream.of(
                        RptSpecifications.conRicevuta(),
                        RptSpecifications.iuvExact(query.iuv()),
                        RptSpecifications.idDominioExact(query.idDominio()),
                        RptSpecifications.idRicevutaExact(query.idRicevuta()),
                        RptSpecifications.dataPagamentoDa(query.dataDa()),
                        RptSpecifications.dataPagamentoA(query.dataA()),
                        RptSpecifications.visibiliPerOperatore(operatore))
                .filter(Objects::nonNull)
                .toList());

        ListRicevute200Response response = new ListRicevute200Response();
        List<Rpt> rows = query.cursor() != null
                ? listCursorMode(spec, query, response)
                : listOffsetMode(spec, query, response);

        List<RicevutaSummary> summaries = rows.stream().map(mapper::toSummary).toList();
        response.setResults(summaries);
        log.debug("listRicevute risultati={} nextCursor={} pagination={}",
                summaries.size(), response.getNextCursor() != null, response.getPagination() != null);
        return response;
    }

    private List<Rpt> listOffsetMode(Specification<Rpt> spec,
                                     RicevutaListQuery query,
                                     ListRicevute200Response response) {
        Sort sort;
        try {
            sort = RicevutaSortParser.parse(query.sort());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }

        int page = query.page();
        int limit = query.limit();
        boolean wantTotal = Boolean.TRUE.equals(query.total());

        Pagination pagination = new Pagination(page, limit, false);
        List<Rpt> rows;
        if (wantTotal) {
            Page<Rpt> p = rptRepository.findAll(spec, PageRequest.of(page - 1, limit, sort));
            rows = p.getContent();
            pagination.setHasNextPage(p.hasNext());
            pagination.setTotalResults(p.getTotalElements());
            pagination.setTotalPages(p.getTotalPages());
        } else {
            List<Rpt> sliced = findSlice(spec, sort, (page - 1) * limit, limit + 1);
            boolean hasNext = sliced.size() > limit;
            rows = hasNext ? sliced.subList(0, limit) : sliced;
            pagination.setHasNextPage(hasNext);
        }
        response.setPagination(pagination);
        return rows;
    }

    /**
     * Modalità cursor keyset: ordina per {@code (dataMsgRicevuta DESC, id DESC)} e
     * filtra con {@code WHERE data < :ts OR (data = :ts AND id < :id)}. Carica
     * {@code limit+1} righe per determinare {@code hasNext}. Cursor vuoto = prima
     * pagina (filtro keyset omesso).
     */
    private List<Rpt> listCursorMode(Specification<Rpt> spec,
                                     RicevutaListQuery query,
                                     ListRicevute200Response response) {
        CursorCodec.Cursor cursor = query.cursor().isBlank() ? null : CursorCodec.decode(query.cursor());
        int limit = query.limit();

        List<Rpt> sliced = findByCursor(spec, cursor, limit + 1);
        boolean hasNext = sliced.size() > limit;
        List<Rpt> rows = hasNext ? sliced.subList(0, limit) : sliced;

        if (hasNext && !rows.isEmpty()) {
            Rpt last = rows.get(rows.size() - 1);
            response.setNextCursor(CursorCodec.encode(last.getDataMsgRicevuta(), last.getId()));
        }
        return rows;
    }

    private List<Rpt> findByCursor(Specification<Rpt> spec, CursorCodec.Cursor cursor, int maxResults) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Rpt> q = cb.createQuery(Rpt.class);
        Root<Rpt> root = q.from(Rpt.class);

        Predicate specPredicate = spec.toPredicate(root, q, cb);
        Path<OffsetDateTime> dataPath = root.get("dataMsgRicevuta");
        Path<Long> idPath = root.get("id");

        Predicate where;
        if (cursor != null) {
            Predicate keyset = cb.or(
                    cb.lessThan(dataPath, cursor.timestamp()),
                    cb.and(
                            cb.equal(dataPath, cursor.timestamp()),
                            cb.lessThan(idPath, cursor.id())));
            where = specPredicate != null ? cb.and(specPredicate, keyset) : keyset;
        } else {
            where = specPredicate;
        }
        if (where != null) {
            q.where(where);
        }
        q.orderBy(cb.desc(dataPath), cb.desc(idPath));

        TypedQuery<Rpt> typed = entityManager.createQuery(q).setMaxResults(maxResults);
        return typed.getResultList();
    }

    private List<Rpt> findSlice(Specification<Rpt> spec, Sort sort, int offset, int maxResults) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Rpt> q = cb.createQuery(Rpt.class);
        Root<Rpt> root = q.from(Rpt.class);
        Predicate predicate = spec.toPredicate(root, q, cb);
        if (predicate != null) {
            q.where(predicate);
        }
        if (sort != null && sort.isSorted()) {
            List<jakarta.persistence.criteria.Order> orders = new java.util.ArrayList<>();
            for (Sort.Order o : sort) {
                Path<Object> path = root.get(o.getProperty());
                orders.add(o.isAscending() ? cb.asc(path) : cb.desc(path));
            }
            q.orderBy(orders);
        }
        TypedQuery<Rpt> typed = entityManager.createQuery(q)
                .setFirstResult(offset)
                .setMaxResults(maxResults);
        return typed.getResultList();
    }
}
