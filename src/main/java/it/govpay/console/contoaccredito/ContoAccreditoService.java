package it.govpay.console.contoaccredito;

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
import it.govpay.console.entity.IbanAccredito;
import it.govpay.console.intermediario.JsonPatchApplier;
import it.govpay.console.model.ContoAccreditoCreate;
import it.govpay.console.model.ContoAccreditoReplace;
import it.govpay.console.model.JsonPatchOperation;
import it.govpay.console.model.ListContiAccredito200Response;
import it.govpay.console.model.Pagination;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.repository.IbanAccreditoRepository;
import it.govpay.console.security.CurrentOperatorService;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.web.BadRequestException;
import it.govpay.console.web.ConflictException;
import it.govpay.console.web.IfMatchMismatchException;
import it.govpay.console.web.NotFoundException;
import it.govpay.console.web.PreconditionRequiredException;
import it.govpay.console.web.RepresentationEtag;
import it.govpay.console.web.RepresentationValidator;
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
public class ContoAccreditoService {

    private static final Logger log = LoggerFactory.getLogger(ContoAccreditoService.class);

    public static final String AZIONE_AUDIT_CREATE = "CONTO_ACCREDITO_CREATE";
    public static final String AZIONE_AUDIT_MODIFICA = "CONTO_ACCREDITO_MODIFICA";

