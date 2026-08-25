package it.govpay.console.riconciliazione;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.Incasso;
import it.govpay.console.eventi.EventoAcl;
import it.govpay.console.model.AclServizio;
import it.govpay.console.model.ListRiconciliazioni200Response;
import it.govpay.console.model.Pagination;
import it.govpay.console.model.RiconciliazioneSummary;
import it.govpay.console.pagination.CursorCodec;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.repository.IncassoRepository;
import it.govpay.console.security.AclAuthorizer;
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
 * Ricerca paginata della collection {@code GET /riconciliazioni}. Espone solo
 * {@link RiconciliazioneSummary} (metadata-only): nessun dato personale,
 * quindi nessun audit GDPR sulla lista.
 *
 * <p>Due modalità mutuamente esclusive (come {@code GET /flussi-rendicontazione}):
 * offset (Slice di default, Page con {@code total=true}) e cursor keyset
 * opt-in, ordinamento fisso {@code (dataOraIncasso, id)}.
 */
@Service
public class RiconciliazioneSearchService {

    private static final Logger log = LoggerFactory.getLogger(RiconciliazioneSearchService.class);

    private final IncassoRepository incassoRepository;
    private final DominioRepository dominioRepository;
    private final RiconciliazioneMapper mapper;
    private final CurrentOperatorService currentOperatorService;
    private final EventoAcl eventoAcl;
    private final AclAuthorizer aclAuthorizer;

    @PersistenceContext
    private EntityManager entityManager;

    public RiconciliazioneSearchService(IncassoRepository incassoRepository,
                                        DominioRepository dominioRepository,
                                        RiconciliazioneMapper mapper,
                                        CurrentOperatorService currentOperatorService,
                                        EventoAcl eventoAcl,
                                        AclAuthorizer aclAuthorizer) {
        this.incassoRepository = incassoRepository;
        this.dominioRepository = dominioRepository;
        this.mapper = mapper;
        this.currentOperatorService = currentOperatorService;
        this.eventoAcl = eventoAcl;
        this.aclAuthorizer = aclAuthorizer;
    }

    @Transactional(readOnly = true)
    public ListRiconciliazioni200Response search(RiconciliazioneListQuery query) {
        aclAuthorizer.requireLettura(AclServizio.RENDICONTAZIONI_E_INCASSI);
        OperatoreCorrente operatore = currentOperatorService.get();
        log.debug("listRiconciliazioni filtri[idDominio={}, dataDa={}, dataA={}, stato={}, sct={}, idFlusso={}, "
                        + "iuv={}], page={}, limit={}, sort={}, total={}, cursor={}, operatore={}",
                query.idDominio(), query.dataDa(), query.dataA(), query.stato(), query.sct(),
                query.idFlusso(), query.iuv(),
                query.page(), query.limit(), query.sort(), query.total(),
                query.cursor() != null, operatore.principal());

        List<String> codiciVisibili = operatore.tuttiIDomini() ? List.of() : eventoAcl.codiciVisibili(operatore);

        Specification<Incasso> spec = Specification.allOf(
                Stream.of(
                        IncassoSpecifications.idDominioExact(query.idDominio()),
                        IncassoSpecifications.dataDa(query.dataDa()),
                        IncassoSpecifications.dataA(query.dataA()),
                        IncassoSpecifications.statoEsatto(query.stato()),
                        IncassoSpecifications.sctParziale(query.sct()),
                        IncassoSpecifications.idFlussoExact(query.idFlusso()),
                        IncassoSpecifications.iuvExact(query.iuv()),
                        IncassoSpecifications.visibiliPerOperatore(operatore.tuttiIDomini(), codiciVisibili))
                .filter(Objects::nonNull)
                .toList());

        ListRiconciliazioni200Response response = new ListRiconciliazioni200Response();
        List<Incasso> rows = query.cursor() != null
                ? listCursorMode(spec, query, response)
                : listOffsetMode(spec, query, response);

        Map<String, Dominio> domini = loadDomini(rows);
        List<RiconciliazioneSummary> summaries = rows.stream().map(r -> mapper.toSummary(r, domini)).toList();
        response.setResults(summaries);
        log.debug("listRiconciliazioni risultati={} nextCursor={} pagination={}",
                summaries.size(), response.getNextCursor() != null, response.getPagination() != null);
        return response;
    }

