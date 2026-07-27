package it.govpay.console.operazioni;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import it.govpay.console.audit.AuditService;
import it.govpay.console.config.OperazioniProperties;
import it.govpay.console.config.OperazioniProperties.OperazioneConfig;
import it.govpay.console.model.AclServizio;
import it.govpay.console.model.Esecuzione;
import it.govpay.console.security.AclAuthorizer;
import it.govpay.console.security.CurrentOperatorService;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.web.ConflictException;
import it.govpay.console.web.NotFoundException;
import it.govpay.console.web.UnprocessableEntityException;
import jakarta.servlet.http.HttpServletRequest;

@Service
public class OperazioneEsecuzioneService {

    private static final String AZIONE_AUDIT_AVVIA_ESECUZIONE = "OPERAZIONE_AVVIA_ESECUZIONE";

    private final OperazioniProperties operazioniProperties;
    private final BatchExecutionReader batchExecutionReader;
    private final OperazioneEsecuzioneClient client;
    private final OperazioneMapper mapper;
    private final AclAuthorizer aclAuthorizer;
    private final AuditService auditService;
    private final CurrentOperatorService currentOperatorService;
    private final long pollIntervalMs;
    private final long pollTimeoutMs;

    public OperazioneEsecuzioneService(OperazioniProperties operazioniProperties,
            BatchExecutionReader batchExecutionReader,
            OperazioneEsecuzioneClient client,
            OperazioneMapper mapper,
            AclAuthorizer aclAuthorizer,
            AuditService auditService,
            CurrentOperatorService currentOperatorService,
            @Value("${app.operazioni.trigger.poll-interval-ms:200}") long pollIntervalMs,
            @Value("${app.operazioni.trigger.poll-timeout-ms:2000}") long pollTimeoutMs) {
        this.operazioniProperties = operazioniProperties;
        this.batchExecutionReader = batchExecutionReader;
        this.client = client;
        this.mapper = mapper;
        this.aclAuthorizer = aclAuthorizer;
        this.auditService = auditService;
        this.currentOperatorService = currentOperatorService;
        this.pollIntervalMs = pollIntervalMs;
        this.pollTimeoutMs = pollTimeoutMs;
    }

    public ResponseEntity<Esecuzione> avviaEsecuzione(String idOperazione, boolean force, HttpServletRequest request) {
        aclAuthorizer.requireScrittura(AclServizio.CONFIGURAZIONE_E_MANUTENZIONE);

        OperazioneConfig config = operazioniProperties.getCatalogo().stream()
                .filter(c -> idOperazione.equals(c.getId()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Operazione '" + idOperazione + "' non trovata nel catalogo."));

        if (config.getJobName() == null) {
            throw new UnprocessableEntityException(
                    "L'operazione '" + idOperazione + "' non e' avviabile manualmente (non e' collegata a un job batch).");
        }

        JobExecution before = ultimaEsecuzioneOf(config);
        if (!force && before != null
                && (before.getStatus() == BatchStatus.STARTING || before.getStatus() == BatchStatus.STARTED)) {
            throw new ConflictException(
                    "Un'esecuzione dell'operazione '" + idOperazione + "' e' gia' in corso (JobExecution ID: "
                            + before.getId() + "). Usa il parametro 'force' per avviarne comunque una nuova.");
        }

        if (config.getTriggerUrl() == null) {
            throw new OperazioneTriggerNonConfiguratoException(idOperazione);
        }

        Long beforeId = before != null ? before.getId() : null;
        client.avviaJob(config.getTriggerUrl(), force);

        JobExecution nuova = attendiNuovaEsecuzione(config.getJobName(), beforeId);
        registraAudit(idOperazione, force, nuova, request);

        if (nuova == null) {
            return ResponseEntity.accepted().build();
        }

        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{idEsecuzione}")
                .buildAndExpand(nuova.getId())
                .toUri();
        return ResponseEntity.accepted().location(location).body(mapper.toEsecuzione(config, nuova, force));
    }

    private JobExecution ultimaEsecuzioneOf(OperazioneConfig config) {
        JobInstance jobInstance = batchExecutionReader.getLastJobInstance(config.getJobName());
        return jobInstance != null ? batchExecutionReader.getLastJobExecution(jobInstance) : null;
    }

    private JobExecution attendiNuovaEsecuzione(String jobName, Long beforeId) {
        long deadline = System.currentTimeMillis() + pollTimeoutMs;
        do {
            JobInstance jobInstance = batchExecutionReader.getLastJobInstance(jobName);
            JobExecution execution = jobInstance != null ? batchExecutionReader.getLastJobExecution(jobInstance) : null;
            if (execution != null && (beforeId == null || execution.getId() != beforeId)) {
                return execution;
            }
            sleep(pollIntervalMs);
        } while (System.currentTimeMillis() < deadline);
        return null;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void registraAudit(String idOperazione, boolean force, JobExecution nuova, HttpServletRequest request) {
        OperatoreCorrente operatore = currentOperatorService.get();
        Map<String, Object> dettaglio = new HashMap<>();
        dettaglio.put("idOperazione", idOperazione);
        dettaglio.put("force", force);
        long idOggetto = nuova != null ? nuova.getId() : 0L;
        auditService.registra(AZIONE_AUDIT_AVVIA_ESECUZIONE, idOggetto, dettaglio, operatore, request);
    }
}