    private final IbanAccreditoRepository repository;
    private final DominioRepository dominioRepository;
    private final ContoAccreditoMapper mapper;
    private final RepresentationValidator representationValidator;
    private final CurrentOperatorService currentOperatorService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    public ContoAccreditoService(IbanAccreditoRepository repository,
                                 DominioRepository dominioRepository,
                                 ContoAccreditoMapper mapper,
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
    public ListContiAccredito200Response list(String idDominio, ContoAccreditoListQuery query) {
        Dominio parent = loadDominio(idDominio);
        log.debug("listContiAccredito dominio={} filtri[descrizione={}, abilitato={}], page={}, limit={}, sort={}, total={}",
                idDominio, query.descrizione(), query.abilitato(),
                query.page(), query.limit(), query.sort(), query.total());

        Specification<IbanAccredito> spec = Specification.allOf(
                Stream.of(
                        ContoAccreditoSpecifications.byDominioId(parent.getId()),
                        ContoAccreditoSpecifications.descrizionePartial(query.descrizione()),
                        ContoAccreditoSpecifications.abilitatoExact(query.abilitato()))
                .filter(Objects::nonNull)
                .toList());

        Sort sort;
        try {
            sort = ContoAccreditoSortParser.parse(query.sort());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }

        int page = query.page();
        int limit = query.limit();
        boolean wantTotal = Boolean.TRUE.equals(query.total());

        Pagination pagination = new Pagination(page, limit, false);
        List<IbanAccredito> rows;
        if (wantTotal) {
            Page<IbanAccredito> p = repository.findAll(spec, PageRequest.of(page - 1, limit, sort));
            rows = p.getContent();
            pagination.setHasNextPage(p.hasNext());
            pagination.setTotalResults(p.getTotalElements());
            pagination.setTotalPages(p.getTotalPages());
        } else {
            List<IbanAccredito> sliced = findSlice(spec, sort, (page - 1) * limit, limit + 1);
            boolean hasNext = sliced.size() > limit;
            rows = hasNext ? sliced.subList(0, limit) : sliced;
            pagination.setHasNextPage(hasNext);
        }

        ListContiAccredito200Response response = new ListContiAccredito200Response();
        response.setResults(rows.stream().map(mapper::toSummary).toList());
        response.setPagination(pagination);
        return response;
    }

    @Transactional(readOnly = true)
    public ResponseEntity<it.govpay.console.model.ContoAccredito> get(String idDominio, String ibanAccredito) {
        IbanAccredito entity = load(idDominio, ibanAccredito);
        return ok(entity);
    }

    @Transactional
    public ResponseEntity<it.govpay.console.model.ContoAccredito> create(String idDominio,
                                                                         ContoAccreditoCreate body,
                                                                         HttpServletRequest request) {
        Dominio parent = loadDominio(idDominio);
        if (repository.existsByDominio_IdAndCodIban(parent.getId(), body.getIbanAccredito())) {
            throw new ConflictException("Esiste gia' un conto di accredito '" + body.getIbanAccredito()
                    + "' per il dominio '" + idDominio + "'.");
        }

        IbanAccredito entity = new IbanAccredito();
        entity.setDominio(parent);
        entity.setCodIban(body.getIbanAccredito());
        entity.setPostale(body.getPostale());
        entity.setAbilitato(body.getAbilitato());
        writeAnagrafica(entity, body.getBic(), body.getDescrizione(), body.getIntestatario(),
                body.getAutStampaPosteItaliane());
        IbanAccredito saved = repository.save(entity);

        audit(AZIONE_AUDIT_CREATE, saved, request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(saved.getCodIban())
                .toUri();
        it.govpay.console.model.ContoAccredito dto = mapper.toDetail(saved);
        return ResponseEntity.created(location)
                .eTag(RepresentationEtag.of(dto, objectMapper))
                .body(dto);
    }

    @Transactional
    public ResponseEntity<it.govpay.console.model.ContoAccredito> replace(String idDominio,
                                                                          String ibanAccredito,
                                                                          ContoAccreditoReplace body,
                                                                          String ifMatch,
                                                                          HttpServletRequest request) {
        IbanAccredito entity = load(idDominio, ibanAccredito);
        checkIfMatch(ifMatch, entity);

        entity.setPostale(body.getPostale());
        entity.setAbilitato(body.getAbilitato());
        writeAnagrafica(entity, body.getBic(), body.getDescrizione(), body.getIntestatario(),
                body.getAutStampaPosteItaliane());
        IbanAccredito saved = repository.save(entity);

        audit(AZIONE_AUDIT_MODIFICA, saved, request);
        return ok(saved);
    }

    @Transactional
    public ResponseEntity<it.govpay.console.model.ContoAccredito> patch(String idDominio,
                                                                        String ibanAccredito,
                                                                        List<JsonPatchOperation> operations,
                                                                        String ifMatch,
                                                                        HttpServletRequest request) {
        IbanAccredito entity = load(idDominio, ibanAccredito);
        checkIfMatch(ifMatch, entity);

        ObjectNode current = objectMapper.valueToTree(mapper.toDetail(entity));
        ObjectNode patched = JsonPatchApplier.apply(current, operations, objectMapper);

        it.govpay.console.model.ContoAccredito result;
        try {
            result = objectMapper.treeToValue(patched, it.govpay.console.model.ContoAccredito.class);
        } catch (JacksonException e) {
            throw new BadRequestException(
                    "La rappresentazione risultante dal PATCH non e' un conto di accredito valido: " + e.getOriginalMessage());
        }

        if (!ibanAccredito.equals(result.getIbanAccredito())) {
            throw new BadRequestException("Il campo 'ibanAccredito' non puo' essere modificato tramite PATCH.");
        }
        if (result.getPostale() == null) {
            throw new BadRequestException("La rappresentazione risultante dal PATCH ha il campo 'postale' mancante.");
        }
        if (result.getAbilitato() == null) {
            throw new BadRequestException("La rappresentazione risultante dal PATCH ha il campo 'abilitato' mancante.");
        }
        representationValidator.validate(result);

        entity.setPostale(result.getPostale());
        entity.setAbilitato(result.getAbilitato());
        writeAnagrafica(entity, result.getBic(), result.getDescrizione(), result.getIntestatario(),
                result.getAutStampaPosteItaliane());
        IbanAccredito saved = repository.save(entity);

        audit(AZIONE_AUDIT_MODIFICA, saved, request);
        return ok(saved);
    }

    // ---- helpers ----------------------------------------------------------

    private void writeAnagrafica(IbanAccredito e, String bic, String descrizione, String intestatario,
            String autStampaPoste) {
        e.setBicAccredito(bic);
        e.setDescrizione(descrizione);
        e.setIntestatario(intestatario);
        e.setAutStampaPoste(autStampaPoste);
    }

    private ResponseEntity<it.govpay.console.model.ContoAccredito> ok(IbanAccredito entity) {
        it.govpay.console.model.ContoAccredito dto = mapper.toDetail(entity);
        return ResponseEntity.ok()
                .eTag(RepresentationEtag.of(dto, objectMapper))
                .body(dto);
    }

    private Dominio loadDominio(String idDominio) {
        return dominioRepository.findByCodDominio(idDominio)
                .orElseThrow(() -> new NotFoundException("Dominio non trovato: " + idDominio));
    }

    private IbanAccredito load(String idDominio, String ibanAccredito) {
        Dominio parent = loadDominio(idDominio);
        return repository.findByDominio_IdAndCodIban(parent.getId(), ibanAccredito)
                .orElseThrow(() -> new NotFoundException("Conto di accredito non trovato: " + ibanAccredito));
    }

    private void checkIfMatch(String ifMatch, IbanAccredito entity) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new PreconditionRequiredException(
                    "Header 'If-Match' obbligatorio per le operazioni di modifica.");
        }
        if (!RepresentationEtag.matches(ifMatch, mapper.toDetail(entity), objectMapper)) {
            throw new IfMatchMismatchException(
                    "L'header 'If-Match' non corrisponde alla versione corrente del conto di accredito.");
        }
    }

    private void audit(String azione, IbanAccredito entity, HttpServletRequest request) {
        OperatoreCorrente operatore = currentOperatorService.get();
        Map<String, Object> dettaglio = new HashMap<>();
        dettaglio.put("idDominio", entity.getDominio().getCodDominio());
        dettaglio.put("ibanAccredito", entity.getCodIban());
        auditService.registra(azione, entity.getId(), dettaglio, operatore, request);
    }

    private List<IbanAccredito> findSlice(Specification<IbanAccredito> spec, Sort sort, int offset, int maxResults) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<IbanAccredito> q = cb.createQuery(IbanAccredito.class);
        Root<IbanAccredito> root = q.from(IbanAccredito.class);
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
        TypedQuery<IbanAccredito> typed = entityManager.createQuery(q)
                .setFirstResult(offset)
                .setMaxResults(maxResults);
        return typed.getResultList();
    }
}
