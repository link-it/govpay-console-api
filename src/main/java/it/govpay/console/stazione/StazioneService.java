package it.govpay.console.stazione;

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
import it.govpay.console.entity.Intermediario;
import it.govpay.console.entity.Stazione;
import it.govpay.console.intermediario.JsonPatchApplier;
import it.govpay.console.model.JsonPatchOperation;
import it.govpay.console.model.ListStazioni200Response;
import it.govpay.console.model.Pagination;
import it.govpay.console.model.StazioneCreate;
import it.govpay.console.model.StazioneReplace;
import it.govpay.console.model.VersioneStazione;
import it.govpay.console.entity.Dominio;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.repository.IntermediarioRepository;
import it.govpay.console.repository.StazioneRepository;
import it.govpay.console.security.CurrentOperatorService;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.web.BadRequestException;
import it.govpay.console.web.ConflictException;
import it.govpay.console.web.IfMatchMismatchException;
import it.govpay.console.web.NotFoundException;
import it.govpay.console.web.PreconditionRequiredException;
import it.govpay.console.web.RepresentationEtag;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class StazioneService {

    private static final Logger log = LoggerFactory.getLogger(StazioneService.class);

    public static final String AZIONE_AUDIT_CREATE = "STAZIONE_CREATE";
    public static final String AZIONE_AUDIT_MODIFICA = "STAZIONE_MODIFICA";

    private static final String DEFAULT_PASSWORD = "";

    private final StazioneRepository repository;
    private final IntermediarioRepository intermediarioRepository;
    private final DominioRepository dominioRepository;
    private final StazioneMapper mapper;
    private final CurrentOperatorService currentOperatorService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    public StazioneService(StazioneRepository repository,
                           IntermediarioRepository intermediarioRepository,
                           DominioRepository dominioRepository,
                           StazioneMapper mapper,
                           CurrentOperatorService currentOperatorService,
                           AuditService auditService,
                           ObjectMapper objectMapper) {
        this.repository = repository;
        this.intermediarioRepository = intermediarioRepository;
        this.dominioRepository = dominioRepository;
        this.mapper = mapper;
        this.currentOperatorService = currentOperatorService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ListStazioni200Response list(String idIntermediario, StazioneListQuery query) {
        Intermediario parent = loadIntermediario(idIntermediario);
        log.debug("listStazioni intermediario={} filtri[codStazione={}, abilitato={}], page={}, limit={}, sort={}, total={}",
                idIntermediario, query.codStazione(), query.abilitato(),
                query.page(), query.limit(), query.sort(), query.total());

        Specification<Stazione> spec = Specification.allOf(
                Stream.of(
                        StazioneSpecifications.byIntermediarioId(parent.getId()),
                        StazioneSpecifications.codStazionePartial(query.codStazione()),
                        StazioneSpecifications.abilitatoExact(query.abilitato()))
                .filter(Objects::nonNull)
                .toList());

        Sort sort;
        try {
            sort = StazioneSortParser.parse(query.sort());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }

        int page = query.page();
        int limit = query.limit();
        boolean wantTotal = Boolean.TRUE.equals(query.total());

        Pagination pagination = new Pagination(page, limit, false);
        List<Stazione> rows;
        if (wantTotal) {
            Page<Stazione> p = repository.findAll(spec, PageRequest.of(page - 1, limit, sort));
            rows = p.getContent();
            pagination.setHasNextPage(p.hasNext());
            pagination.setTotalResults(p.getTotalElements());
            pagination.setTotalPages(p.getTotalPages());
        } else {
            List<Stazione> sliced = findSlice(spec, sort, (page - 1) * limit, limit + 1);
            boolean hasNext = sliced.size() > limit;
            rows = hasNext ? sliced.subList(0, limit) : sliced;
            pagination.setHasNextPage(hasNext);
        }

        ListStazioni200Response response = new ListStazioni200Response();
        response.setResults(rows.stream().map(mapper::toSummary).toList());
        response.setPagination(pagination);
        return response;
    }

    @Transactional(readOnly = true)
    public ResponseEntity<it.govpay.console.model.Stazione> get(String idIntermediario, String idStazione) {
        Stazione entity = load(idIntermediario, idStazione);
        return ok(entity);
    }

    @Transactional
    public ResponseEntity<it.govpay.console.model.Stazione> create(String idIntermediario,
                                                                   StazioneCreate body,
                                                                   HttpServletRequest request) {
        Intermediario parent = loadIntermediario(idIntermediario);
        int applicationCode = StazioneIdFormat.applicationCode(body.getIdStazione(), idIntermediario);
        if (repository.existsByCodStazione(body.getIdStazione())) {
            throw new ConflictException(
                    "Esiste gia' una stazione con idStazione '" + body.getIdStazione() + "'.");
        }
        Stazione entity = new Stazione();
        entity.setCodStazione(body.getIdStazione());
        entity.setApplicationCode(applicationCode);
        entity.setVersione(body.getVersione().getValue());
        entity.setAbilitato(body.getAbilitato());
        entity.setPassword(DEFAULT_PASSWORD);
        entity.setIntermediario(parent);
        Stazione saved = repository.save(entity);

        audit(AZIONE_AUDIT_CREATE, saved, request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(saved.getCodStazione())
                .toUri();
        it.govpay.console.model.Stazione dto = toDetailDto(saved);
        return ResponseEntity.created(location)
                .eTag(RepresentationEtag.of(dto, objectMapper))
                .body(dto);
    }

    @Transactional
    public ResponseEntity<it.govpay.console.model.Stazione> replace(String idIntermediario,
                                                                    String idStazione,
                                                                    StazioneReplace body,
                                                                    String ifMatch,
                                                                    HttpServletRequest request) {
        Stazione entity = load(idIntermediario, idStazione);
        checkIfMatch(ifMatch, entity);

        entity.setVersione(body.getVersione().getValue());
        entity.setAbilitato(body.getAbilitato());
        Stazione saved = repository.save(entity);

        audit(AZIONE_AUDIT_MODIFICA, saved, request);
        return ok(saved);
    }

    @Transactional
    public ResponseEntity<it.govpay.console.model.Stazione> patch(String idIntermediario,
                                                                  String idStazione,
                                                                  List<JsonPatchOperation> operations,
                                                                  String ifMatch,
                                                                  HttpServletRequest request) {
        Stazione entity = load(idIntermediario, idStazione);
        checkIfMatch(ifMatch, entity);

        ObjectNode current = objectMapper.valueToTree(toDetailDto(entity));
        ObjectNode patched = JsonPatchApplier.apply(current, operations, objectMapper);

        if (!idStazione.equals(text(patched, "idStazione"))) {
            throw new BadRequestException("Il campo 'idStazione' non puo' essere modificato tramite PATCH.");
        }
        if (!Objects.equals(patched.get("domini"), current.get("domini"))) {
            throw new BadRequestException("Il campo 'domini' e' di sola lettura e non puo' essere modificato.");
        }

        VersioneStazione versione = requireVersione(patched);
        Boolean abilitato = requireBoolean(patched, "abilitato");

        entity.setVersione(versione.getValue());
        entity.setAbilitato(abilitato);
        Stazione saved = repository.save(entity);

        audit(AZIONE_AUDIT_MODIFICA, saved, request);
        return ok(saved);
    }

    private ResponseEntity<it.govpay.console.model.Stazione> ok(Stazione entity) {
        it.govpay.console.model.Stazione dto = toDetailDto(entity);
        return ResponseEntity.ok()
                .eTag(RepresentationEtag.of(dto, objectMapper))
                .body(dto);
    }

    private it.govpay.console.model.Stazione toDetailDto(Stazione entity) {
        List<Dominio> domini = dominioRepository.findByStazione_IdOrderByCodDominioAsc(entity.getId());
        return mapper.toDetail(entity, domini);
    }

    private Intermediario loadIntermediario(String idIntermediario) {
        return intermediarioRepository.findByCodIntermediario(idIntermediario)
                .orElseThrow(() -> new NotFoundException(
                        "Intermediario non trovato: " + idIntermediario));
    }

    private Stazione load(String idIntermediario, String idStazione) {
        Intermediario parent = loadIntermediario(idIntermediario);
        Stazione stazione = repository.findByCodStazione(idStazione)
                .orElseThrow(() -> new NotFoundException("Stazione non trovata: " + idStazione));
        if (stazione.getIntermediario() == null
                || !parent.getId().equals(stazione.getIntermediario().getId())) {
            throw new NotFoundException("Stazione non trovata: " + idStazione);
        }
        return stazione;
    }

    private void checkIfMatch(String ifMatch, Stazione entity) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new PreconditionRequiredException(
                    "Header 'If-Match' obbligatorio per le operazioni di modifica.");
        }
        if (!RepresentationEtag.matches(ifMatch, toDetailDto(entity), objectMapper)) {
            throw new IfMatchMismatchException(
                    "L'header 'If-Match' non corrisponde alla versione corrente della stazione.");
        }
    }

    private void audit(String azione, Stazione entity, HttpServletRequest request) {
        OperatoreCorrente operatore = currentOperatorService.get();
        Map<String, Object> dettaglio = new HashMap<>();
        dettaglio.put("idStazione", entity.getCodStazione());
        auditService.registra(azione, entity.getId(), dettaglio, operatore, request);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    private static VersioneStazione requireVersione(ObjectNode node) {
        String value = text(node, "versione");
        if (value == null || value.isBlank()) {
            throw new BadRequestException(
                    "La rappresentazione risultante dal PATCH ha il campo 'versione' mancante.");
        }
        try {
            return VersioneStazione.fromValue(value);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Valore di 'versione' non valido: '" + value + "'.");
        }
    }

    private static Boolean requireBoolean(ObjectNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isBoolean()) {
            throw new BadRequestException(
                    "La rappresentazione risultante dal PATCH ha il campo '" + field + "' mancante o non booleano.");
        }
        return value.asBoolean();
    }

    private List<Stazione> findSlice(Specification<Stazione> spec, Sort sort, int offset, int maxResults) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Stazione> q = cb.createQuery(Stazione.class);
        Root<Stazione> root = q.from(Stazione.class);
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
        TypedQuery<Stazione> typed = entityManager.createQuery(q)
                .setFirstResult(offset)
                .setMaxResults(maxResults);
        return typed.getResultList();
    }
}
