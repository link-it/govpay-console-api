package it.govpay.console.unitaoperativa;

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
import it.govpay.console.entity.UnitaOperativa;
import it.govpay.console.intermediario.JsonPatchApplier;
import it.govpay.console.model.JsonPatchOperation;
import it.govpay.console.model.ListUnitaOperative200Response;
import it.govpay.console.model.Pagination;
import it.govpay.console.model.UnitaOperativaCreate;
import it.govpay.console.model.UnitaOperativaReplace;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.repository.UnitaOperativaRepository;
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
public class UnitaOperativaService {

    private static final Logger log = LoggerFactory.getLogger(UnitaOperativaService.class);

    public static final String AZIONE_AUDIT_CREATE = "UNITA_OPERATIVA_CREATE";
    public static final String AZIONE_AUDIT_MODIFICA = "UNITA_OPERATIVA_MODIFICA";

    /** Codice riservato all'unita' operativa che porta l'anagrafica del dominio. */
    private static final String COD_UO_EC = "EC";

    private final UnitaOperativaRepository repository;
    private final DominioRepository dominioRepository;
    private final UnitaOperativaMapper mapper;
    private final RepresentationValidator representationValidator;
    private final CurrentOperatorService currentOperatorService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    public UnitaOperativaService(UnitaOperativaRepository repository,
                                 DominioRepository dominioRepository,
                                 UnitaOperativaMapper mapper,
                                 RepresentationValidator representationValidator,
                                 CurrentOperatorService currentOperatorService,
                                 AuditService auditService,
                                 ObjectMapper objectMapper) {
        this.repository = repository;
        this.dominioRepository = dominioRepository;
        this.mapper = mapper;
        this.representationValidator = representationValidator;
        this.currentOperatorService = currentOperatorService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ListUnitaOperative200Response list(String idDominio, UnitaOperativaListQuery query) {
        Dominio parent = loadDominio(idDominio);
        log.debug("listUnitaOperative dominio={} filtri[ragioneSociale={}, abilitato={}], page={}, limit={}, sort={}, total={}",
                idDominio, query.ragioneSociale(), query.abilitato(),
                query.page(), query.limit(), query.sort(), query.total());

        Specification<UnitaOperativa> spec = Specification.allOf(
                Stream.of(
                        UnitaOperativaSpecifications.byDominioId(parent.getId()),
                        UnitaOperativaSpecifications.excludeEc(COD_UO_EC),
                        UnitaOperativaSpecifications.ragioneSocialePartial(query.ragioneSociale()),
                        UnitaOperativaSpecifications.abilitatoExact(query.abilitato()))
                .filter(Objects::nonNull)
                .toList());

        Sort sort;
        try {
            sort = UnitaOperativaSortParser.parse(query.sort());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }

        int page = query.page();
        int limit = query.limit();
        boolean wantTotal = Boolean.TRUE.equals(query.total());

        Pagination pagination = new Pagination(page, limit, false);
        List<UnitaOperativa> rows;
        if (wantTotal) {
            Page<UnitaOperativa> p = repository.findAll(spec, PageRequest.of(page - 1, limit, sort));
            rows = p.getContent();
            pagination.setHasNextPage(p.hasNext());
            pagination.setTotalResults(p.getTotalElements());
            pagination.setTotalPages(p.getTotalPages());
        } else {
            List<UnitaOperativa> sliced = findSlice(spec, sort, (page - 1) * limit, limit + 1);
            boolean hasNext = sliced.size() > limit;
            rows = hasNext ? sliced.subList(0, limit) : sliced;
            pagination.setHasNextPage(hasNext);
        }

        ListUnitaOperative200Response response = new ListUnitaOperative200Response();
        response.setResults(rows.stream().map(mapper::toSummary).toList());
        response.setPagination(pagination);
        return response;
    }

    @Transactional(readOnly = true)
    public ResponseEntity<it.govpay.console.model.UnitaOperativa> get(String idDominio, String idUnitaOperativa) {
        UnitaOperativa entity = load(idDominio, idUnitaOperativa);
        return ok(entity);
    }

    @Transactional
    public ResponseEntity<it.govpay.console.model.UnitaOperativa> create(String idDominio,
                                                                         UnitaOperativaCreate body,
                                                                         HttpServletRequest request) {
        Dominio parent = loadDominio(idDominio);
        if (COD_UO_EC.equals(body.getIdUnitaOperativa())) {
            throw new UnprocessableEntityException(
                    "Il codice '" + COD_UO_EC + "' e' riservato all'anagrafica del dominio e non puo' essere usato per una unita' operativa.");
        }
        if (repository.existsByDominio_IdAndCodUo(parent.getId(), body.getIdUnitaOperativa())) {
            throw new ConflictException("Esiste gia' una unita' operativa '" + body.getIdUnitaOperativa()
                    + "' per il dominio '" + idDominio + "'.");
        }

        UnitaOperativa entity = new UnitaOperativa();
        entity.setDominio(parent);
        entity.setCodUo(body.getIdUnitaOperativa());
        entity.setUoCodiceIdentificativo(body.getIdUnitaOperativa());
        entity.setAbilitato(body.getAbilitato());
        writeAnagrafica(entity, body.getRagioneSociale(), body.getIndirizzo(), body.getCivico(),
                body.getCap(), body.getLocalita(), body.getProvincia(), body.getNazione(),
                body.getEmail(), body.getPec(), body.getTel(), body.getFax(), body.getWeb(), body.getArea());
        UnitaOperativa saved = repository.save(entity);

        audit(AZIONE_AUDIT_CREATE, saved, request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(saved.getCodUo())
                .toUri();
        it.govpay.console.model.UnitaOperativa dto = mapper.toDetail(saved);
        return ResponseEntity.created(location)
                .eTag(RepresentationEtag.of(dto, objectMapper))
                .body(dto);
    }

    @Transactional
    public ResponseEntity<it.govpay.console.model.UnitaOperativa> replace(String idDominio,
                                                                          String idUnitaOperativa,
                                                                          UnitaOperativaReplace body,
                                                                          String ifMatch,
                                                                          HttpServletRequest request) {
        UnitaOperativa entity = load(idDominio, idUnitaOperativa);
        checkIfMatch(ifMatch, entity);

        entity.setAbilitato(body.getAbilitato());
        writeAnagrafica(entity, body.getRagioneSociale(), body.getIndirizzo(), body.getCivico(),
                body.getCap(), body.getLocalita(), body.getProvincia(), body.getNazione(),
                body.getEmail(), body.getPec(), body.getTel(), body.getFax(), body.getWeb(), body.getArea());
        UnitaOperativa saved = repository.save(entity);

        audit(AZIONE_AUDIT_MODIFICA, saved, request);
        return ok(saved);
    }

    @Transactional
    public ResponseEntity<it.govpay.console.model.UnitaOperativa> patch(String idDominio,
                                                                        String idUnitaOperativa,
                                                                        List<JsonPatchOperation> operations,
                                                                        String ifMatch,
                                                                        HttpServletRequest request) {
        UnitaOperativa entity = load(idDominio, idUnitaOperativa);
        checkIfMatch(ifMatch, entity);

        ObjectNode current = objectMapper.valueToTree(mapper.toDetail(entity));
        ObjectNode patched = JsonPatchApplier.apply(current, operations, objectMapper);

        it.govpay.console.model.UnitaOperativa result;
        try {
            result = objectMapper.treeToValue(patched, it.govpay.console.model.UnitaOperativa.class);
        } catch (JacksonException e) {
            throw new BadRequestException(
                    "La rappresentazione risultante dal PATCH non e' una unita' operativa valida: " + e.getOriginalMessage());
        }

        if (!idUnitaOperativa.equals(result.getIdUnitaOperativa())) {
            throw new BadRequestException("Il campo 'idUnitaOperativa' non puo' essere modificato tramite PATCH.");
        }
        representationValidator.validate(result);

        entity.setAbilitato(result.getAbilitato());
        writeAnagrafica(entity, result.getRagioneSociale(), result.getIndirizzo(), result.getCivico(),
                result.getCap(), result.getLocalita(), result.getProvincia(), result.getNazione(),
                result.getEmail(), result.getPec(), result.getTel(), result.getFax(), result.getWeb(), result.getArea());
        UnitaOperativa saved = repository.save(entity);

        audit(AZIONE_AUDIT_MODIFICA, saved, request);
        return ok(saved);
    }

    // ---- helpers ----------------------------------------------------------

    private void writeAnagrafica(UnitaOperativa e, String ragioneSociale, String indirizzo, String civico,
            String cap, String localita, String provincia, String nazione, String email, String pec,
            String tel, String fax, String web, String area) {
        e.setUoDenominazione(ragioneSociale);
        e.setUoIndirizzo(indirizzo);
        e.setUoCivico(civico);
        e.setUoCap(cap);
        e.setUoLocalita(localita);
        e.setUoProvincia(provincia);
        e.setUoNazione(nazione);
        e.setUoEmail(email);
        e.setUoPec(pec);
        e.setUoTel(tel);
        e.setUoFax(fax);
        e.setUoUrlSitoWeb(web);
        e.setUoArea(area);
    }

    private ResponseEntity<it.govpay.console.model.UnitaOperativa> ok(UnitaOperativa entity) {
        it.govpay.console.model.UnitaOperativa dto = mapper.toDetail(entity);
        return ResponseEntity.ok()
                .eTag(RepresentationEtag.of(dto, objectMapper))
                .body(dto);
    }

    private Dominio loadDominio(String idDominio) {
        return dominioRepository.findByCodDominio(idDominio)
                .orElseThrow(() -> new NotFoundException("Dominio non trovato: " + idDominio));
    }

    private UnitaOperativa load(String idDominio, String idUnitaOperativa) {
        Dominio parent = loadDominio(idDominio);
        // l'EC e' gestito dal CRUD del dominio: come sub-resource non esiste.
        if (COD_UO_EC.equals(idUnitaOperativa)) {
            throw new NotFoundException("Unita' operativa non trovata: " + idUnitaOperativa);
        }
        return repository.findByDominio_IdAndCodUo(parent.getId(), idUnitaOperativa)
                .orElseThrow(() -> new NotFoundException("Unita' operativa non trovata: " + idUnitaOperativa));
    }

    private void checkIfMatch(String ifMatch, UnitaOperativa entity) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new PreconditionRequiredException(
                    "Header 'If-Match' obbligatorio per le operazioni di modifica.");
        }
        if (!RepresentationEtag.matches(ifMatch, mapper.toDetail(entity), objectMapper)) {
            throw new IfMatchMismatchException(
                    "L'header 'If-Match' non corrisponde alla versione corrente dell'unita' operativa.");
        }
    }

    private void audit(String azione, UnitaOperativa entity, HttpServletRequest request) {
        OperatoreCorrente operatore = currentOperatorService.get();
        Map<String, Object> dettaglio = new HashMap<>();
        dettaglio.put("idDominio", entity.getDominio().getCodDominio());
        dettaglio.put("idUnitaOperativa", entity.getCodUo());
        auditService.registra(azione, entity.getId(), dettaglio, operatore, request);
    }

    private List<UnitaOperativa> findSlice(Specification<UnitaOperativa> spec, Sort sort, int offset, int maxResults) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<UnitaOperativa> q = cb.createQuery(UnitaOperativa.class);
        Root<UnitaOperativa> root = q.from(UnitaOperativa.class);
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
        TypedQuery<UnitaOperativa> typed = entityManager.createQuery(q)
                .setFirstResult(offset)
                .setMaxResults(maxResults);
        return typed.getResultList();
    }
}
