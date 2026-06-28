package it.govpay.console.dominio;

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
import it.govpay.console.entity.Stazione;
import it.govpay.console.entity.UnitaOperativa;
import it.govpay.console.intermediario.JsonPatchApplier;
import it.govpay.console.model.DominioCreate;
import it.govpay.console.model.DominioReplace;
import it.govpay.console.model.JsonPatchOperation;
import it.govpay.console.model.ListDomini200Response;
import it.govpay.console.model.Pagination;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.repository.StazioneRepository;
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
public class DominioService {

    private static final Logger log = LoggerFactory.getLogger(DominioService.class);

    public static final String AZIONE_AUDIT_CREATE = "DOMINIO_CREATE";
    public static final String AZIONE_AUDIT_MODIFICA = "DOMINIO_MODIFICA";

    /** Codice della unita' operativa speciale che porta l'anagrafica del dominio. */
    private static final String COD_UO_EC = "EC";

    private final DominioRepository repository;
    private final UnitaOperativaRepository uoRepository;
    private final StazioneRepository stazioneRepository;
    private final DominioMapper mapper;
    private final DominioSemanticValidator semanticValidator;
    private final RepresentationValidator representationValidator;
    private final CurrentOperatorService currentOperatorService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    public DominioService(DominioRepository repository,
                          UnitaOperativaRepository uoRepository,
                          StazioneRepository stazioneRepository,
                          DominioMapper mapper,
                          DominioSemanticValidator semanticValidator,
                          RepresentationValidator representationValidator,
                          CurrentOperatorService currentOperatorService,
                          AuditService auditService,
                          ObjectMapper objectMapper) {
        this.repository = repository;
        this.uoRepository = uoRepository;
        this.stazioneRepository = stazioneRepository;
        this.mapper = mapper;
        this.semanticValidator = semanticValidator;
        this.representationValidator = representationValidator;
        this.currentOperatorService = currentOperatorService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ListDomini200Response list(DominioListQuery query) {
        log.debug("listDomini filtri[idDominio={}, ragioneSociale={}, abilitato={}], "
                        + "page={}, limit={}, sort={}, total={}",
                query.idDominio(), query.ragioneSociale(), query.abilitato(),
                query.page(), query.limit(), query.sort(), query.total());

        Specification<Dominio> spec = Specification.allOf(
                Stream.of(
                        DominioSpecifications.codDominioPartial(query.idDominio()),
                        DominioSpecifications.ragioneSocialePartial(query.ragioneSociale()),
                        DominioSpecifications.abilitatoExact(query.abilitato()))
                .filter(Objects::nonNull)
                .toList());

        Sort sort;
        try {
            sort = DominioSortParser.parse(query.sort());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }

        int page = query.page();
        int limit = query.limit();
        boolean wantTotal = Boolean.TRUE.equals(query.total());

        Pagination pagination = new Pagination(page, limit, false);
        List<Dominio> rows;
        if (wantTotal) {
            Page<Dominio> p = repository.findAll(spec, PageRequest.of(page - 1, limit, sort));
            rows = p.getContent();
            pagination.setHasNextPage(p.hasNext());
            pagination.setTotalResults(p.getTotalElements());
            pagination.setTotalPages(p.getTotalPages());
        } else {
            List<Dominio> sliced = findSlice(spec, sort, (page - 1) * limit, limit + 1);
            boolean hasNext = sliced.size() > limit;
            rows = hasNext ? sliced.subList(0, limit) : sliced;
            pagination.setHasNextPage(hasNext);
        }

        ListDomini200Response response = new ListDomini200Response();
        response.setResults(rows.stream().map(mapper::toSummary).toList());
        response.setPagination(pagination);
        return response;
    }

    @Transactional(readOnly = true)
    public ResponseEntity<it.govpay.console.model.Dominio> get(String idDominio) {
        Dominio entity = load(idDominio);
        return ok(entity, loadEc(entity));
    }

    @Transactional
    public ResponseEntity<it.govpay.console.model.Dominio> create(DominioCreate body,
                                                                  HttpServletRequest request) {
        if (repository.existsByCodDominio(body.getIdDominio())) {
            throw new ConflictException("Esiste gia' un dominio con idDominio '" + body.getIdDominio() + "'.");
        }
        semanticValidator.validate(body.getIntermediato(), body.getGln(), body.getIdStazione(),
                body.getSegregationCode(), body.getCbill(), body.getAutStampaPosteItaliane(),
                body.getIuvPrefix(), body.getAuxDigit());
        Stazione stazione = resolveStazioneOptional(body.getIdStazione());

        Dominio entity = new Dominio();
        entity.setCodDominio(body.getIdDominio());
        writeDominio(entity, body.getRagioneSociale(), body.getGln(), body.getCbill(),
                body.getIuvPrefix(), body.getAutStampaPosteItaliane(), body.getAuxDigit(),
                body.getSegregationCode(), body.getTassonomiaPagoPA(), body.getIntermediato(),
                body.getScaricaFr(), body.getAbilitato(), stazione);
        Dominio saved = repository.save(entity);

        UnitaOperativa ec = new UnitaOperativa();
        ec.setCodUo(COD_UO_EC);
        ec.setAbilitato(Boolean.TRUE);
        ec.setDominio(saved);
        writeEc(ec, saved.getCodDominio(), body.getRagioneSociale(), body.getIndirizzo(),
                body.getCivico(), body.getCap(), body.getLocalita(), body.getProvincia(),
                body.getNazione(), body.getEmail(), body.getPec(), body.getTel(),
                body.getFax(), body.getWeb(), body.getArea());
        UnitaOperativa savedEc = uoRepository.save(ec);

        audit(AZIONE_AUDIT_CREATE, saved, request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(saved.getCodDominio())
                .toUri();
        it.govpay.console.model.Dominio dto = mapper.toDetail(saved, savedEc);
        return ResponseEntity.created(location)
                .eTag(RepresentationEtag.of(dto, objectMapper))
                .body(dto);
    }

    @Transactional
    public ResponseEntity<it.govpay.console.model.Dominio> replace(String idDominio, DominioReplace body,
                                                                   String ifMatch, HttpServletRequest request) {
        Dominio entity = load(idDominio);
        UnitaOperativa ec = loadOrCreateEc(entity);
        checkIfMatch(ifMatch, entity, ec);

        semanticValidator.validate(body.getIntermediato(), body.getGln(), body.getIdStazione(),
                body.getSegregationCode(), body.getCbill(), body.getAutStampaPosteItaliane(),
                body.getIuvPrefix(), body.getAuxDigit());
        Stazione stazione = resolveStazioneOptional(body.getIdStazione());
        writeDominio(entity, body.getRagioneSociale(), body.getGln(), body.getCbill(),
                body.getIuvPrefix(), body.getAutStampaPosteItaliane(), body.getAuxDigit(),
                body.getSegregationCode(), body.getTassonomiaPagoPA(), body.getIntermediato(),
                body.getScaricaFr(), body.getAbilitato(), stazione);
        writeEc(ec, entity.getCodDominio(), body.getRagioneSociale(), body.getIndirizzo(),
                body.getCivico(), body.getCap(), body.getLocalita(), body.getProvincia(),
                body.getNazione(), body.getEmail(), body.getPec(), body.getTel(),
                body.getFax(), body.getWeb(), body.getArea());

        Dominio saved = repository.save(entity);
        UnitaOperativa savedEc = uoRepository.save(ec);

        audit(AZIONE_AUDIT_MODIFICA, saved, request);
        return ok(saved, savedEc);
    }

    @Transactional
    public ResponseEntity<it.govpay.console.model.Dominio> patch(String idDominio,
                                                                 List<JsonPatchOperation> operations,
                                                                 String ifMatch, HttpServletRequest request) {
        Dominio entity = load(idDominio);
        UnitaOperativa ec = loadOrCreateEc(entity);
        checkIfMatch(ifMatch, entity, ec);

        ObjectNode current = objectMapper.valueToTree(mapper.toDetail(entity, ec));
        ObjectNode patched = JsonPatchApplier.apply(current, operations, objectMapper);

        it.govpay.console.model.Dominio result;
        try {
            result = objectMapper.treeToValue(patched, it.govpay.console.model.Dominio.class);
        } catch (JacksonException e) {
            throw new BadRequestException(
                    "La rappresentazione risultante dal PATCH non e' un dominio valido: " + e.getOriginalMessage());
        }

        if (!idDominio.equals(result.getIdDominio())) {
            throw new BadRequestException("Il campo 'idDominio' non puo' essere modificato tramite PATCH.");
        }
        // riferimentoIntermediario e' derivato (sola lettura): per cambiare l'intermediario
        // si modifica idStazione. Un PATCH che lo tocca verrebbe ignorato: lo rifiutiamo.
        // Objects.equals: per un dominio non intermediato il campo e' assente (node null).
        if (!Objects.equals(current.get("riferimentoIntermediario"), patched.get("riferimentoIntermediario"))) {
            throw new BadRequestException(
                    "Il campo 'riferimentoIntermediario' e' derivato e non puo' essere modificato tramite PATCH "
                            + "(usa 'idStazione').");
        }
        // abilitato e scaricaFr sono sempre obbligatori ma non sono notNull nello schema di
        // dettaglio, quindi vanno verificati qui; gli altri vincoli via Bean Validation e
        // validazione semantica.
        if (result.getAbilitato() == null) {
            throw new BadRequestException("La rappresentazione risultante dal PATCH ha il campo 'abilitato' mancante.");
        }
        if (result.getScaricaFr() == null) {
            throw new BadRequestException("La rappresentazione risultante dal PATCH ha il campo 'scaricaFr' mancante.");
        }
        representationValidator.validate(result);
        semanticValidator.validate(result.getIntermediato(), result.getGln(), result.getIdStazione(),
                result.getSegregationCode(), result.getCbill(), result.getAutStampaPosteItaliane(),
                result.getIuvPrefix(), result.getAuxDigit());

        Stazione stazione = resolveStazioneOptional(result.getIdStazione());
        writeDominio(entity, result.getRagioneSociale(), result.getGln(), result.getCbill(),
                result.getIuvPrefix(), result.getAutStampaPosteItaliane(), result.getAuxDigit(),
                result.getSegregationCode(), result.getTassonomiaPagoPA(), result.getIntermediato(),
                result.getScaricaFr(), result.getAbilitato(), stazione);
        writeEc(ec, entity.getCodDominio(), result.getRagioneSociale(), result.getIndirizzo(),
                result.getCivico(), result.getCap(), result.getLocalita(), result.getProvincia(),
                result.getNazione(), result.getEmail(), result.getPec(), result.getTel(),
                result.getFax(), result.getWeb(), result.getArea());

        Dominio saved = repository.save(entity);
        UnitaOperativa savedEc = uoRepository.save(ec);

        audit(AZIONE_AUDIT_MODIFICA, saved, request);
        return ok(saved, savedEc);
    }

    // ---- write helpers ----------------------------------------------------

    private void writeDominio(Dominio e, String ragioneSociale, String gln, String cbill,
            String iuvPrefix, String autStampaPoste, Integer auxDigit, Integer segregationCode,
            String tassonomia, Boolean intermediato, Boolean scaricaFr, Boolean abilitato,
            Stazione stazione) {
        e.setRagioneSociale(ragioneSociale);
        e.setGln(gln);
        e.setCbill(cbill);
        e.setIuvPrefix(iuvPrefix);
        e.setAutStampaPoste(autStampaPoste);
        e.setAuxDigit(auxDigit != null ? auxDigit : Integer.valueOf(0));
        e.setSegregationCode(segregationCode);
        e.setTassonomiaPagoPa(tassonomia);
        e.setIntermediato(intermediato != null ? intermediato : Boolean.TRUE);
        e.setScaricaFr(scaricaFr);
        e.setAbilitato(abilitato);
        e.setStazione(stazione);
    }

    private void writeEc(UnitaOperativa ec, String codDominio, String ragioneSociale,
            String indirizzo, String civico, String cap, String localita, String provincia,
            String nazione, String email, String pec, String tel, String fax, String web, String area) {
        ec.setUoCodiceIdentificativo(codDominio);
        ec.setUoDenominazione(ragioneSociale);
        ec.setUoIndirizzo(indirizzo);
        ec.setUoCivico(civico);
        ec.setUoCap(cap);
        ec.setUoLocalita(localita);
        ec.setUoProvincia(provincia);
        ec.setUoNazione(nazione);
        ec.setUoEmail(email);
        ec.setUoPec(pec);
        ec.setUoTel(tel);
        ec.setUoFax(fax);
        ec.setUoUrlSitoWeb(web);
        ec.setUoArea(area);
    }

    private Stazione resolveStazioneOptional(String idStazione) {
        if (idStazione == null) {
            return null;
        }
        return stazioneRepository.findByCodStazione(idStazione)
                .orElseThrow(() -> new UnprocessableEntityException(
                        "Stazione non trovata per idStazione '" + idStazione + "'."));
    }

    private ResponseEntity<it.govpay.console.model.Dominio> ok(Dominio entity, UnitaOperativa ec) {
        it.govpay.console.model.Dominio dto = mapper.toDetail(entity, ec);
        return ResponseEntity.ok()
                .eTag(RepresentationEtag.of(dto, objectMapper))
                .body(dto);
    }

    private Dominio load(String idDominio) {
        return repository.findByCodDominio(idDominio)
                .orElseThrow(() -> new NotFoundException("Dominio non trovato: " + idDominio));
    }

    private UnitaOperativa loadEc(Dominio dominio) {
        return uoRepository.findByDominio_IdAndCodUo(dominio.getId(), COD_UO_EC).orElse(null);
    }

    private UnitaOperativa loadOrCreateEc(Dominio dominio) {
        UnitaOperativa ec = loadEc(dominio);
        if (ec == null) {
            ec = new UnitaOperativa();
            ec.setCodUo(COD_UO_EC);
            ec.setAbilitato(Boolean.TRUE);
            ec.setDominio(dominio);
        }
        return ec;
    }

    private void checkIfMatch(String ifMatch, Dominio entity, UnitaOperativa ec) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new PreconditionRequiredException(
                    "Header 'If-Match' obbligatorio per le operazioni di modifica.");
        }
        if (!RepresentationEtag.matches(ifMatch, mapper.toDetail(entity, ec), objectMapper)) {
            throw new IfMatchMismatchException(
                    "L'header 'If-Match' non corrisponde alla versione corrente del dominio.");
        }
    }

    private void audit(String azione, Dominio entity, HttpServletRequest request) {
        OperatoreCorrente operatore = currentOperatorService.get();
        Map<String, Object> dettaglio = new HashMap<>();
        dettaglio.put("idDominio", entity.getCodDominio());
        auditService.registra(azione, entity.getId(), dettaglio, operatore, request);
    }

    private List<Dominio> findSlice(Specification<Dominio> spec, Sort sort, int offset, int maxResults) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Dominio> q = cb.createQuery(Dominio.class);
        Root<Dominio> root = q.from(Dominio.class);
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
        TypedQuery<Dominio> typed = entityManager.createQuery(q)
                .setFirstResult(offset)
                .setMaxResults(maxResults);
        return typed.getResultList();
    }
}
