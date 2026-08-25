package it.govpay.console.rendicontazione;

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

import it.govpay.console.entity.Fr;
import it.govpay.console.model.AclServizio;
import it.govpay.console.model.ListFlussiRendicontazione200Response;
import it.govpay.console.model.Pagination;
import it.govpay.console.model.FlussoRendicontazioneSummary;
import it.govpay.console.pagination.CursorCodec;
import it.govpay.console.repository.FrRepository;
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
 * Ricerca paginata della collection {@code GET /flussi-rendicontazione}. Espone
 * solo {@link FlussoRendicontazioneSummary} (metadata-only): nessun dato
 * personale, quindi nessun audit GDPR sulla lista.
 *
 * <p>Due modalità mutuamente esclusive (come {@code GET /ricevute}): offset
 * (Slice di default, Page con {@code total=true}) e cursor keyset opt-in. Il
 * cursor riusa {@link CursorCodec} a 2 chiavi ({@code dataOraFlusso}, {@code id}
 * tecnico come tie-breaker) invece di implementarne uno a 3 chiavi per
 * {@code (dataOraFlusso, idFlusso, revisione)}: l'{@code id} tecnico è già
 * univoco e monotono, garantisce lo stesso determinismo con meno codice.
 */
@Service
public class FrSearchService {

    private static final Logger log = LoggerFactory.getLogger(FrSearchService.class);

    private final FrRepository frRepository;
    private final FrMapper mapper;
    private final CurrentOperatorService currentOperatorService;
    private final AclAuthorizer aclAuthorizer;

    @PersistenceContext
    private EntityManager entityManager;

    public FrSearchService(FrRepository frRepository,
                           FrMapper mapper,
                           CurrentOperatorService currentOperatorService,
                           AclAuthorizer aclAuthorizer) {
        this.frRepository = frRepository;
        this.mapper = mapper;
        this.currentOperatorService = currentOperatorService;
        this.aclAuthorizer = aclAuthorizer;
    }

    @Transactional(readOnly = true)
    public ListFlussiRendicontazione200Response search(FrListQuery query) {
        aclAuthorizer.requireLettura(AclServizio.RENDICONTAZIONI_E_INCASSI);
        OperatoreCorrente operatore = currentOperatorService.get();
        log.debug("listFlussiRendicontazione filtri[idDominio={}, idFlusso={}, idPsp={}, dataDa={}, dataA={}, "
                        + "stato={}, incassato={}, iuv={}], page={}, limit={}, sort={}, total={}, cursor={}, operatore={}",
                query.idDominio(), query.idFlusso(), query.idPsp(), query.dataDa(), query.dataA(),
                query.stato(), query.incassato(), query.iuv(),
                query.page(), query.limit(), query.sort(), query.total(),
                query.cursor() != null, operatore.principal());

        Specification<Fr> spec = Specification.allOf(
                Stream.of(
                        FrSpecifications.idDominioExact(query.idDominio()),
                        FrSpecifications.idFlussoExact(query.idFlusso()),
                        FrSpecifications.idPspExact(query.idPsp()),
                        FrSpecifications.dataAcquisizioneDa(query.dataDa()),
                        FrSpecifications.dataAcquisizioneA(query.dataA()),
                        FrSpecifications.statoEsatto(query.stato()),
                        FrSpecifications.incassato(query.incassato()),
                        FrSpecifications.iuvExact(query.iuv()),
                        FrSpecifications.visibiliPerOperatore(operatore))
                .filter(Objects::nonNull)
                .toList());

        ListFlussiRendicontazione200Response response = new ListFlussiRendicontazione200Response();
        List<Fr> rows = query.cursor() != null
                ? listCursorMode(spec, query, response)
                : listOffsetMode(spec, query, response);

        List<FlussoRendicontazioneSummary> summaries = rows.stream().map(mapper::toSummary).toList();
        response.setResults(summaries);
        log.debug("listFlussiRendicontazione risultati={} nextCursor={} pagination={}",
                summaries.size(), response.getNextCursor() != null, response.getPagination() != null);
        return response;
    }

    private List<Fr> listOffsetMode(Specification<Fr> spec,
                                    FrListQuery query,
                                    ListFlussiRendicontazione200Response response) {
        Sort sort;
        try {
            sort = FrSortParser.parse(query.sort());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }

        int page = query.page();
        int limit = query.limit();
        boolean wantTotal = Boolean.TRUE.equals(query.total());

        Pagination pagination = new Pagination(page, limit, false);
        List<Fr> rows;
        if (wantTotal) {
            Page<Fr> p = frRepository.findAll(spec, PageRequest.of(page - 1, limit, sort));
            rows = p.getContent();
            pagination.setHasNextPage(p.hasNext());
            pagination.setTotalResults(p.getTotalElements());
            pagination.setTotalPages(p.getTotalPages());
        } else {
            List<Fr> sliced = findSlice(spec, sort, (page - 1) * limit, limit + 1);
            boolean hasNext = sliced.size() > limit;
            rows = hasNext ? sliced.subList(0, limit) : sliced;
            pagination.setHasNextPage(hasNext);
        }
        response.setPagination(pagination);
        return rows;
    }

    /**
     * Modalità cursor keyset: ordina per {@code (dataOraFlusso DESC, id DESC)} e
     * filtra con {@code WHERE data < :ts OR (data = :ts AND id < :id)}.
     */
    private List<Fr> listCursorMode(Specification<Fr> spec,
                                    FrListQuery query,
                                    ListFlussiRendicontazione200Response response) {
        CursorCodec.Cursor cursor = query.cursor().isBlank() ? null : CursorCodec.decode(query.cursor());
        int limit = query.limit();

        List<Fr> sliced = findByCursor(spec, cursor, limit + 1);
        boolean hasNext = sliced.size() > limit;
        List<Fr> rows = hasNext ? sliced.subList(0, limit) : sliced;

        if (hasNext && !rows.isEmpty()) {
            Fr last = rows.get(rows.size() - 1);
            response.setNextCursor(CursorCodec.encode(last.getDataOraFlusso(), last.getId()));
        }
        return rows;
    }

    private List<Fr> findByCursor(Specification<Fr> spec, CursorCodec.Cursor cursor, int maxResults) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Fr> q = cb.createQuery(Fr.class);
        Root<Fr> root = q.from(Fr.class);

        Predicate specPredicate = spec.toPredicate(root, q, cb);
        Path<OffsetDateTime> dataPath = root.get("dataOraFlusso");
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

        TypedQuery<Fr> typed = entityManager.createQuery(q).setMaxResults(maxResults);
        return typed.getResultList();
    }

    private List<Fr> findSlice(Specification<Fr> spec, Sort sort, int offset, int maxResults) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Fr> q = cb.createQuery(Fr.class);
        Root<Fr> root = q.from(Fr.class);
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
        TypedQuery<Fr> typed = entityManager.createQuery(q)
                .setFirstResult(offset)
                .setMaxResults(maxResults);
        return typed.getResultList();
    }
}
