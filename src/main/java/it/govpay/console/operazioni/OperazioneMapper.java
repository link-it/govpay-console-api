package it.govpay.console.operazioni;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.stereotype.Component;

import it.govpay.console.config.OperazioniProperties.OperazioneConfig;
import it.govpay.console.entity.BatchJobExecutionEntity;
import it.govpay.console.model.Esecuzione;
import it.govpay.console.model.EsecuzioneSummary;
import it.govpay.console.model.Operazione;
import it.govpay.console.model.StatoEsecuzione;

@Component
public class OperazioneMapper {

    private final Clock clock;

    public OperazioneMapper(Clock clock) {
        this.clock = clock;
    }

    Operazione toOperazione(OperazioneConfig config, JobExecution ultimaEsecuzione) {
        EsecuzioneSummary summary = ultimaEsecuzione != null ? toEsecuzioneSummary(ultimaEsecuzione) : null;

        Operazione operazione = new Operazione(config.getId(), config.getNome(), config.isAbilitata());
        operazione.descrizione(config.getDescrizione());
        operazione.frequenzaSchedulata(config.getFrequenzaSchedulata() != null ? config.getFrequenzaSchedulata().toString() : null);
        operazione.ultimaEsecuzione(summary);
        operazione.prossimaEsecuzione(calcolaProssimaEsecuzione(config, summary));
        operazione.lockAttivo(null);
        return operazione;
    }

    private EsecuzioneSummary toEsecuzioneSummary(JobExecution execution) {
        EsecuzioneSummary summary = new EsecuzioneSummary(String.valueOf(execution.getId()),
                toStatoEsecuzione(execution.getStatus()), toOffsetDateTime(dataInizioOf(execution)));
        summary.dataFine(toOffsetDateTime(execution.getEndTime()));
        return summary;
    }

    Esecuzione toEsecuzione(OperazioneConfig config, JobExecution execution, boolean forzata) {
        Esecuzione esecuzione = new Esecuzione(String.valueOf(execution.getId()), config.getId(),
                toStatoEsecuzione(execution.getStatus()), toOffsetDateTime(dataInizioOf(execution)));
        esecuzione.dataFine(toOffsetDateTime(execution.getEndTime()));
        esecuzione.forzata(forzata);
        return esecuzione;
    }

    EsecuzioneSummary toEsecuzioneSummary(BatchJobExecutionEntity execution) {
        EsecuzioneSummary summary = new EsecuzioneSummary(String.valueOf(execution.getId()),
                toStatoEsecuzione(BatchStatus.valueOf(execution.getStatus())),
                toOffsetDateTime(dataInizioOf(execution.getStartTime(), execution.getCreateTime())));
        summary.dataFine(toOffsetDateTime(execution.getEndTime()));
        return summary;
    }

    /**
     * {@code forzata} e' sempre null: le esecuzioni lette storicamente (non
     * appena create da {@code POST .../esecuzioni}) non hanno modo di
     * distinguere avvio manuale da schedulato — Spring Batch non registra
     * la provenienza di una JobExecution.
     */
    Esecuzione toEsecuzione(BatchJobExecutionEntity execution, String idOperazione) {
        Esecuzione esecuzione = new Esecuzione(String.valueOf(execution.getId()), idOperazione,
                toStatoEsecuzione(BatchStatus.valueOf(execution.getStatus())),
                toOffsetDateTime(dataInizioOf(execution.getStartTime(), execution.getCreateTime())));
        esecuzione.dataFine(toOffsetDateTime(execution.getEndTime()));
        esecuzione.forzata(null);
        return esecuzione;
    }

    // getStartTime() e' null finche' l'esecuzione e' solo in coda
    // (STARTING): CREATE_TIME e' invece NOT NULL a schema, sempre
    // valorizzato alla creazione della JobExecution.
    private static LocalDateTime dataInizioOf(JobExecution execution) {
        return dataInizioOf(execution.getStartTime(), execution.getCreateTime());
    }

    private static LocalDateTime dataInizioOf(LocalDateTime startTime, LocalDateTime createTime) {
        return startTime != null ? startTime : createTime;
    }

    private OffsetDateTime calcolaProssimaEsecuzione(OperazioneConfig config, EsecuzioneSummary ultimaEsecuzione) {
        if (!config.isAbilitata() || config.getFrequenzaSchedulata() == null || ultimaEsecuzione == null) {
            return null;
        }
        OffsetDateTime dataFine = ultimaEsecuzione.getDataFine().orElse(null);
        return dataFine != null ? dataFine.plus(config.getFrequenzaSchedulata()) : null;
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return value != null ? value.atZone(clock.getZone()).toOffsetDateTime() : null;
    }

    /** Converte un filtro OffsetDateTime in ingresso nel timezone applicativo, per confrontarlo con le colonne LocalDateTime di Spring Batch. */
    LocalDateTime toLocalDateTime(OffsetDateTime value) {
        return value != null ? value.atZoneSameInstant(clock.getZone()).toLocalDateTime() : null;
    }

    static StatoEsecuzione toStatoEsecuzione(BatchStatus status) {
        return switch (status) {
            // STARTING: JobExecution creata ma non ancora avviata (startTime
            // ancora null) - genuinamente "in coda", non "in corso".
            case STARTING -> StatoEsecuzione.IN_CODA;
            case STARTED -> StatoEsecuzione.IN_CORSO;
            case COMPLETED -> StatoEsecuzione.COMPLETATA;
            case FAILED, UNKNOWN -> StatoEsecuzione.FALLITA;
            case STOPPING, STOPPED, ABANDONED -> StatoEsecuzione.ANNULLATA;
        };
    }
}
