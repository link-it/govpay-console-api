package it.govpay.console.entratadominio;

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
import it.govpay.console.entity.TipoTributo;
import it.govpay.console.entity.Tributo;
import it.govpay.console.entrata.EntrataMapper;
import it.govpay.console.intermediario.JsonPatchApplier;
import it.govpay.console.model.EntrataDominioCreate;
import it.govpay.console.model.EntrataDominioReplace;
import it.govpay.console.model.JsonPatchOperation;
import it.govpay.console.model.ListEntrateDominio200Response;
import it.govpay.console.model.Pagination;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.repository.IbanAccreditoRepository;
import it.govpay.console.repository.TipoTributoRepository;
import it.govpay.console.repository.TributoRepository;
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
public class EntrataDominioService {

    private static final Logger log = LoggerFactory.getLogger(EntrataDominioService.class);

    public static final String AZIONE_AUDIT_CREATE = "ENTRATA_DOMINIO_CREATE";
    public static final String AZIONE_AUDIT_MODIFICA = "ENTRATA_DOMINIO_MODIFICA";

    private final TributoRepository repository;
    private final DominioRepository dominioRepository;
    private final TipoTributoRepository tipoTributoRepository;
    private final IbanAccreditoRepository ibanAccreditoRepository;
    private final EntrataDominioMapper mapper;
    private final EntrataMapper entrataMapper;
    private final RepresentationValidator representationValidator;
    private final CurrentOperatorService currentOperatorService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    public EntrataDominioService(TributoRepository repository,
                                 DominioRepository dominioRepository,
                                 TipoTributoRepository tipoTributoRepository,
                                 IbanAccreditoRepository ibanAccreditoRepository,
                                 EntrataDominioMapper mapper,
                                 EntrataMapper entrataMapper,
                                 RepresentationValidator representationValidator,
                                 CurrentOperatorService currentOperatorService,
                                 AuditService auditService,
                                 ObjectMapper objectMapper) {
        this.repository = repository;
        this.dominioRepository = dominioRepository;
        this.tipoTributoRepository = tipoTributoRepository;
        this.ibanAccreditoRepository = ibanAccreditoRepository;
        this.mapper = mapper;
        this.entrataMapper = entrataMapper;
        this.representationValidator = representationValidator;
        this.currentOperatorService = currentOperatorService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ListEntrateDominio200Response list(String idDominio, EntrataDominioListQuery query) {
        Dominio parent = loadDominio(idDominio);
        log.debug("listEntrateDominio dominio={} filtri[idEntrata={}, descrizione={}, abilitato={}], page={}, limit={}, sort={}, total={}",
                idDominio, query.idEntrata(), query.descrizione(), query.abilitato(),
                query.page(), query.limit(), query.sort(), query.total());

        Specification<Tributo> spec = Specification.allOf(
                Stream.of(
                        EntrataDominioSpecifications.byDominioId(parent.getId()),
                        EntrataDominioSpecifications.idEntrataPartial(query.idEntrata()),
                        EntrataDominioSpecifications.descrizionePartial(query.descrizione()),
                        EntrataDominioSpecifications.abilitatoExact(query.abilitato()))
                .filter(Objects::nonNull)
                .toList());

        Sort sort;
        try {
            sort = EntrataDominioSortParser.parse(query.sort());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }

        int page = query.page();
        int limit = query.limit();
        boolean wantTotal = Boolean.TRUE.equals(query.total());

        Pagination pagination = new Pagination(page, limit, false);
        List<Tributo> rows;
        if (wantTotal) {
            Page<Tributo> p = repository.findAll(spec, PageRequest.of(page - 1, limit, sort));
            rows = p.getContent();
            pagination.setHasNextPage(p.hasNext());
            pagination.setTotalResults(p.getTotalElements());
            pagination.setTotalPages(p.getTotalPages());
        } else {
            List<Tributo> sliced = findSlice(spec, sort, (page - 1) * limit, limit + 1);
            boolean hasNext = sliced.size() > limit;
            rows = hasNext ? sliced.subList(0, limit) : sliced;
            pagination.setHasNextPage(hasNext);
        }

        ListEntrateDominio200Response response = new ListEntrateDominio200Response();
        response.setResults(rows.stream().map(mapper::toSummary).toList());
        response.setPagination(pagination);
        return response;
    }

    @Transactional(readOnly = true)
    public ResponseEntity<it.govpay.console.model.EntrataDominio> get(String idDominio, String idEntrata) {
        Tributo entity = load(idDominio, idEntrata);
        return ok(entity);
    }

    @Transactional
    public ResponseEntity<it.govpay.console.model.EntrataDominio> create(String idDominio,
                                                                         EntrataDominioCreate body,
                                                                         HttpServletRequest request) {
        Dominio parent = loadDominio(idDominio);
        TipoTributo tipoTributo = tipoTributoRepository.findByCodTributo(body.getIdEntrata())
                .orElseThrow(() -> new UnprocessableEntityException(
                        "L'entrata globale '" + body.getIdEntrata() + "' non esiste."));
        if (repository.existsByDominio_IdAndTipoTributo_CodTributo(parent.getId(), body.getIdEntrata())) {
            throw new ConflictException("Esiste gia' l'entrata '" + body.getIdEntrata()
                    + "' per il dominio '" + idDominio + "'.");
        }

        Tributo entity = new Tributo();
        entity.setDominio(parent);
        entity.setTipoTributo(tipoTributo);
        entity.setAbilitato(body.getAbilitato());
        entity.setTipoContabilita(entrataMapper.toCodifica(body.getTipoContabilita()));
        entity.setCodiceContabilita(body.getCodiceContabilita());
        entity.setIbanAccredito(resolveIban(parent, body.getIbanAccredito(), "ibanAccredito"));
        entity.setIbanAppoggio(resolveIban(parent, body.getIbanAppoggio(), "ibanAppoggio"));
        Tributo saved = repository.save(entity);

        audit(AZIONE_AUDIT_CREATE, saved, request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(saved.getTipoTributo().getCodTributo())
                .toUri();
        it.govpay.console.model.EntrataDominio dto = mapper.toDetail(saved);
        return ResponseEntity.created(location)
                .eTag(RepresentationEtag.of(dto, objectMapper))
                .body(dto);
    }

    @Transactional
    public ResponseEntity<it.govpay.console.model.EntrataDominio> replace(String idDominio,
                                                                          String idEntrata,
                                                                          EntrataDominioReplace body,
                                                                          String ifMatch,
                                                                          HttpServletRequest request) {
        Tributo entity = load(idDominio, idEntrata);
        checkIfMatch(ifMatch, entity);

        entity.setAbilitato(body.getAbilitato());
        entity.setTipoContabilita(entrataMapper.toCodifica(body.getTipoContabilita()));
        entity.setCodiceContabilita(body.getCodiceContabilita());
        entity.setIbanAccredito(resolveIban(entity.getDominio(), body.getIbanAccredito(), "ibanAccredito"));
        entity.setIbanAppoggio(resolveIban(entity.getDominio(), body.getIbanAppoggio(), "ibanAppoggio"));
        Tributo saved = repository.save(entity);

        audit(AZIONE_AUDIT_MODIFICA, saved, request);
        return ok(saved);
    }

    @Transactional
    public ResponseEntity<it.govpay.console.model.EntrataDominio> patch(String idDominio,
                                                                        String idEntrata,
                                                                        List<JsonPatchOperation> operations,
                                                                        String ifMatch,
                                                                        HttpServletRequest request) {
        Tributo entity = load(idDominio, idEntrata);
        checkIfMatch(ifMatch, entity);

        ObjectNode current = objectMapper.valueToTree(mapper.toDetail(entity));
        ObjectNode patched = JsonPatchApplier.apply(current, operations, objectMapper);

        it.govpay.console.model.EntrataDominio result;
        try {
            result = objectMapper.treeToValue(patched, it.govpay.console.model.EntrataDominio.class);
        } catch (JacksonException e) {
            throw new BadRequestException(
                    "La rappresentazione risultante dal PATCH non e' una entrata valida: " + e.getOriginalMessage());
        }

        if (!idEntrata.equals(result.getIdEntrata())) {
            throw new BadRequestException("Il campo 'idEntrata' non puo' essere modificato tramite PATCH.");
        }
        if (!Objects.equals(current.get("tipoEntrata"), patched.get("tipoEntrata"))) {
            throw new BadRequestException(
                    "Il campo 'tipoEntrata' e' di sola lettura e non puo' essere modificato tramite PATCH.");
        }
        if (result.getAbilitato() == null) {
            throw new BadRequestException("La rappresentazione risultante dal PATCH ha il campo 'abilitato' mancante.");
        }
        representationValidator.validate(result);

        entity.setAbilitato(result.getAbilitato());
        entity.setTipoContabilita(entrataMapper.toCodifica(result.getTipoContabilita()));
        entity.setCodiceContabilita(result.getCodiceContabilita());
        entity.setIbanAccredito(resolveIban(entity.getDominio(), result.getIbanAccredito(), "ibanAccredito"));
        entity.setIbanAppoggio(resolveIban(entity.getDominio(), result.getIbanAppoggio(), "ibanAppoggio"));
        Tributo saved = repository.save(entity);

        audit(AZIONE_AUDIT_MODIFICA, saved, request);
        return ok(saved);
    }

    // ---- helpers ----------------------------------------------------------

    private IbanAccredito resolveIban(Dominio dominio, String codIban, String campo) {
        if (codIban == null || codIban.isBlank()) {
            return null;
        }
        return ibanAccreditoRepository.findByDominio_IdAndCodIban(dominio.getId(), codIban)
                .orElseThrow(() -> new UnprocessableEntityException(
                        "Il conto '" + codIban + "' indicato in '" + campo
                                + "' non esiste per il dominio '" + dominio.getCodDominio() + "'."));
    }

    private ResponseEntity<it.govpay.console.model.EntrataDominio> ok(Tributo entity) {
        it.govpay.console.model.EntrataDominio dto = mapper.toDetail(entity);
        return ResponseEntity.ok()
                .eTag(RepresentationEtag.of(dto, objectMapper))
                .body(dto);
    }

    private Dominio loadDominio(String idDominio) {
        return dominioRepository.findByCodDominio(idDominio)
                .orElseThrow(() -> new NotFoundException("Dominio non trovato: " + idDominio));
    }

    private Tributo load(String idDominio, String idEntrata) {
        Dominio parent = loadDominio(idDominio);
        return repository.findByDominio_IdAndTipoTributo_CodTributo(parent.getId(), idEntrata)
                .orElseThrow(() -> new NotFoundException("Entrata non trovata: " + idEntrata));
    }

    private void checkIfMatch(String ifMatch, Tributo entity) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new PreconditionRequiredException(
                    "Header 'If-Match' obbligatorio per le operazioni di modifica.");
        }
        if (!RepresentationEtag.matches(ifMatch, mapper.toDetail(entity), objectMapper)) {
            throw new IfMatchMismatchException(
                    "L'header 'If-Match' non corrisponde alla versione corrente dell'entrata.");
        }
    }

    private void audit(String azione, Tributo entity, HttpServletRequest request) {
        OperatoreCorrente operatore = currentOperatorService.get();
        Map<String, Object> dettaglio = new HashMap<>();
        dettaglio.put("idDominio", entity.getDominio().getCodDominio());
        dettaglio.put("idEntrata", entity.getTipoTributo().getCodTributo());
        auditService.registra(azione, entity.getId(), dettaglio, operatore, request);
    }

    private List<Tributo> findSlice(Specification<Tributo> spec, Sort sort, int offset, int maxResults) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tributo> q = cb.createQuery(Tributo.class);
        Root<Tributo> root = q.from(Tributo.class);
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
        TypedQuery<Tributo> typed = entityManager.createQuery(q)
                .setFirstResult(offset)
                .setMaxResults(maxResults);
        return typed.getResultList();
    }

    /** Risolve un path eventualmente annidato (es. {@code tipoTributo.codTributo}). */
    private static Path<Object> resolvePath(Root<Tributo> root, String property) {
        Path<Object> path = null;
        for (String part : property.split("\\.")) {
            path = path == null ? root.get(part) : path.get(part);
        }
        return path;
    }
}
