package it.govpay.console.operazioni;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.springframework.batch.core.BatchStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.config.OperazioniProperties;
import it.govpay.console.config.OperazioniProperties.OperazioneConfig;
import it.govpay.console.entity.BatchJobExecutionEntity;
import it.govpay.console.model.AclServizio;
import it.govpay.console.model.Esecuzione;
import it.govpay.console.model.ListEsecuzioni200Response;
import it.govpay.console.model.Pagination;
import it.govpay.console.model.StatoEsecuzione;
import it.govpay.console.repository.BatchJobExecutionRepository;
import it.govpay.console.security.AclAuthorizer;
import it.govpay.console.web.BadRequestException;
import it.govpay.console.web.ConflictException;
import it.govpay.console.web.NotFoundException;
import it.govpay.console.web.NotImplementedException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

@Service
public class OperazioneEsecuzioniService {

    private final OperazioniProperties operazioniProperties;
    private final BatchJobExecutionRepository repository;
    private final OperazioneMapper mapper;
    private final AclAuthorizer aclAuthorizer;

    @PersistenceContext
    private EntityManager entityManager;

    public OperazioneEsecuzioniService(OperazioniProperties operazioniProperties,
            BatchJobExecutionRepository repository, OperazioneMapper mapper, AclAuthorizer aclAuthorizer) {
        this.operazioniProperties = operazioniProperties;
        this.repository = repository;
        this.mapper = mapper;
        this.aclAuthorizer = aclAuthorizer;
    }

    @Transactional(readOnly = true)
    public ListEsecuzioni200Response list(String idOperazione, StatoEsecuzione stato,
            OffsetDateTime dataInizioMin, OffsetDateTime dataInizioMax,
            int page, int limit, boolean wantTotal) {
        OperazioneConfig config = findConfig(idOperazione);

        ListEsecuzioni200Response response = new ListEsecuzioni200Response();
        Pagination pagination = new Pagination(page, limit, false);

        if (config.getJobName() == null) {
            // Operazione non collegata a un job batch: nessuna esecuzione e' mai esistita.
            response.setResults(List.of());
            response.setPagination(pagination);
            return response;
        }

        Specification<BatchJobExecutionEntity> spec = Specification.allOf(
                Stream.of(
                        EsecuzioneSpecifications.jobNameEquals(config.getJobName()),
                        EsecuzioneSpecifications.statoEquals(stato),
                        EsecuzioneSpecifications.dataInizioMin(mapper.toLocalDateTime(dataInizioMin)),
                        EsecuzioneSpecifications.dataInizioMax(mapper.toLocalDateTime(dataInizioMax)))
                .filter(Objects::nonNull)
                .toList());

        List<BatchJobExecutionEntity> rows;
        if (wantTotal) {
            long totalElements = countTotal(spec);
            int totalPages = (int) Math.ceil((double) totalElements / limit);
            rows = findSlice(spec, (page - 1) * limit, limit);
            pagination.setHasNextPage(page < totalPages);
            pagination.setTotalResults(totalElements);
            pagination.setTotalPages(totalPages);
        } else {
            List<BatchJobExecutionEntity> sliced = findSlice(spec, (page - 1) * limit, limit + 1);
            boolean hasNext = sliced.size() > limit;
            rows = hasNext ? sliced.subList(0, limit) : sliced;
            pagination.setHasNextPage(hasNext);
        }

        response.setResults(rows.stream().map(mapper::toEsecuzioneSummary).toList());
        response.setPagination(pagination);
        return response;
    }

    @Transactional(readOnly = true)
    public Esecuzione dettaglio(String idOperazione, String idEsecuzione) {
        OperazioneConfig config = findConfig(idOperazione);
        BatchJobExecutionEntity execution = findExecutionScoped(config, idOperazione, idEsecuzione);
        return mapper.toEsecuzione(execution, idOperazione);
    }

