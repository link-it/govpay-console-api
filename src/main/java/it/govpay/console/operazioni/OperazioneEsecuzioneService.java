package it.govpay.console.operazioni;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import it.govpay.common.batch.dto.BatchStatusInfo;
import it.govpay.common.batch.dto.LastExecutionInfo;
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
import jakarta.servlet.http.HttpServletRequest;

@Service
public class OperazioneEsecuzioneService {

    private static final String AZIONE_AUDIT_AVVIA_ESECUZIONE = "OPERAZIONE_AVVIA_ESECUZIONE";

    private final OperazioniProperties operazioniProperties;
    private final OperazioneBatchClient client;
    private final OperazioneMapper mapper;
    private final AclAuthorizer aclAuthorizer;
    private final AuditService auditService;
    private final CurrentOperatorService currentOperatorService;
    private final Map<String, OperazioneLocaleHandler> handlersPerId;
    private final long pollIntervalMs;
    private final long pollTimeoutMs;

    public OperazioneEsecuzioneService(OperazioniProperties operazioniProperties,
            OperazioneBatchClient client,
            OperazioneMapper mapper,
            AclAuthorizer aclAuthorizer,
            AuditService auditService,
            CurrentOperatorService currentOperatorService,
            List<OperazioneLocaleHandler> handlers,
            @Value("${app.operazioni.trigger.poll-interval-ms:200}") long pollIntervalMs,
            @Value("${app.operazioni.trigger.poll-timeout-ms:2000}") long pollTimeoutMs) {
        this.operazioniProperties = operazioniProperties;
        this.client = client;
        this.mapper = mapper;
        this.aclAuthorizer = aclAuthorizer;
        this.auditService = auditService;
        this.currentOperatorService = currentOperatorService;
        this.handlersPerId = handlers.stream().collect(Collectors.toMap(OperazioneLocaleHandler::getId, Function.identity()));
        this.pollIntervalMs = pollIntervalMs;
        this.pollTimeoutMs = pollTimeoutMs;
    }

    public ResponseEntity<Esecuzione> avviaEsecuzione(String idOperazione, boolean force, HttpServletRequest request) {
        aclAuthorizer.requireScrittura(AclServizio.CONFIGURAZIONE_E_MANUTENZIONE);

        OperazioneConfig config = operazioniProperties.getCatalogo().stream()
                .filter(c -> idOperazione.equals(c.getId()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Operazione '" + idOperazione + "' non trovata nel catalogo."));

        if (config.getUrl() == null) {
            return avviaEsecuzioneLocale(config, request);
        }
        return avviaEsecuzioneRemota(config, force, request);
    }

    private ResponseEntity<Esecuzione> avviaEsecuzioneLocale(OperazioneConfig config, HttpServletRequest request) {
        OperazioneLocaleHandler handler = handlersPerId.get(config.getId());
        if (handler == null) {
            throw new OperazioneTriggerNonConfiguratoException(config.getId());
        }

        handler.eseguire();
        Esecuzione esecuzione = mapper.toEsecuzioneLocale(config.getId());
        registraAudit(config.getId(), true, 0L, request);
        return ResponseEntity.accepted().body(esecuzione);
    }

    private ResponseEntity<Esecuzione> avviaEsecuzioneRemota(OperazioneConfig config, boolean force, HttpServletRequest request) {
        BatchStatusInfo statoPrima = client.status(config.getUrl());

        if (!force && statoPrima.isRunning()) {
            throw new ConflictException(
                    "Un'esecuzione dell'operazione '" + config.getId() + "' e' gia' in corso (JobExecution ID: "
                            + statoPrima.getExecutionId() + "). Usa il parametro 'force' per avviarne comunque una nuova.");
        }

        Long beforeId = statoPrima.isRunning() ? statoPrima.getExecutionId() : null;
        client.run(config.getUrl(), force);

        Long nuovoId = attendiNuovaEsecuzione(config.getUrl(), beforeId);
        registraAudit(config.getId(), force, nuovoId, request);

        if (nuovoId == null) {
            return ResponseEntity.accepted().build();
        }

        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{idEsecuzione}")
                .buildAndExpand(nuovoId)
                .toUri();
        LastExecutionInfo dettaglio = client.getExecution(config.getUrl(), nuovoId);
        return ResponseEntity.accepted().location(location).body(mapper.toEsecuzione(config.getId(), dettaglio));
    }

    private Long attendiNuovaEsecuzione(String url, Long beforeId) {
        long deadline = System.currentTimeMillis() + pollTimeoutMs;
        do {
            BatchStatusInfo stato = client.status(url);
            if (stato.isRunning() && (beforeId == null || !stato.getExecutionId().equals(beforeId))) {
                return stato.getExecutionId();
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

    private void registraAudit(String idOperazione, boolean force, Long idOggetto, HttpServletRequest request) {
        OperatoreCorrente operatore = currentOperatorService.get();
        Map<String, Object> dettaglio = new HashMap<>();
        dettaglio.put("idOperazione", idOperazione);
        dettaglio.put("force", force);
        auditService.registra(AZIONE_AUDIT_AVVIA_ESECUZIONE, idOggetto != null ? idOggetto : 0L, dettaglio, operatore, request);
    }
}
