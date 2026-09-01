package it.govpay.console.ricevuta;

import java.time.OffsetDateTime;
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
import it.govpay.console.entity.Rpt;
import it.govpay.console.model.ListRicevute200Response;
import it.govpay.console.model.Pagination;
import it.govpay.console.model.RicevutaSummary;
import it.govpay.console.pagination.CursorCodec;
import it.govpay.console.pendenza.PendenzaService;
import it.govpay.console.repository.RptRepository;
import it.govpay.console.security.CurrentOperatorService;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.web.BadRequestException;
import it.govpay.console.web.ListQueryValidator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Ricerca paginata della collection top-level {@code GET /ricevute}. Espone solo
 * {@link RicevutaSummary} (metadata-only): nessun dato personale in risposta.
 * La ricerca per {@code identificativoDebitore} genera comunque un audit
 * (issue #68 §A), riusando la stessa azione di {@code /pendenze} — vedi
 * {@link PendenzaService#AZIONE_AUDIT_RICERCA}: non e' un audit distinto per
 * endpoint, e' lo stesso evento ("ricerca per debitore") tracciato una volta
 * sola indipendentemente da dove parte la richiesta.
 *
 * <p>La ricerca per {@code anagraficaDebitore} (issue #68 §C) usa invece
 * un'azione di audit dedicata ({@link #AZIONE_AUDIT_RICERCA_ANAGRAFICA}): non
 * e' un riuso di un filtro esistente come {@code identificativoDebitore}, e'
 * una ricerca testuale su un nome — un vettore di rischio diverso (enumerabile,
 * non un match esatto su un codice) che merita un evento distinguibile in
 * {@code gp_audit}. Vincoli aggiuntivi validati qui, non nello {@code Specification}:
 * lunghezza minima del termine e limite risultati piu' stretto.
 *
 * <p>Due modalità mutuamente esclusive (come {@code GET /pendenze}): offset
 * (Slice di default, Page con {@code total=true}) e cursor keyset opt-in con
 * ordinamento fisso {@code (dataMsgRicevuta DESC, id DESC)}. La visibilità ACL è
 * sempre spinta nella query.
 */
@Service
public class RicevutaSearchService {

    private static final Logger log = LoggerFactory.getLogger(RicevutaSearchService.class);

    public static final String AZIONE_AUDIT_RICERCA_ANAGRAFICA = "RICEVUTE_RICERCA_PER_ANAGRAFICA_DEBITORE";

    private static final int MAX_ID_TIPO_PENDENZA = 50;
    private static final int MAX_DIREZIONE_DIVISIONE = 50;
    private static final int MIN_ANAGRAFICA_DEBITORE_LENGTH = 3;
    private static final int MAX_LIMIT_ANAGRAFICA_DEBITORE = 50;

    private final RptRepository rptRepository;
    private final RicevutaMapper mapper;
    private final CurrentOperatorService currentOperatorService;
    private final AuditService auditService;

    @PersistenceContext
    private EntityManager entityManager;

    public RicevutaSearchService(RptRepository rptRepository,
                                 RicevutaMapper mapper,
                                 CurrentOperatorService currentOperatorService,
                                 AuditService auditService) {
        this.rptRepository = rptRepository;
        this.mapper = mapper;
        this.currentOperatorService = currentOperatorService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public ListRicevute200Response search(RicevutaListQuery query, HttpServletRequest request) {
        OperatoreCorrente operatore = currentOperatorService.get();
        log.debug("listRicevute filtri[iuv={}, idDominio={}, idRicevuta={}, dataRicevutaDa={}, "
                        + "dataRicevutaA={}, dataRichiestaDa={}, dataRichiestaA={}, idA2A={}, idPendenza={}, "
                        + "identificativoDebitore={}, idUnitaOperativa={}, idTipoPendenza={}, direzione={}, "
                        + "divisione={}, tassonomia={}, anagraficaDebitore={}], "
                        + "page={}, limit={}, sort={}, total={}, cursor={}, operatore={}",
                query.iuv(), query.idDominio(), query.idRicevuta(),
                query.dataRicevutaDa(), query.dataRicevutaA(), query.dataRichiestaDa(), query.dataRichiestaA(),
                query.idA2A(), query.idPendenza(), query.identificativoDebitore(), query.idUnitaOperativa(),
                query.idTipoPendenza(), query.direzione(), query.divisione(), query.tassonomia(),
                query.anagraficaDebitore() != null,
                query.page(), query.limit(), query.sort(), query.total(),
                query.cursor() != null, operatore.principal());

        String anagraficaDebitore = validateAnagraficaDebitore(query.anagraficaDebitore(), query.limit());

        List<String> idTipoPendenza = ListQueryValidator.normalizeCsvList(
                query.idTipoPendenza(), "idTipoPendenza", MAX_ID_TIPO_PENDENZA);
        List<String> direzione = ListQueryValidator.normalizeCsvList(
                query.direzione(), "direzione", MAX_DIREZIONE_DIVISIONE);
        List<String> divisione = ListQueryValidator.normalizeCsvList(
                query.divisione(), "divisione", MAX_DIREZIONE_DIVISIONE);

        Specification<Rpt> spec = Specification.allOf(
                Stream.of(
                        RptSpecifications.conRicevuta(),
                        RptSpecifications.iuvExact(query.iuv()),
                        RptSpecifications.idDominioExact(query.idDominio()),
                        RptSpecifications.idRicevutaExact(query.idRicevuta()),
                        RptSpecifications.dataRicevutaDa(query.dataRicevutaDa()),
                        RptSpecifications.dataRicevutaA(query.dataRicevutaA()),
                        RptSpecifications.dataRichiestaDa(query.dataRichiestaDa()),
                        RptSpecifications.dataRichiestaA(query.dataRichiestaA()),
                        RptSpecifications.idA2AExact(query.idA2A()),
                        RptSpecifications.idPendenzaExact(query.idPendenza()),
                        RptSpecifications.identificativoDebitoreExact(query.identificativoDebitore()),
                        RptSpecifications.idUnitaOperativaExact(query.idUnitaOperativa()),
                        RptSpecifications.idTipoPendenzaIn(idTipoPendenza),
                        RptSpecifications.direzioneIn(direzione),
                        RptSpecifications.divisioneIn(divisione),
                        RptSpecifications.tassonomiaExact(query.tassonomia()),
                        RptSpecifications.anagraficaDebitorePartial(anagraficaDebitore),
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

        if (query.identificativoDebitore() != null && !query.identificativoDebitore().isBlank()) {
            Map<String, Object> dettaglio = new HashMap<>();
            dettaglio.put("identificativoDebitore", query.identificativoDebitore());
            Map<String, Object> altriFiltri = new HashMap<>();
            if (query.idDominio() != null) altriFiltri.put("idDominio", query.idDominio());
            if (query.idPendenza() != null) altriFiltri.put("idPendenza", query.idPendenza());
            if (query.idA2A() != null) altriFiltri.put("idA2A", query.idA2A());
            dettaglio.put("altriFiltri", altriFiltri);
            dettaglio.put("totaleRisultati", summaries.size());
            auditService.registra(PendenzaService.AZIONE_AUDIT_RICERCA, 0L, dettaglio, operatore, request);
        }

        if (anagraficaDebitore != null) {
            Map<String, Object> dettaglio = new HashMap<>();
            dettaglio.put("anagraficaDebitore", anagraficaDebitore);
            dettaglio.put("totaleRisultati", summaries.size());
            auditService.registra(AZIONE_AUDIT_RICERCA_ANAGRAFICA, 0L, dettaglio, operatore, request);
        }

        return response;
    }

    /**
     * {@code null} se il parametro non e' presente; altrimenti applica i vincoli
     * dell'issue #68 §C, entrambi anti-enumerazione: lunghezza minima del
     * termine e limite risultati piu' stretto delle altre ricerche.
     */
    private String validateAnagraficaDebitore(String raw, int limit) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.length() < MIN_ANAGRAFICA_DEBITORE_LENGTH) {
            throw new BadRequestException("'anagraficaDebitore' richiede almeno "
                    + MIN_ANAGRAFICA_DEBITORE_LENGTH + " caratteri.");
        }
        if (limit > MAX_LIMIT_ANAGRAFICA_DEBITORE) {
            throw new BadRequestException("'limit' non puo' superare " + MAX_LIMIT_ANAGRAFICA_DEBITORE
                    + " quando 'anagraficaDebitore' e' valorizzato.");
        }
        return trimmed;
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
