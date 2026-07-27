package it.govpay.console.operazioni;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.config.OperazioniProperties;
import it.govpay.console.config.OperazioniProperties.OperazioneConfig;
import it.govpay.console.entity.BatchJobExecutionEntity;
import it.govpay.console.model.Esecuzione;
import it.govpay.console.model.ListEsecuzioni200Response;
import it.govpay.console.model.Pagination;
import it.govpay.console.model.StatoEsecuzione;
import it.govpay.console.repository.BatchJobExecutionRepository;
import it.govpay.console.web.BadRequestException;
import it.govpay.console.web.NotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

@Service
public class OperazioneEsecuzioniService {

    private final OperazioniProperties operazioniProperties;
    private final BatchJobExecutionRepository repository;
    private final OperazioneMapper mapper;

    @PersistenceContext
    private EntityManager entityManager;

    public OperazioneEsecuzioniService(OperazioniProperties operazioniProperties,
            BatchJobExecutionRepository repository, OperazioneMapper mapper) {
        this.operazioniProperties = operazioniProperties;
        this.repository = repository;
        this.mapper = mapper;
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
            Page<BatchJobExecutionEntity> p = repository.findAll(spec,
                    PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "id")));
            rows = p.getContent();
            pagination.setHasNextPage(p.hasNext());
            pagination.setTotalResults(p.getTotalElements());
            pagination.setTotalPages(p.getTotalPages());
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
        long id = parseId(idEsecuzione);

        BatchJobExecutionEntity execution = repository.findById(id)
                .filter(e -> e.getJobInstance().getJobName().equals(config.getJobName()))
                .orElseThrow(() -> new NotFoundException(
                        "Esecuzione '" + idEsecuzione + "' non trovata per l'operazione '" + idOperazione + "'."));

        return mapper.toEsecuzione(execution, idOperazione);
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

    /** Slice senza COUNT(*): stesso schema di OperatoreService.findSlice, ordine fisso per id DESC (vedi dataInizio DESC in piano). */
    private List<BatchJobExecutionEntity> findSlice(Specification<BatchJobExecutionEntity> spec, int offset, int maxResults) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<BatchJobExecutionEntity> q = cb.createQuery(BatchJobExecutionEntity.class);
        Root<BatchJobExecutionEntity> root = q.from(BatchJobExecutionEntity.class);
        Predicate predicate = spec.toPredicate(root, q, cb);
        if (predicate != null) {
            q.where(predicate);
        }
        q.orderBy(cb.desc(root.get("id")));
        TypedQuery<BatchJobExecutionEntity> typed = entityManager.createQuery(q)
                .setFirstResult(offset)
                .setMaxResults(maxResults);
        return typed.getResultList();
    }
}
