package it.govpay.console.applicazione;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import it.govpay.console.audit.AuditService;
import it.govpay.console.entity.Applicazione;
import it.govpay.console.entity.Utenza;
import it.govpay.console.intermediario.JsonPatchApplier;
import it.govpay.console.model.AclServizio;
import it.govpay.console.model.ApplicazioneCreate;
import it.govpay.console.model.ApplicazioneReplace;
import it.govpay.console.model.CodificaAvvisi;
import it.govpay.console.model.JsonPatchOperation;
import it.govpay.console.model.ListApplicazioni200Response;
import it.govpay.console.model.Pagination;
import it.govpay.console.model.RichiestaCambioPassword;
import it.govpay.console.model.TipoPendenzaRef;
import it.govpay.console.repository.ApplicazioneRepository;
import it.govpay.console.repository.UtenzaRepository;
import it.govpay.console.security.AclAuthorizer;
import it.govpay.console.security.CurrentOperatorService;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.utenza.PasswordPolicy;
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

@Service
public class ApplicazioneService {

    private static final Logger log = LoggerFactory.getLogger(ApplicazioneService.class);

    public static final String AZIONE_AUDIT_CREATE = "APPLICAZIONE_CREATE";
    public static final String AZIONE_AUDIT_MODIFICA = "APPLICAZIONE_MODIFICA";
    public static final String AZIONE_AUDIT_CAMBIO_PASSWORD = "APPLICAZIONE_CAMBIO_PASSWORD";

    /** Firma ricevuta "nessuna" (V1 {@code FirmaRichiesta.NESSUNA}). */
    private static final String FIRMA_RICEVUTA_NESSUNA = "0";
    private static final int MAX_PRINCIPAL = 4000;
    private static final Pattern CODIFICA_IUV_PATTERN = Pattern.compile("[0-9]{1,3}");

    private final ApplicazioneRepository applicazioneRepository;
    private final UtenzaRepository utenzaRepository;
    private final UtenzaAssociazioniWriter writer;
    private final ApplicazioneMapper mapper;
    private final CurrentOperatorService currentOperatorService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final AclAuthorizer aclAuthorizer;
    private final PasswordEncoder passwordEncoder;

    @PersistenceContext
    private EntityManager entityManager;

