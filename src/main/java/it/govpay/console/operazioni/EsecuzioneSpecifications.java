package it.govpay.console.operazioni;

import java.time.LocalDateTime;
import java.util.Set;

import org.springframework.data.jpa.domain.Specification;

import it.govpay.console.entity.BatchJobExecutionEntity;
import it.govpay.console.model.StatoEsecuzione;

public final class EsecuzioneSpecifications {

    private EsecuzioneSpecifications() {
    }

    public static Specification<BatchJobExecutionEntity> jobNameEquals(String jobName) {
        return (root, q, cb) -> cb.equal(root.get("jobInstance").get("jobName"), jobName);
    }

    public static Specification<BatchJobExecutionEntity> statoEquals(StatoEsecuzione stato) {
        if (stato == null) {
            return null;
        }
        Set<String> statiGrezzi = batchStatusRawValues(stato);
        return (root, q, cb) -> root.get("status").in(statiGrezzi);
    }

    /** {@code value} deve gia' essere convertito nel timezone applicativo (vedi {@code Clock}). */
    public static Specification<BatchJobExecutionEntity> dataInizioMin(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return (root, q, cb) -> cb.greaterThanOrEqualTo(
                cb.coalesce(root.get("startTime"), root.get("createTime")), value);
    }

    /** {@code value} deve gia' essere convertito nel timezone applicativo (vedi {@code Clock}). */
    public static Specification<BatchJobExecutionEntity> dataInizioMax(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return (root, q, cb) -> cb.lessThanOrEqualTo(
                cb.coalesce(root.get("startTime"), root.get("createTime")), value);
    }

    /**
     * Inverso di {@link OperazioneMapper#toStatoEsecuzione}: uno stato
     * pubblico corrisponde a uno o piu' {@code BatchStatus} grezzi.
     */
    private static Set<String> batchStatusRawValues(StatoEsecuzione stato) {
        return switch (stato) {
            case IN_CODA -> Set.of("STARTING");
            case IN_CORSO -> Set.of("STARTED");
            case COMPLETATA -> Set.of("COMPLETED");
            case FALLITA -> Set.of("FAILED", "UNKNOWN");
            case ANNULLATA -> Set.of("STOPPING", "STOPPED", "ABANDONED");
        };
    }
}
