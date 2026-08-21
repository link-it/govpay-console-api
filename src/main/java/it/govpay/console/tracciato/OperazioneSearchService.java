package it.govpay.console.tracciato;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.audit.AuditService;
import it.govpay.console.entity.Operazione;
import it.govpay.console.entity.Tracciato;
import it.govpay.console.model.ListTracciatoPendenzeOperazioni200Response;
import it.govpay.console.model.OperazionePendenza;
import it.govpay.console.model.StatoOperazionePendenza;
import it.govpay.console.model.TipoOperazionePendenza;
import it.govpay.console.pagination.BadCursorException;
import it.govpay.console.repository.OperazioneRepository;
import it.govpay.console.repository.TracciatoRepository;
import it.govpay.console.security.CurrentOperatorService;
import it.govpay.console.security.DominioVisibilita;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.web.NotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Base64;

/**
 * {@code GET .../tracciati/{id}/operazioni} (lista, cursor-only — nessuna
 * modalita' offset: le operazioni sono scoped a un singolo tracciato,
 * bound superiore naturale) e {@code .../operazioni/{numero}} (dettaglio,
 * con audit).
 */
@Service
public class OperazioneSearchService {

    public static final String AZIONE_AUDIT_OPERAZIONE_VISUALIZZA = "TRACCIATO_OPERAZIONE_VISUALIZZA";

    private final OperazioneRepository repository;
    private final TracciatoRepository tracciatoRepository;
    private final OperazionePendenzaMapper mapper;
    private final CurrentOperatorService currentOperatorService;
    private final AuditService auditService;

    @PersistenceContext
    private EntityManager entityManager;

    public OperazioneSearchService(OperazioneRepository repository,
                                   TracciatoRepository tracciatoRepository,
                                   OperazionePendenzaMapper mapper,
                                   CurrentOperatorService currentOperatorService,
                                   AuditService auditService) {
        this.repository = repository;
        this.tracciatoRepository = tracciatoRepository;
        this.mapper = mapper;
        this.currentOperatorService = currentOperatorService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public ListTracciatoPendenzeOperazioni200Response list(Long idTracciato,
                                                            Integer limit,
                                                            String cursor,
                                                            StatoOperazionePendenza stato,
                                                            TipoOperazionePendenza tipoOperazione) {
        loadTracciatoVisibile(idTracciato);

        Specification<Operazione> spec = Specification.allOf(
                Stream.of(
                        OperazioneSpecifications.diTracciato(idTracciato),
                        OperazioneSpecifications.statoExact(stato),
                        OperazioneSpecifications.tipoOperazioneExact(tipoOperazione))
                .filter(Objects::nonNull)
                .toList());

        int effectiveLimit = limit == null ? 25 : limit;
        Long cursorNumero = decodeCursor(cursor);

        List<Operazione> sliced = findByCursor(spec, cursorNumero, effectiveLimit + 1);
        boolean hasNext = sliced.size() > effectiveLimit;
        List<Operazione> rows = hasNext ? sliced.subList(0, effectiveLimit) : sliced;

        ListTracciatoPendenzeOperazioni200Response response = new ListTracciatoPendenzeOperazioni200Response(
                rows.stream().map(mapper::toSummary).toList());
        if (hasNext && !rows.isEmpty()) {
            response.setNextCursor(encodeCursor(rows.get(rows.size() - 1).getLineaElaborazione()));
        }
        return response;
    }

    @Transactional(readOnly = true)
    public OperazionePendenza get(Long idTracciato, Long numero, HttpServletRequest request) {
        Tracciato tracciato = loadTracciatoVisibile(idTracciato);
        Operazione operazione = repository.findByTracciato_IdAndLineaElaborazione(idTracciato, numero)
                .orElseThrow(() -> new NotFoundException(
                        "Operazione non trovata: tracciato=" + idTracciato + ", numero=" + numero));

        OperazionePendenza dto = mapper.toDetail(operazione);

        OperatoreCorrente operatore = currentOperatorService.get();
        auditService.registra(AZIONE_AUDIT_OPERAZIONE_VISUALIZZA, operazione.getId(),
                java.util.Map.of("idTracciato", idTracciato, "numero", numero), operatore, request);
        return dto;
    }

    private Tracciato loadTracciatoVisibile(Long idTracciato) {
        OperatoreCorrente operatore = currentOperatorService.get();
        Tracciato tracciato = tracciatoRepository.findById(idTracciato)
                .orElseThrow(() -> new NotFoundException("Tracciato non trovato: " + idTracciato));
        if (!DominioVisibilita.isVisibile(tracciato.getDominio().getId(), operatore)) {
            throw new NotFoundException("Tracciato non trovato: " + idTracciato);
        }
        return tracciato;
    }

    private List<Operazione> findByCursor(Specification<Operazione> spec, Long cursorNumero, int maxResults) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Operazione> q = cb.createQuery(Operazione.class);
        Root<Operazione> root = q.from(Operazione.class);

        Predicate specPredicate = spec.toPredicate(root, q, cb);
        Path<Long> numeroPath = root.get("lineaElaborazione");

        Predicate where = specPredicate;
        if (cursorNumero != null) {
            Predicate keyset = cb.greaterThan(numeroPath, cursorNumero);
            where = where != null ? cb.and(where, keyset) : keyset;
        }
        if (where != null) {
            q.where(where);
        }
        q.orderBy(cb.asc(numeroPath));

        TypedQuery<Operazione> typed = entityManager.createQuery(q).setMaxResults(maxResults);
        return typed.getResultList();
    }

    /** Cursor semplice (solo {@code numero}, gia' univoco all'interno del tracciato): niente timestamp, {@link it.govpay.console.pagination.CursorCodec} non e' un buon fit qui. */
    private static Long decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            return Long.parseLong(raw);
        } catch (IllegalArgumentException e) {
            throw new BadCursorException("Cursor malformato.", e);
        }
    }

    private static String encodeCursor(Long numero) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(Long.toString(numero).getBytes(StandardCharsets.UTF_8));
    }
}
