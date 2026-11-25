package it.govpay.console.pendenza;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.audit.AuditService;
import it.govpay.console.pagination.CursorCodec;
import it.govpay.console.model.ListPendenze200Response;
import it.govpay.console.model.Pagination;
import it.govpay.console.model.PendenzaExpand;
import it.govpay.console.model.PendenzaLinks;
import it.govpay.console.model.PendenzaSummary;
import it.govpay.console.repository.UnitaOperativaRepository;
import it.govpay.pendenze.entity.PdDebitore;
import it.govpay.pendenze.entity.Pendenza;
import it.govpay.console.security.CurrentOperatorService;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.web.BadRequestException;
import it.govpay.console.web.ListQueryValidator;
import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Subgraph;
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
public class PendenzaService {

    private static final Logger log = LoggerFactory.getLogger(PendenzaService.class);

    public static final String AZIONE_AUDIT_RICERCA = "PENDENZE_RICERCA_PER_DEBITORE";

    private static final int MAX_ID_TIPO_PENDENZA = 50;
    private static final int MAX_DIREZIONE_DIVISIONE = 50;

    private final PendenzaLettura lettura;
    private final UnitaOperativaRepository unitaOperativaRepository;
    private final PendenzaAnagrafiche anagrafiche;
    private final PendenzaMapper mapper;
    private final PendenzaLinksBuilder linksBuilder;
    private final CurrentOperatorService currentOperatorService;
    private final AuditService auditService;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    public PendenzaService(PendenzaLettura lettura,
                           UnitaOperativaRepository unitaOperativaRepository,
                           PendenzaAnagrafiche anagrafiche,
                           PendenzaMapper mapper,
                           PendenzaLinksBuilder linksBuilder,
                           CurrentOperatorService currentOperatorService,
                           AuditService auditService,
                           Clock clock) {
        this.lettura = lettura;
        this.unitaOperativaRepository = unitaOperativaRepository;
        this.anagrafiche = anagrafiche;
        this.mapper = mapper;
        this.linksBuilder = linksBuilder;
        this.currentOperatorService = currentOperatorService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public it.govpay.console.model.Pendenza get(String idA2A, String idPendenza, Set<PendenzaExpand> expand) {
        OperatoreCorrente operatore = currentOperatorService.get();
        log.debug("getPendenza idA2A={} idPendenza={} expand={} operatore={}",
                idA2A, idPendenza, expand, operatore.principal());

        PendenzaLettura.Letta letta = lettura.leggiVisibile(idA2A, idPendenza, operatore);
        Pendenza p = letta.pendenza();
        PendenzaAnagrafiche.Contesto contesto = letta.contesto();

        it.govpay.console.model.Pendenza pendenza = mapper.toDetail(idA2A, p, contesto, anagrafiche.risolviVoci(p.getVoci()), expand);
        PendenzaLinks links = linksBuilder.build(idA2A, idPendenza,
                pendenza.getPosizioneDebitoria().getIdPosizioneDebitoria(), p.getNumeroAvviso());
        pendenza.setLinks(links);
        log.debug("getPendenza dettaglio costruito idPendenza={} voci={} avviso={}",
                idPendenza, pendenza.getVoci().size(), links.getAvviso() != null);
        return pendenza;
    }

    @Transactional(readOnly = true)
    public ListPendenze200Response list(PendenzaListQuery query, HttpServletRequest request) {
        OperatoreCorrente operatore = currentOperatorService.get();
        log.debug("listPendenze filtri[idPendenza={}, numeroAvviso={}, idDominio={}, identificativoDebitore={}, "
                        + "stato={}, dataDa={}, dataA={}, iuv={}, direzione={}, divisione={}, idA2A={}, "
                        + "idTipoPendenza={}], page={}, limit={}, sort={}, total={}, cursor={}, operatore={}",
                query.idPendenza(), query.numeroAvviso(), query.idDominio(), query.identificativoDebitore(),
                query.stato(), query.dataDa(), query.dataA(), query.iuv(), query.direzione(), query.divisione(),
                query.idA2A(), query.idTipoPendenza(),
                query.page(), query.limit(), query.sort(), query.total(),
                query.cursor() != null, operatore.principal());

        if (query.dataDa() != null && query.dataA() != null && query.dataDa().isAfter(query.dataA())) {
            throw new BadRequestException("'dataDa' non puo' essere successiva a 'dataA'.");
        }
        List<String> idTipoPendenza = ListQueryValidator.normalizeCsvList(
                query.idTipoPendenza(), "idTipoPendenza", MAX_ID_TIPO_PENDENZA);
        List<String> direzione = ListQueryValidator.normalizeCsvList(
                query.direzione(), "direzione", MAX_DIREZIONE_DIVISIONE);
        List<String> divisione = ListQueryValidator.normalizeCsvList(
                query.divisione(), "divisione", MAX_DIREZIONE_DIVISIONE);

        OffsetDateTime now = OffsetDateTime.now(clock);
        LocalDate oggi = LocalDate.now(clock);
        // Domini di cui l'operatore ha la UO EC visibile: una query per richiesta, e solo
        // se l'operatore e' limitato a UO specifiche.
        Set<Long> dominiConUoEc = !operatore.tuttiIDomini() && !operatore.idUoVisibili().isEmpty()
                ? unitaOperativaRepository.findDominioIdsConUoEcTra(operatore.idUoVisibili())
                : Set.of();

        // Spring Data JPA 4.x: Specification.allOf rifiuta null. Filtriamo i predicati assenti.
        Specification<Pendenza> spec = Specification.allOf(
                java.util.stream.Stream.of(
                        PendenzaSpecifications.conOpzione(),
                        PendenzaSpecifications.idPendenzaPartial(query.idPendenza()),
                        PendenzaSpecifications.numeroAvvisoExact(query.numeroAvviso()),
                        PendenzaSpecifications.idDominioExact(query.idDominio()),
                        PendenzaSpecifications.identificativoDebitoreExact(query.identificativoDebitore()),
                        PendenzaSpecifications.statoExact(query.stato(), now, oggi),
                        PendenzaSpecifications.dataCreazioneDa(query.dataDa()),
                        PendenzaSpecifications.dataCreazioneA(query.dataA()),
                        PendenzaSpecifications.iuvExact(query.iuv()),
                        PendenzaSpecifications.direzioneIn(direzione),
                        PendenzaSpecifications.divisioneIn(divisione),
                        PendenzaSpecifications.idA2AExact(query.idA2A()),
                        PendenzaSpecifications.idTipoPendenzaIn(idTipoPendenza),
                        PendenzaSpecifications.visibiliPerOperatore(operatore, dominiConUoEc))
                .filter(java.util.Objects::nonNull)
                .toList());

        ListPendenze200Response response = new ListPendenze200Response();
        List<Pendenza> rows;

        // cursor != null ⇔ "?cursor=..." presente in URL (anche vuoto = prima pagina cursor mode)
        if (query.cursor() != null) {
            rows = listCursorMode(spec, query, response);
        } else {
            rows = listOffsetMode(spec, query, response);
        }

        // Anagrafiche della pagina in blocco: una query per anagrafica, non una per riga.
        PendenzaAnagrafiche.Pagina pagina = anagrafiche.risolviPagina(rows);
        List<PendenzaSummary> summaries = rows.stream()
                .map(p -> mapper.toSummary(p, pagina.idA2A(p), pagina.contesto(p)))
                .toList();
        response.setResults(summaries);
        log.debug("listPendenze risultati={} nextCursor={} pagination={}",
                summaries.size(), response.getNextCursor() != null, response.getPagination() != null);

        if (query.identificativoDebitore() != null && !query.identificativoDebitore().isBlank()) {
            Map<String, Object> dettaglio = new HashMap<>();
            dettaglio.put("identificativoDebitore", query.identificativoDebitore());
            Map<String, Object> altriFiltri = new HashMap<>();
            if (query.idDominio() != null) altriFiltri.put("idDominio", query.idDominio());
            if (query.idPendenza() != null) altriFiltri.put("idPendenza", query.idPendenza());
            if (query.numeroAvviso() != null) altriFiltri.put("numeroAvviso", query.numeroAvviso());
            dettaglio.put("altriFiltri", altriFiltri);
            dettaglio.put("totaleRisultati", summaries.size());
            auditService.registra(AZIONE_AUDIT_RICERCA, 0L, dettaglio, operatore, request);
        }

        return response;
    }

    private List<Pendenza> listOffsetMode(Specification<Pendenza> spec,
                                            PendenzaListQuery query,
                                            ListPendenze200Response response) {
        Sort sort;
        try {
            sort = PendenzaSortParser.parse(query.sort());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }

        int page = query.page();
        int limit = query.limit();
        boolean wantTotal = Boolean.TRUE.equals(query.total());

        Pagination pagination = new Pagination(page, limit, false);
        List<Pendenza> rows;
        if (wantTotal) {
            long total = count(spec);
            rows = findSlice(spec, sort, (page - 1) * limit, limit);
            pagination.setHasNextPage((long) page * limit < total);
            pagination.setTotalResults(total);
            pagination.setTotalPages((int) ((total + limit - 1) / limit));
        } else {
            List<Pendenza> sliced = findSlice(spec, sort, (page - 1) * limit, limit + 1);
            boolean hasNext = sliced.size() > limit;
            rows = hasNext ? sliced.subList(0, limit) : sliced;
            pagination.setHasNextPage(hasNext);
        }
        response.setPagination(pagination);
        return rows;
    }

    /**
     * Modalita' cursor (keyset): ordina per
     * {@code (dataCreazione DESC, id DESC)} e filtra con
     * {@code WHERE data < :ts OR (data = :ts AND id < :id)}. Carica {@code limit+1}
     * righe per determinare {@code hasNext}.
     *
     * <p>Se il cursor e' vuoto (caso "prima pagina cursor mode", attivato da
     * {@code ?cursor=} senza valore), il filtro keyset viene omesso e si
     * usano solo l'ordinamento e il limit.
     *
     * <p>Verifica indici (non applicata: lo schema di {@code versamenti}
     * e' condiviso col core, la migrazione va concordata a parte). Sul DDL V1
     * reale esiste solo {@code idx_vrs_data_creaz(data_creazione DESC)}, a singola
     * colonna: non copre il tiebreak su {@code id} di questa query. Proposta:
     * {@code CREATE INDEX idx_vrs_data_creaz_id ON versamenti (data_creazione DESC, id DESC);}
     * (sostituirebbe {@code idx_vrs_data_creaz}, che ne e' un prefisso).
     */
    private List<Pendenza> listCursorMode(Specification<Pendenza> spec,
                                            PendenzaListQuery query,
                                            ListPendenze200Response response) {
        CursorCodec.Cursor cursor = query.cursor().isBlank() ? null : CursorCodec.decode(query.cursor());
        int limit = query.limit();

        List<Pendenza> sliced = findByCursor(spec, cursor, limit + 1);
        boolean hasNext = sliced.size() > limit;
        List<Pendenza> rows = hasNext ? sliced.subList(0, limit) : sliced;

        if (hasNext && !rows.isEmpty()) {
            Pendenza last = rows.get(rows.size() - 1);
            response.setNextCursor(CursorCodec.encode(last.getDataCreazione(), last.getId()));
        }
        return rows;
    }

    private long count(Specification<Pendenza> spec) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> q = cb.createQuery(Long.class);
        Root<Pendenza> root = q.from(Pendenza.class);
        q.select(cb.count(root));
        Predicate predicate = spec.toPredicate(root, q, cb);
        if (predicate != null) {
            q.where(predicate);
        }
        return entityManager.createQuery(q).getSingleResult();
    }

