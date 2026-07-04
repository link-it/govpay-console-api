package it.govpay.console.operatore;

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
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Utenza;
import it.govpay.console.intermediario.JsonPatchApplier;
import it.govpay.console.model.JsonPatchOperation;
import it.govpay.console.model.ListOperatori200Response;
import it.govpay.console.model.OperatoreCreate;
import it.govpay.console.model.OperatoreReplace;
import it.govpay.console.model.Pagination;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.UtenzaRepository;
import it.govpay.console.security.CurrentOperatorService;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.utenza.UtenzaAssociazioniWriter;
import it.govpay.console.web.BadRequestException;
import it.govpay.console.web.ConflictException;
import it.govpay.console.web.IfMatchMismatchException;
import it.govpay.console.web.NotFoundException;
import it.govpay.console.web.PreconditionRequiredException;
import it.govpay.console.web.RepresentationEtag;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * CRUD degli operatori (utenti della console). Stessa struttura di
 * {@code ApplicazioneService} ma identificato dal {@code principal}, con un
 * {@code nome} e senza codificaAvvisi/connettore/trusted. Le associazioni
 * utenza (domini/tipiPendenza/ruoli/acl) sono delegate a
 * {@link UtenzaAssociazioniWriter}.
 */
@Service
public class OperatoreService {

    private static final Logger log = LoggerFactory.getLogger(OperatoreService.class);

    public static final String AZIONE_AUDIT_CREATE = "OPERATORE_CREATE";
    public static final String AZIONE_AUDIT_MODIFICA = "OPERATORE_MODIFICA";

    private static final int MAX_PRINCIPAL = 4000;
    private static final int MAX_NOME = 35;