    /**
     * Cancellazione best-effort: 404/409 sono calcolati per davvero, ma
     * nessun batch espone oggi un meccanismo di cancellazione cooperativa
     * (ne' Spring Batch {@code JobOperator.stop()} ne' un equivalente
     * custom) — per qualunque esecuzione ancora annullabile la richiesta
     * e' sempre rifiutata con 501.
     */
    @Transactional(readOnly = true)
    public void annullaEsecuzione(String idOperazione, String idEsecuzione) {
        aclAuthorizer.requireScrittura(AclServizio.CONFIGURAZIONE_E_MANUTENZIONE);

        OperazioneConfig config = findConfig(idOperazione);
        BatchJobExecutionEntity execution = findExecutionScoped(config, idOperazione, idEsecuzione);

        StatoEsecuzione stato = OperazioneMapper.toStatoEsecuzione(BatchStatus.valueOf(execution.getStatus()));
        if (stato == StatoEsecuzione.COMPLETATA || stato == StatoEsecuzione.FALLITA
                || stato == StatoEsecuzione.ANNULLATA) {
            throw new ConflictException(
                    "L'esecuzione '" + idEsecuzione + "' e' gia' in uno stato terminale (" + stato + ").");
        }

        throw new NotImplementedException(
                "Nessun meccanismo di cancellazione cooperativa disponibile per l'operazione '" + idOperazione + "'.");
    }

    private BatchJobExecutionEntity findExecutionScoped(OperazioneConfig config, String idOperazione, String idEsecuzione) {
        long id = parseId(idEsecuzione);
        return repository.findById(id)
                .filter(e -> e.getJobInstance().getJobName().equals(config.getJobName()))
                .orElseThrow(() -> new NotFoundException(
                        "Esecuzione '" + idEsecuzione + "' non trovata per l'operazione '" + idOperazione + "'."));
    }

    private OperazioneConfig findConfig(String idOperazione) {
        return operazioniProperties.getCatalogo().stream()
                .filter(c -> idOperazione.equals(c.getId()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Operazione '" + idOperazione + "' non trovata nel catalogo."));
    }

    private static long parseId(String idEsecuzione) {
        try {
            return Long.parseLong(idEsecuzione);
        } catch (NumberFormatException e) {
            throw new BadRequestException("Il campo 'idEsecuzione' deve essere un identificativo numerico.");
        }
    }

    /**
     * Slice via Criteria (nessun COUNT(*): stesso schema di
     * OperatoreService.findSlice). Ordine per dataInizio DESC
     * (coalesce(startTime, createTime)) — non esprimibile via Sort/Pageable
     * (solo path di proprieta'), quindi costruito a mano; id DESC come
     * spareggio a parita' di dataInizio.
     */
    private List<BatchJobExecutionEntity> findSlice(Specification<BatchJobExecutionEntity> spec, int offset, int maxResults) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<BatchJobExecutionEntity> q = cb.createQuery(BatchJobExecutionEntity.class);
        Root<BatchJobExecutionEntity> root = q.from(BatchJobExecutionEntity.class);
        Predicate predicate = spec.toPredicate(root, q, cb);
        if (predicate != null) {
            q.where(predicate);
        }
        q.orderBy(cb.desc(dataInizioExpression(cb, root)), cb.desc(root.get("id")));
        TypedQuery<BatchJobExecutionEntity> typed = entityManager.createQuery(q)
                .setFirstResult(offset)
                .setMaxResults(maxResults);
        return typed.getResultList();
    }

    private long countTotal(Specification<BatchJobExecutionEntity> spec) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> q = cb.createQuery(Long.class);
        Root<BatchJobExecutionEntity> root = q.from(BatchJobExecutionEntity.class);
        Predicate predicate = spec.toPredicate(root, q, cb);
        q.select(cb.count(root));
        if (predicate != null) {
            q.where(predicate);
        }
        return entityManager.createQuery(q).getSingleResult();
    }

    private static Expression<LocalDateTime> dataInizioExpression(CriteriaBuilder cb, Root<BatchJobExecutionEntity> root) {
        return cb.coalesce(root.get("startTime"), root.get("createTime"));
    }
}