    private List<Pendenza> findByCursor(Specification<Pendenza> spec,
                                          CursorCodec.Cursor cursor,
                                          int maxResults) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Pendenza> q = cb.createQuery(Pendenza.class);
        Root<Pendenza> root = q.from(Pendenza.class);

        Predicate specPredicate = spec.toPredicate(root, q, cb);
        Path<OffsetDateTime> dataPath = root.get("dataCreazione");
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

        TypedQuery<Pendenza> typed = entityManager.createQuery(q).setMaxResults(maxResults);
        typed.setHint("jakarta.persistence.fetchgraph", summaryEntityGraph());
        return typed.getResultList();
    }

    private List<Pendenza> findSlice(Specification<Pendenza> spec, Sort sort, int offset, int maxResults) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Pendenza> q = cb.createQuery(Pendenza.class);
        Root<Pendenza> root = q.from(Pendenza.class);
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
        TypedQuery<Pendenza> typed = entityManager.createQuery(q)
                .setFirstResult(offset)
                .setMaxResults(maxResults);
        typed.setHint("jakarta.persistence.fetchgraph", summaryEntityGraph());
        return typed.getResultList();
    }

    /** Opzione, debitore dell'opzione e posizione: servono a stato, UO, debitore e riferimenti di ogni riga. */
    private EntityGraph<Pendenza> summaryEntityGraph() {
        EntityGraph<Pendenza> graph = entityManager.createEntityGraph(Pendenza.class);
        Subgraph<PdDebitore> pdDebitore = graph.addSubgraph("opzionePagamento").addSubgraph("pdDebitore");
        pdDebitore.addSubgraph("posizioneDebitoria");
        pdDebitore.addSubgraph("debitore");
        return graph;
    }
}
