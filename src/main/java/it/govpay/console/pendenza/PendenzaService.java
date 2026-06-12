package it.govpay.console.pendenza;

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
import it.govpay.console.entity.Versamento;
import it.govpay.console.model.ListPendenze200Response;
import it.govpay.console.model.Pagination;
import it.govpay.console.model.Pendenza;
import it.govpay.console.model.PendenzaExpand;
import it.govpay.console.model.PendenzaLinks;
import it.govpay.console.model.PendenzaSummary;
import it.govpay.console.repository.VersamentoRepository;
import it.govpay.console.security.CurrentOperatorService;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.web.BadRequestException;
import it.govpay.console.web.NotFoundException;
import jakarta.persistence.EntityGraph;
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
public class PendenzaService {

    private static final Logger log = LoggerFactory.getLogger(PendenzaService.class);

    public static final String AZIONE_AUDIT_RICERCA = "PENDENZE_RICERCA_PER_DEBITORE";

    private final VersamentoRepository repository;
    private final PendenzaMapper mapper;
    private final PendenzaLinksBuilder linksBuilder;
    private final CurrentOperatorService currentOperatorService;
    private final AuditService auditService;

    @PersistenceContext
    private EntityManager entityManager;

    public PendenzaService(VersamentoRepository repository,
                           PendenzaMapper mapper,
                           PendenzaLinksBuilder linksBuilder,
                           CurrentOperatorService currentOperatorService,
                           AuditService auditService) {
        this.repository = repository;
        this.mapper = mapper;
        this.linksBuilder = linksBuilder;
        this.currentOperatorService = currentOperatorService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Pendenza get(String idA2A, String idPendenza, Set<PendenzaExpand> expand) {
        OperatoreCorrente operatore = currentOperatorService.get();
        log.debug("getPendenza idA2A={} idPendenza={} expand={} operatore={}",
                idA2A, idPendenza, expand, operatore.principal());

        Versamento versamento = repository.findDetail(idA2A, idPendenza)
                .orElseThrow(() -> new NotFoundException(
                        "Pendenza non trovata: idA2A=" + idA2A + ", idPendenza=" + idPendenza));

        if (!isVisibile(versamento, operatore)) {
            log.debug("getPendenza ACL nega l'accesso (404 anti-leak) idA2A={} idPendenza={} principal={}",
                    idA2A, idPendenza, operatore.principal());
            throw new NotFoundException(
                    "Pendenza non trovata: idA2A=" + idA2A + ", idPendenza=" + idPendenza);
        }

        Pendenza pendenza = mapper.toDetail(versamento, expand);
        PendenzaLinks links = linksBuilder.build(idA2A, idPendenza,
                versamento.getNumeroAvviso(), pendenza.getStato());
        pendenza.setLinks(links);
        log.debug("getPendenza dettaglio costruito idPendenza={} voci={} avviso={} ricevuta={}",
                idPendenza, pendenza.getVoci().size(), links.getAvviso() != null, links.getRicevuta() != null);
        return pendenza;
    }

    private static boolean isVisibile(Versamento v, OperatoreCorrente operatore) {
        if (!isDominioOrUoVisible(v, operatore)) {
            return false;
        }
        if (!operatore.tuttiITipiVersamento()) {
            if (v.getTipoVersamento() == null) {
                return false;
            }
            return operatore.idTipiVersamentoVisibili().contains(v.getTipoVersamento().getId());
        }
        return true;
    }

    private static boolean isDominioOrUoVisible(Versamento v, OperatoreCorrente operatore) {
        if (operatore.tuttiIDomini()) {
            return true;
        }
        if (v.getDominio() == null) {
            return false;
        }
        if (operatore.idDominiInteri().contains(v.getDominio().getId())) {
            return true;
        }
        if (v.getUnitaOperativa() != null
                && operatore.idUoVisibili().contains(v.getUnitaOperativa().getId())) {
            return true;
        }
        return false;
    }

    @Transactional(readOnly = true)
    public ListPendenze200Response list(PendenzaListQuery query, HttpServletRequest request) {
        OperatoreCorrente operatore = currentOperatorService.get();
        log.debug("listPendenze filtri[idPendenza={}, numeroAvviso={}, idDominio={}, identificativoDebitore={}], "
                        + "page={}, limit={}, sort={}, total={}, cursor={}, operatore={}",
                query.idPendenza(), query.numeroAvviso(), query.idDominio(), query.identificativoDebitore(),
                query.page(), query.limit(), query.sort(), query.total(),
                query.cursor() != null, operatore.principal());

        Specification<Versamento> spec = Specification.allOf(
                PendenzaSpecifications.idPendenzaPartial(query.idPendenza()),
                PendenzaSpecifications.numeroAvvisoExact(query.numeroAvviso()),
                PendenzaSpecifications.idDominioExact(query.idDominio()),
                PendenzaSpecifications.identificativoDebitoreExact(query.identificativoDebitore()),
                PendenzaSpecifications.visibiliPerOperatore(operatore));

        ListPendenze200Response response = new ListPendenze200Response();
        List<Versamento> rows;

        // cursor != null ⇔ "?cursor=..." presente in URL (anche vuoto = prima pagina cursor mode)
        if (query.cursor() != null) {
            rows = listCursorMode(spec, query, response);
        } else {
            rows = listOffsetMode(spec, query, response);
        }

        List<PendenzaSummary> summaries = rows.stream().map(mapper::toSummary).toList();
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

    private List<Versamento> listOffsetMode(Specification<Versamento> spec,
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
        List<Versamento> rows;
        if (wantTotal) {
            Page<Versamento> p = repository.findAll(spec, PageRequest.of(page - 1, limit, sort));
            rows = p.getContent();
            pagination.setHasNextPage(p.hasNext());
            pagination.setTotalResults(p.getTotalElements());
            pagination.setTotalPages(p.getTotalPages());
        } else {
            List<Versamento> sliced = findSlice(spec, sort, (page - 1) * limit, limit + 1);
            boolean hasNext = sliced.size() > limit;
            rows = hasNext ? sliced.subList(0, limit) : sliced;
            pagination.setHasNextPage(hasNext);
        }
        response.setPagination(pagination);
        return rows;
    }

    /**
     * Modalita' cursor (keyset): ordina per
     * {@code (dataOraUltimoAggiornamento DESC, id DESC)} e filtra con
     * {@code WHERE data < :ts OR (data = :ts AND id < :id)}. Carica {@code limit+1}
     * righe per determinare {@code hasNext}.
     *
     * <p>Se il cursor e' vuoto (caso "prima pagina cursor mode", attivato da
     * {@code ?cursor=} senza valore), il filtro keyset viene omesso e si
     * usano solo l'ordinamento e il limit.
     */
    private List<Versamento> listCursorMode(Specification<Versamento> spec,
                                            PendenzaListQuery query,
                                            ListPendenze200Response response) {
        CursorCodec.Cursor cursor = query.cursor().isBlank() ? null : CursorCodec.decode(query.cursor());
        int limit = query.limit();

        List<Versamento> sliced = findByCursor(spec, cursor, limit + 1);
        boolean hasNext = sliced.size() > limit;
        List<Versamento> rows = hasNext ? sliced.subList(0, limit) : sliced;

        if (hasNext && !rows.isEmpty()) {
            Versamento last = rows.get(rows.size() - 1);
            response.setNextCursor(CursorCodec.encode(last.getDataOraUltimoAggiornamento(), last.getId()));
        }
        return rows;
    }

    private List<Versamento> findByCursor(Specification<Versamento> spec,
                                          CursorCodec.Cursor cursor,
                                          int maxResults) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Versamento> q = cb.createQuery(Versamento.class);
        Root<Versamento> root = q.from(Versamento.class);

        Predicate specPredicate = spec.toPredicate(root, q, cb);
        Path<OffsetDateTime> dataPath = root.get("dataOraUltimoAggiornamento");
        Path<Long> idPath = root.get("id");

        Predicate where;
        if (cursor != null) {
            Predicate keyset = cb.or(
                    cb.lessThan(dataPath, cursor.dataOraUltimoAggiornamento()),
                    cb.and(
                            cb.equal(dataPath, cursor.dataOraUltimoAggiornamento()),
                            cb.lessThan(idPath, cursor.id())));
            where = specPredicate != null ? cb.and(specPredicate, keyset) : keyset;
        } else {
            where = specPredicate;
        }
        if (where != null) {
            q.where(where);
        }
        q.orderBy(cb.desc(dataPath), cb.desc(idPath));

        TypedQuery<Versamento> typed = entityManager.createQuery(q).setMaxResults(maxResults);
        typed.setHint("jakarta.persistence.fetchgraph", summaryEntityGraph());
        return typed.getResultList();
    }

    private List<Versamento> findSlice(Specification<Versamento> spec, Sort sort, int offset, int maxResults) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Versamento> q = cb.createQuery(Versamento.class);
        Root<Versamento> root = q.from(Versamento.class);
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
        TypedQuery<Versamento> typed = entityManager.createQuery(q)
                .setFirstResult(offset)
                .setMaxResults(maxResults);
        typed.setHint("jakarta.persistence.fetchgraph", summaryEntityGraph());
        return typed.getResultList();
    }

    private EntityGraph<Versamento> summaryEntityGraph() {
        EntityGraph<Versamento> graph = entityManager.createEntityGraph(Versamento.class);
        graph.addSubgraph("dominio");
        graph.addSubgraph("applicazione");
        graph.addSubgraph("tipoVersamento");
        graph.addSubgraph("unitaOperativa");
        graph.addSubgraph("tipoVersamentoDominio").addSubgraph("tipoVersamento");
        return graph;
    }
}
