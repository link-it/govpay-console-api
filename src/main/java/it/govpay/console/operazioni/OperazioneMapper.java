package it.govpay.console.operazioni;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import org.springframework.stereotype.Component;

import it.govpay.common.batch.dto.BatchInfo;
import it.govpay.common.batch.dto.ExecutionSummaryInfo;
import it.govpay.common.batch.dto.LastExecutionInfo;
import it.govpay.common.batch.dto.NextExecutionInfo;
import it.govpay.console.config.OperazioniProperties.OperazioneConfig;
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

    Operazione toOperazione(OperazioneConfig config, BatchInfo info, LastExecutionInfo ultima, NextExecutionInfo prossima) {
        Operazione operazione = new Operazione(config.getId(), info.getDisplayName(), config.isAbilitata());
        operazione.descrizione(info.getDescription());
        operazione.frequenzaSchedulata(toIsoDuration(prossima.getIntervalMillis()));
        operazione.ultimaEsecuzione(toEsecuzioneSummary(ultima));
        operazione.prossimaEsecuzione(toOffsetDateTime(prossima.getNextExecutionTime()));
        operazione.lockAttivo(null);
        return operazione;
    }

    Operazione toOperazioneLocale(OperazioneConfig config, OperazioneLocaleHandler handler) {
        Operazione operazione = new Operazione(config.getId(), handler.getNome(), config.isAbilitata());
        operazione.descrizione(handler.getDescrizione());
        nessunDatoDinamico(operazione);
        return operazione;
    }

    /**
     * Voce degradata quando il microservizio proprietario non e'
     * raggiungibile: {@code nome} ripiega sull'id (unico dato sempre
     * disponibile localmente), nessun dato dinamico.
     */
    Operazione toOperazioneNonRaggiungibile(OperazioneConfig config) {
        Operazione operazione = new Operazione(config.getId(), config.getId(), config.isAbilitata());
        operazione.descrizione("Microservizio proprietario del job non raggiungibile.");
        nessunDatoDinamico(operazione);
        return operazione;
    }

    /** Chiavi sempre presenti nel JSON (JsonNullable "valorizzato a null", non omesso). */
    private static void nessunDatoDinamico(Operazione operazione) {
        operazione.frequenzaSchedulata(null);
        operazione.ultimaEsecuzione(null);
        operazione.prossimaEsecuzione(null);
        operazione.lockAttivo(null);
    }

    /** Null se il batch non ha mai completato un'esecuzione ({@code executionId} assente). */
    private EsecuzioneSummary toEsecuzioneSummary(LastExecutionInfo execution) {
        if (execution == null || execution.getExecutionId() == null) {
            return null;
        }
        EsecuzioneSummary summary = new EsecuzioneSummary(String.valueOf(execution.getExecutionId()),
                toStatoEsecuzione(execution.getStatus()), toOffsetDateTime(execution.getStartTime()));
        summary.dataFine(toOffsetDateTime(execution.getEndTime()));
        return summary;
    }

    EsecuzioneSummary toEsecuzioneSummary(ExecutionSummaryInfo row) {
        EsecuzioneSummary summary = new EsecuzioneSummary(String.valueOf(row.getExecutionId()),
                toStatoEsecuzione(row.getStatus()), toOffsetDateTime(row.getStartTime()));
        summary.dataFine(toOffsetDateTime(row.getEndTime()));
        return summary;
    }

    Esecuzione toEsecuzione(String idOperazione, LastExecutionInfo execution) {
        Esecuzione esecuzione = new Esecuzione(String.valueOf(execution.getExecutionId()), idOperazione,
                toStatoEsecuzione(execution.getStatus()), toOffsetDateTime(execution.getStartTime()));
        esecuzione.dataFine(toOffsetDateTime(execution.getEndTime()));
        esecuzione.forzata(toForzata(execution.getTriggerType()));
        return esecuzione;
    }

    /**
     * Esecuzione "istantanea" di un'operazione locale (non backed da un job
     * batch): eseguita sincronamente, sempre completata al momento della
     * chiamata.
     */
    Esecuzione toEsecuzioneLocale(String idOperazione) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        Esecuzione esecuzione = new Esecuzione(idOperazione, idOperazione, StatoEsecuzione.COMPLETATA, now);
        esecuzione.dataFine(now);
        esecuzione.forzata(true);
        return esecuzione;
    }

    /** {@code MANUAL} → true, {@code SCHEDULED} → false, assente (esecuzioni precedenti al JobParameter) → null. */
    private static Boolean toForzata(String triggerType) {
        if (triggerType == null) {
            return null;
        }
        return "MANUAL".equals(triggerType);
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return value != null ? value.atZone(clock.getZone()).toOffsetDateTime() : null;
    }

    /** {@code frequenzaSchedulata} e' documentata in formato durata ISO 8601 (es. {@code PT2H}). */
    private static String toIsoDuration(Long intervalMillis) {
        return intervalMillis != null ? Duration.ofMillis(intervalMillis).toString() : null;
    }

    /**
     * {@code statoGrezzo} e' lo stato Spring Batch nativo cosi' come
     * restituito da govpay-common (govpay-common e' l'unico punto che
     * ancora dipende da {@code spring-batch-core}; console-api lavora sulla
     * stringa per restare disaccoppiato).
     */
    static StatoEsecuzione toStatoEsecuzione(String statoGrezzo) {
        return switch (statoGrezzo) {
            // STARTING: JobExecution creata ma non ancora avviata (startTime
            // ancora null) - genuinamente "in coda", non "in corso".
            case "STARTING" -> StatoEsecuzione.IN_CODA;
            case "STARTED" -> StatoEsecuzione.IN_CORSO;
            case "COMPLETED" -> StatoEsecuzione.COMPLETATA;
            case "FAILED", "UNKNOWN" -> StatoEsecuzione.FALLITA;
            case "STOPPING", "STOPPED", "ABANDONED" -> StatoEsecuzione.ANNULLATA;
            default -> throw new IllegalArgumentException("Stato batch grezzo sconosciuto: " + statoGrezzo);
        };
    }

    /**
     * Inverso di {@link #toStatoEsecuzione}: uno stato pubblico corrisponde
     * a uno o piu' {@code BatchStatus} grezzi, tradotti nel formato CSV
     * accettato da {@code GET {url}/executions?stato=}.
     */
    static String toBatchStatusCsv(StatoEsecuzione stato) {
        if (stato == null) {
            return null;
        }
        return switch (stato) {
            case IN_CODA -> "STARTING";
            case IN_CORSO -> "STARTED";
            case COMPLETATA -> "COMPLETED";
            case FALLITA -> "FAILED,UNKNOWN";
            case ANNULLATA -> "STOPPING,STOPPED,ABANDONED";
        };
    }
}