    private final OperatoreRepository operatoreRepository;
    private final UtenzaRepository utenzaRepository;
    private final UtenzaAssociazioniWriter writer;
    private final OperatoreMapper mapper;
    private final CurrentOperatorService currentOperatorService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    public OperatoreService(OperatoreRepository operatoreRepository,
                            UtenzaRepository utenzaRepository,
                            UtenzaAssociazioniWriter writer,
                            OperatoreMapper mapper,
                            CurrentOperatorService currentOperatorService,
                            AuditService auditService,
                            ObjectMapper objectMapper) {
        this.operatoreRepository = operatoreRepository;
        this.utenzaRepository = utenzaRepository;
        this.writer = writer;
        this.mapper = mapper;
        this.currentOperatorService = currentOperatorService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ListOperatori200Response list(OperatoreListQuery query) {
        log.debug("listOperatori filtri[principal={}, nome={}, abilitato={}], page={}, limit={}, sort={}, total={}",
                query.principal(), query.nome(), query.abilitato(),
                query.page(), query.limit(), query.sort(), query.total());

        Specification<Operatore> spec = Specification.allOf(
                Stream.of(
                        OperatoreSpecifications.principalPartial(query.principal()),
                        OperatoreSpecifications.nomePartial(query.nome()),
                        OperatoreSpecifications.abilitatoExact(query.abilitato()))
                .filter(Objects::nonNull)
                .toList());

        Sort sort;
        try {
            sort = OperatoreSortParser.parse(query.sort());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }

        int page = query.page();
        int limit = query.limit();
        boolean wantTotal = Boolean.TRUE.equals(query.total());

        Pagination pagination = new Pagination(page, limit, false);
        List<Operatore> rows;
        if (wantTotal) {
            Page<Operatore> p = operatoreRepository.findAll(spec, PageRequest.of(page - 1, limit, sort));
            rows = p.getContent();
            pagination.setHasNextPage(p.hasNext());
            pagination.setTotalResults(p.getTotalElements());
            pagination.setTotalPages(p.getTotalPages());
        } else {
            List<Operatore> sliced = findSlice(spec, sort, (page - 1) * limit, limit + 1);
            boolean hasNext = sliced.size() > limit;
            rows = hasNext ? sliced.subList(0, limit) : sliced;
            pagination.setHasNextPage(hasNext);
        }

        ListOperatori200Response response = new ListOperatori200Response();
        response.setResults(rows.stream().map(op -> mapper.toSummary(op, utenzaOf(op))).toList());
        response.setPagination(pagination);
        return response;
    }

    @Transactional(readOnly = true)
    public ResponseEntity<it.govpay.console.model.Operatore> get(String principal) {
        return ok(load(principal));
    }

    @Transactional
    public ResponseEntity<it.govpay.console.model.Operatore> create(OperatoreCreate body,
                                                                    HttpServletRequest request) {
        String principal = validatePrincipal(body.getPrincipal());
        String nome = validateNome(body.getNome());
        if (utenzaRepository.existsByPrincipalOriginale(principal)) {
            throw new ConflictException("Il principal '" + principal + "' e' gia' associato a un'altra utenza.");
        }

        UtenzaAssociazioniWriter.DominiResolution dom = writer.resolveDomini(body.getDomini());
        UtenzaAssociazioniWriter.TipiResolution tipi = writer.resolveTipiPendenza(body.getTipiPendenza());
        String ruoliCsv = writer.validateRuoliToCsv(body.getRuoli());
        boolean abilitato = body.getAbilitato() == null || Boolean.TRUE.equals(body.getAbilitato());

        Utenza utenza = new Utenza();
        utenza.setPrincipal(principal);
        utenza.setPrincipalOriginale(principal);
        utenza.setAbilitato(abilitato);
        utenza.setPassword(null);
        utenza.setRuoli(ruoliCsv);
        utenza.setAutorizzazioneDominiStar(dom.star());
        utenza.setAutorizzazioneTipiVersStar(tipi.star());
        Utenza savedUtenza = utenzaRepository.save(utenza);

        Operatore op = new Operatore();
        op.setNome(nome);
        op.setIdUtenza(savedUtenza.getId());
        Operatore savedOp = operatoreRepository.save(op);

        writer.writeChildren(savedUtenza.getId(), dom, tipi, writer.buildAclEntities(body.getAcl(), savedUtenza.getId()));

        audit(AZIONE_AUDIT_CREATE, savedOp, principal, request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{principal}")
                .buildAndExpand(principal)
                .toUri();
        it.govpay.console.model.Operatore dto = mapper.toDetail(savedOp, savedUtenza);
        return ResponseEntity.created(location)
                .eTag(RepresentationEtag.of(dto, objectMapper))
                .body(dto);
    }

    @Transactional
    public ResponseEntity<it.govpay.console.model.Operatore> replace(String principal, OperatoreReplace body,
                                                                     String ifMatch, HttpServletRequest request) {
        Operatore op = load(principal);
        checkIfMatch(ifMatch, op);
        return doReplace(op, body, request);
    }

    @Transactional
    public ResponseEntity<it.govpay.console.model.Operatore> patch(String principal, List<JsonPatchOperation> operations,
                                                                   String ifMatch, HttpServletRequest request) {
        Operatore op = load(principal);
        checkIfMatch(ifMatch, op);

        ObjectNode current = objectMapper.valueToTree(mapper.toDetail(op, utenzaOf(op)));
        ObjectNode patched = JsonPatchApplier.apply(current, operations, objectMapper);

        String patchedPrincipal = text(patched, "principal");
        if (!principal.equals(patchedPrincipal)) {
            throw new BadRequestException("Il campo 'principal' non puo' essere modificato tramite PATCH.");
        }
        patched.remove("principal");

        OperatoreReplace body;
        try {
            body = objectMapper.treeToValue(patched, OperatoreReplace.class);
        } catch (RuntimeException e) {
            throw new BadRequestException("La rappresentazione risultante dal PATCH non e' valida: " + e.getMessage());
        }
        return doReplace(op, body, request);
    }

    private ResponseEntity<it.govpay.console.model.Operatore> doReplace(Operatore op, OperatoreReplace body,
                                                                        HttpServletRequest request) {
        String nome = validateNome(body.getNome());
        Boolean abilitato = body.getAbilitato();
        if (abilitato == null) {
            throw new BadRequestException("Il campo 'abilitato' e' obbligatorio.");
        }

        Utenza utenza = utenzaOf(op);
        UtenzaAssociazioniWriter.DominiResolution dom = writer.resolveDomini(body.getDomini());
        UtenzaAssociazioniWriter.TipiResolution tipi = writer.resolveTipiPendenza(body.getTipiPendenza());
        String ruoliCsv = writer.validateRuoliToCsv(body.getRuoli());

        utenza.setAbilitato(abilitato);
        utenza.setRuoli(ruoliCsv);
        utenza.setAutorizzazioneDominiStar(dom.star());
        utenza.setAutorizzazioneTipiVersStar(tipi.star());
        utenzaRepository.save(utenza);

        op.setNome(nome);
        operatoreRepository.save(op);

        writer.deleteChildrenAndFlush(utenza.getId());
        writer.writeChildren(utenza.getId(), dom, tipi, writer.buildAclEntities(body.getAcl(), utenza.getId()));

        audit(AZIONE_AUDIT_MODIFICA, op, utenza.getPrincipalOriginale(), request);
        return ok(op);
    }

    private static String validatePrincipal(String principal) {
        if (principal == null || principal.isBlank()) {
            throw new BadRequestException("Il campo 'principal' e' obbligatorio.");
        }
        if (principal.length() > MAX_PRINCIPAL) {
            throw new UnprocessableEntityException(
                    "Il campo 'principal' supera la lunghezza massima di " + MAX_PRINCIPAL + " caratteri.");
        }
        return principal;
    }

    private static String validateNome(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new BadRequestException("Il campo 'nome' e' obbligatorio.");
        }
        if (nome.length() > MAX_NOME) {
            throw new UnprocessableEntityException(
                    "Il campo 'nome' supera la lunghezza massima di " + MAX_NOME + " caratteri.");
        }
        return nome;
    }

    private ResponseEntity<it.govpay.console.model.Operatore> ok(Operatore op) {
        it.govpay.console.model.Operatore dto = mapper.toDetail(op, utenzaOf(op));
        return ResponseEntity.ok()
                .eTag(RepresentationEtag.of(dto, objectMapper))
                .body(dto);
    }

    private Operatore load(String principal) {
        return operatoreRepository.findByUtenza_PrincipalOriginale(principal)
                .orElseThrow(() -> new NotFoundException("Operatore non trovato: " + principal));
    }

    /**
     * Carica la utenza dell'operatore via repository (non tramite la relazione
     * JPA read-only, che non e' popolata quando l'entita' proviene dal
     * persistence context avendo valorizzato solo la FK {@code idUtenza}).
     */
    private Utenza utenzaOf(Operatore op) {
        return utenzaRepository.findById(op.getIdUtenza())
                .orElseThrow(() -> new IllegalStateException(
                        "Utenza non trovata per operatore id=" + op.getId()));
    }

    private void checkIfMatch(String ifMatch, Operatore op) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new PreconditionRequiredException(
                    "Header 'If-Match' obbligatorio per le operazioni di modifica.");
        }
        if (!RepresentationEtag.matches(ifMatch, mapper.toDetail(op, utenzaOf(op)), objectMapper)) {
            throw new IfMatchMismatchException(
                    "L'header 'If-Match' non corrisponde alla versione corrente dell'operatore.");
        }
    }

    private void audit(String azione, Operatore op, String principal, HttpServletRequest request) {
        OperatoreCorrente operatore = currentOperatorService.get();
        Map<String, Object> dettaglio = new HashMap<>();
        dettaglio.put("principal", principal);
        auditService.registra(azione, op.getId(), dettaglio, operatore, request);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    private List<Operatore> findSlice(Specification<Operatore> spec, Sort sort, int offset, int maxResults) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Operatore> q = cb.createQuery(Operatore.class);
        Root<Operatore> root = q.from(Operatore.class);
        Predicate predicate = spec.toPredicate(root, q, cb);
        if (predicate != null) {
            q.where(predicate);
        }
        if (sort != null && sort.isSorted()) {
            List<Order> orders = new ArrayList<>();
            for (Sort.Order o : sort) {
                Path<Object> path = resolvePath(root, o.getProperty());
                orders.add(o.isAscending() ? cb.asc(path) : cb.desc(path));
            }
            q.orderBy(orders);
        }
        TypedQuery<Operatore> typed = entityManager.createQuery(q)
                .setFirstResult(offset)
                .setMaxResults(maxResults);
        return typed.getResultList();
    }

    /** Risolve un path eventualmente annidato (es. {@code utenza.principalOriginale}). */
    private static Path<Object> resolvePath(Root<Operatore> root, String property) {
        Path<Object> path = null;
        for (String part : property.split("\\.")) {
            path = path == null ? root.get(part) : path.get(part);
        }
        return path;
    }
}