    public ApplicazioneService(ApplicazioneRepository applicazioneRepository,
                               UtenzaRepository utenzaRepository,
                               UtenzaAssociazioniWriter writer,
                               ApplicazioneMapper mapper,
                               CurrentOperatorService currentOperatorService,
                               AuditService auditService,
                               ObjectMapper objectMapper,
                               AclAuthorizer aclAuthorizer,
                               PasswordEncoder passwordEncoder) {
        this.applicazioneRepository = applicazioneRepository;
        this.utenzaRepository = utenzaRepository;
        this.writer = writer;
        this.mapper = mapper;
        this.currentOperatorService = currentOperatorService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.aclAuthorizer = aclAuthorizer;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public ListApplicazioni200Response list(ApplicazioneListQuery query) {
        log.debug("listApplicazioni filtri[idA2A={}, principal={}, abilitato={}], page={}, limit={}, sort={}, total={}",
                query.idA2A(), query.principal(), query.abilitato(),
                query.page(), query.limit(), query.sort(), query.total());

        Specification<Applicazione> spec = Specification.allOf(
                Stream.of(
                        ApplicazioneSpecifications.idA2APartial(query.idA2A()),
                        ApplicazioneSpecifications.principalPartial(query.principal()),
                        ApplicazioneSpecifications.abilitatoExact(query.abilitato()))
                .filter(Objects::nonNull)
                .toList());

        Sort sort;
        try {
            sort = ApplicazioneSortParser.parse(query.sort());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }

        int page = query.page();
        int limit = query.limit();
        boolean wantTotal = Boolean.TRUE.equals(query.total());

        Pagination pagination = new Pagination(page, limit, false);
        List<Applicazione> rows;
        if (wantTotal) {
            Page<Applicazione> p = applicazioneRepository.findAll(spec, PageRequest.of(page - 1, limit, sort));
            rows = p.getContent();
            pagination.setHasNextPage(p.hasNext());
            pagination.setTotalResults(p.getTotalElements());
            pagination.setTotalPages(p.getTotalPages());
        } else {
            List<Applicazione> sliced = findSlice(spec, sort, (page - 1) * limit, limit + 1);
            boolean hasNext = sliced.size() > limit;
            rows = hasNext ? sliced.subList(0, limit) : sliced;
            pagination.setHasNextPage(hasNext);
        }

        ListApplicazioni200Response response = new ListApplicazioni200Response();
        response.setResults(rows.stream().map(mapper::toSummary).toList());
        response.setPagination(pagination);
        return response;
    }

    @Transactional(readOnly = true)
    public ResponseEntity<it.govpay.console.model.Applicazione> get(String idA2A) {
        return ok(load(idA2A));
    }

    @Transactional
    public ResponseEntity<it.govpay.console.model.Applicazione> create(ApplicazioneCreate body,
                                                                       HttpServletRequest request) {
        String idA2A = body.getIdA2A();
        if (applicazioneRepository.existsByCodApplicazione(idA2A)) {
            throw new ConflictException("Esiste gia' un'applicazione con idA2A '" + idA2A + "'.");
        }
        String principal = validatePrincipal(body.getPrincipal());
        if (utenzaRepository.existsByPrincipalOriginale(principal)) {
            throw new ConflictException("Il principal '" + principal + "' e' gia' associato a un'altra utenza.");
        }

        TrustedSplit split = splitAutodeterminazione(body.getTipiPendenza());
        UtenzaAssociazioniWriter.DominiResolution dom = writer.resolveDomini(body.getDomini());
        UtenzaAssociazioniWriter.TipiResolution tipi = writer.resolveTipiPendenza(split.tipi());
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

        Applicazione app = new Applicazione();
        app.setCodApplicazione(idA2A);
        app.setUtenza(savedUtenza);
        app.setTrusted(split.trusted());
        app.setFirmaRicevuta(FIRMA_RICEVUTA_NESSUNA);
        applyCodificaAvvisi(app, body.getCodificaAvvisi());
        app.setCodConnettoreIntegrazione(null);
        Applicazione savedApp = applicazioneRepository.save(app);

        writer.writeChildren(savedUtenza.getId(), dom, tipi, writer.buildAclEntities(body.getAcl(), savedUtenza.getId()));

        audit(AZIONE_AUDIT_CREATE, savedApp, request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(savedApp.getCodApplicazione())
                .toUri();
        it.govpay.console.model.Applicazione dto = mapper.toDetail(savedApp);
        return ResponseEntity.created(location)
                .eTag(RepresentationEtag.of(dto, objectMapper))
                .body(dto);
    }

    @Transactional
    public ResponseEntity<it.govpay.console.model.Applicazione> replace(String idA2A,
                                                                        ApplicazioneReplace body,
                                                                        String ifMatch,
                                                                        HttpServletRequest request) {
        Applicazione app = load(idA2A);
        checkIfMatch(ifMatch, app);
        return doReplace(app, body, request);
    }

    @Transactional
    public ResponseEntity<it.govpay.console.model.Applicazione> patch(String idA2A,
                                                                      List<JsonPatchOperation> operations,
                                                                      String ifMatch,
                                                                      HttpServletRequest request) {
        Applicazione app = load(idA2A);
        checkIfMatch(ifMatch, app);

        ObjectNode current = objectMapper.valueToTree(mapper.toDetail(app));
        ObjectNode patched = JsonPatchApplier.apply(current, operations, objectMapper);

        String patchedId = text(patched, "idA2A");
        if (!idA2A.equals(patchedId)) {
            throw new BadRequestException("Il campo 'idA2A' non puo' essere modificato tramite PATCH.");
        }
        patched.remove("idA2A");
        patched.remove("_links");

        ApplicazioneReplace body;
        try {
            body = objectMapper.treeToValue(patched, ApplicazioneReplace.class);
        } catch (RuntimeException e) {
            throw new BadRequestException("La rappresentazione risultante dal PATCH non e' valida: " + e.getMessage());
        }
        return doReplace(app, body, request);
    }

    private ResponseEntity<it.govpay.console.model.Applicazione> doReplace(Applicazione app,
                                                                           ApplicazioneReplace body,
                                                                           HttpServletRequest request) {
        String principal = validatePrincipal(body.getPrincipal());
        Boolean abilitato = body.getAbilitato();
        if (abilitato == null) {
            throw new BadRequestException("Il campo 'abilitato' e' obbligatorio.");
        }

        Utenza utenza = app.getUtenza();
        if (!principal.equals(utenza.getPrincipalOriginale()) && utenzaRepository.existsByPrincipalOriginale(principal)) {
            throw new ConflictException("Il principal '" + principal + "' e' gia' associato a un'altra utenza.");
        }

        TrustedSplit split = splitAutodeterminazione(body.getTipiPendenza());
        UtenzaAssociazioniWriter.DominiResolution dom = writer.resolveDomini(body.getDomini());
        UtenzaAssociazioniWriter.TipiResolution tipi = writer.resolveTipiPendenza(split.tipi());
        String ruoliCsv = writer.validateRuoliToCsv(body.getRuoli());

        utenza.setPrincipal(principal);
        utenza.setPrincipalOriginale(principal);
        utenza.setAbilitato(abilitato);
        utenza.setRuoli(ruoliCsv);
        utenza.setAutorizzazioneDominiStar(dom.star());
        utenza.setAutorizzazioneTipiVersStar(tipi.star());
        utenzaRepository.save(utenza);

        app.setTrusted(split.trusted());
        applyCodificaAvvisi(app, body.getCodificaAvvisi());
        applicazioneRepository.save(app);

        writer.deleteChildrenAndFlush(utenza.getId());
        writer.writeChildren(utenza.getId(), dom, tipi, writer.buildAclEntities(body.getAcl(), utenza.getId()));

        audit(AZIONE_AUDIT_MODIFICA, app, request);
        return ok(app);
    }

    @Transactional
    public ResponseEntity<Void> putPassword(String idA2A, RichiestaCambioPassword body,
                                            HttpServletRequest request) {
        aclAuthorizer.requireScrittura(AclServizio.ANAGRAFICA_APPLICAZIONI);
        Applicazione app = load(idA2A);
        PasswordPolicy.validate(body.getNuovaPassword());

        Utenza utenza = app.getUtenza();
        utenza.setPassword(passwordEncoder.encode(body.getNuovaPassword()));
        utenzaRepository.save(utenza);

        audit(AZIONE_AUDIT_CAMBIO_PASSWORD, app, request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Estrae il valore speciale {@code autodeterminazione} (specifico delle
     * applicazioni → flag {@code trusted}) dalla lista dei tipi pendenza, che
     * viene poi risolta dal writer condiviso come normali riferimenti.
     */
    private static TrustedSplit splitAutodeterminazione(List<TipoPendenzaRef> tipiPendenza) {
        boolean trusted = false;
        List<TipoPendenzaRef> tipi = new ArrayList<>();
        if (tipiPendenza != null) {
            for (TipoPendenzaRef ref : tipiPendenza) {
                String id = ref == null ? null : ref.getIdTipoPendenza();
                if (ApplicazioneMapper.AUTODETERMINAZIONE_ID.equals(id)) {
                    trusted = true;
                } else {
                    tipi.add(ref);
                }
            }
        }
        return new TrustedSplit(trusted, tipi);
    }

    private record TrustedSplit(boolean trusted, List<TipoPendenzaRef> tipi) {
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

    private static void applyCodificaAvvisi(Applicazione app, CodificaAvvisi codifica) {
        if (codifica == null) {
            app.setCodApplicazioneIuv(null);
            app.setRegExp(null);
            app.setAutoIuv(false);
            return;
        }
        String codificaIuv = codifica.getCodificaIuv();
        if (codificaIuv != null && !codificaIuv.isBlank() && !CODIFICA_IUV_PATTERN.matcher(codificaIuv).matches()) {
            throw new UnprocessableEntityException(
                    "Il campo 'codificaAvvisi.codificaIuv' deve essere numerico di 1-3 cifre.");
        }
        String regExpIuv = codifica.getRegExpIuv();
        if (regExpIuv != null && !regExpIuv.isBlank()) {
            try {
                Pattern.compile(regExpIuv);
            } catch (PatternSyntaxException e) {
                throw new UnprocessableEntityException(
                        "Il campo 'codificaAvvisi.regExpIuv' non contiene un'espressione regolare valida.");
            }
        }
        app.setCodApplicazioneIuv(blankToNull(codificaIuv));
        app.setRegExp(blankToNull(regExpIuv));
        app.setAutoIuv(Boolean.TRUE.equals(codifica.getGenerazioneIuvInterna()));
    }

    // --- helper comuni -------------------------------------------------------

    private ResponseEntity<it.govpay.console.model.Applicazione> ok(Applicazione entity) {
        it.govpay.console.model.Applicazione dto = mapper.toDetail(entity);
        return ResponseEntity.ok()
                .eTag(RepresentationEtag.of(dto, objectMapper))
                .body(dto);
    }

    private Applicazione load(String idA2A) {
        return applicazioneRepository.findByCodApplicazione(idA2A)
                .orElseThrow(() -> new NotFoundException("Applicazione non trovata: " + idA2A));
    }

    private void checkIfMatch(String ifMatch, Applicazione entity) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new PreconditionRequiredException(
                    "Header 'If-Match' obbligatorio per le operazioni di modifica.");
        }
        if (!RepresentationEtag.matches(ifMatch, mapper.toDetail(entity), objectMapper)) {
            throw new IfMatchMismatchException(
                    "L'header 'If-Match' non corrisponde alla versione corrente dell'applicazione.");
        }
    }

    private void audit(String azione, Applicazione entity, HttpServletRequest request) {
        OperatoreCorrente operatore = currentOperatorService.get();
        Map<String, Object> dettaglio = new HashMap<>();
        dettaglio.put("idA2A", entity.getCodApplicazione());
        auditService.registra(azione, entity.getId(), dettaglio, operatore, request);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private List<Applicazione> findSlice(Specification<Applicazione> spec, Sort sort, int offset, int maxResults) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Applicazione> q = cb.createQuery(Applicazione.class);
        Root<Applicazione> root = q.from(Applicazione.class);
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
        TypedQuery<Applicazione> typed = entityManager.createQuery(q)
                .setFirstResult(offset)
                .setMaxResults(maxResults);
        return typed.getResultList();
    }

    /** Risolve un path eventualmente annidato (es. {@code utenza.principalOriginale}). */
    private static Path<Object> resolvePath(Root<Applicazione> root, String property) {
        Path<Object> path = null;
        for (String part : property.split("\\.")) {
            path = path == null ? root.get(part) : path.get(part);
        }
        return path;
    }
}