    private Map<String, Dominio> loadDomini(List<Incasso> rows) {
        Set<String> codici = rows.stream().map(Incasso::getCodDominio).collect(Collectors.toSet());
        if (codici.isEmpty()) {
            return Map.of();
        }
        return dominioRepository.findByCodDominioIn(codici).stream()
                .collect(Collectors.toMap(Dominio::getCodDominio, d -> d));
    }

    private List<Incasso> listOffsetMode(Specification<Incasso> spec,
                                         RiconciliazioneListQuery query,
                                         ListRiconciliazioni200Response response) {
        Sort sort;
        try {
            sort = IncassoSortParser.parse(query.sort());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }

        int page = query.page();
        int limit = query.limit();
        boolean wantTotal = Boolean.TRUE.equals(query.total());

        Pagination pagination = new Pagination(page, limit, false);
        List<Incasso> rows;
        if (wantTotal) {
            Page<Incasso> p = incassoRepository.findAll(spec, PageRequest.of(page - 1, limit, sort));
            rows = p.getContent();
            pagination.setHasNextPage(p.hasNext());
            pagination.setTotalResults(p.getTotalElements());
            pagination.setTotalPages(p.getTotalPages());
        } else {
            List<Incasso> sliced = findSlice(spec, sort, (page - 1) * limit, limit + 1);
            boolean hasNext = sliced.size() > limit;
            rows = hasNext ? sliced.subList(0, limit) : sliced;
            pagination.setHasNextPage(hasNext);
        }
        response.setPagination(pagination);
        return rows;
    }

    /**
     * Modalità cursor keyset: ordina per {@code (dataOraIncasso DESC, id DESC)}
     * e filtra con {@code WHERE data < :ts OR (data = :ts AND id < :id)}.
     */
    private List<Incasso> listCursorMode(Specification<Incasso> spec,
                                         RiconciliazioneListQuery query,
                                         ListRiconciliazioni200Response response) {
        CursorCodec.Cursor cursor = query.cursor().isBlank() ? null : CursorCodec.decode(query.cursor());
        int limit = query.limit();

        List<Incasso> sliced = findByCursor(spec, cursor, limit + 1);
        boolean hasNext = sliced.size() > limit;
        List<Incasso> rows = hasNext ? sliced.subList(0, limit) : sliced;

        if (hasNext && !rows.isEmpty()) {
            Incasso last = rows.get(rows.size() - 1);
            response.setNextCursor(CursorCodec.encode(last.getDataOraIncasso(), last.getId()));
        }
        return rows;
    }

    private List<Incasso> findByCursor(Specification<Incasso> spec, CursorCodec.Cursor cursor, int maxResults) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Incasso> q = cb.createQuery(Incasso.class);
        Root<Incasso> root = q.from(Incasso.class);

        Predicate specPredicate = spec.toPredicate(root, q, cb);
        Path<OffsetDateTime> dataPath = root.get("dataOraIncasso");
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

        TypedQuery<Incasso> typed = entityManager.createQuery(q).setMaxResults(maxResults);
        return typed.getResultList();
    }

    private List<Incasso> findSlice(Specification<Incasso> spec, Sort sort, int offset, int maxResults) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Incasso> q = cb.createQuery(Incasso.class);
        Root<Incasso> root = q.from(Incasso.class);
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
        TypedQuery<Incasso> typed = entityManager.createQuery(q)
                .setFirstResult(offset)
                .setMaxResults(maxResults);
        return typed.getResultList();
    }
}
