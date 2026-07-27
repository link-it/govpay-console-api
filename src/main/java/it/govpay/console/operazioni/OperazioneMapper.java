package it.govpay.console.operazioni;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.stereotype.Component;

import it.govpay.console.config.OperazioniProperties.OperazioneConfig;
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
        // getStartTime() e' null finche' l'esecuzione e' solo in coda
        // (STARTING): CREATE_TIME e' invece NOT NULL a schema, sempre
        // valorizzato alla creazione della JobExecution.
        LocalDateTime dataInizio = execution.getStartTime() != null ? execution.getStartTime() : execution.getCreateTime();
        EsecuzioneSummary summary = new EsecuzioneSummary(String.valueOf(execution.getId()),
                toStatoEsecuzione(execution.getStatus()), toOffsetDateTime(dataInizio));
        summary.dataFine(toOffsetDateTime(execution.getEndTime()));
        return summary;
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
