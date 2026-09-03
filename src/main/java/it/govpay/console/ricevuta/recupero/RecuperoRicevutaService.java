package it.govpay.console.ricevuta.recupero;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import it.govpay.common.metrics.ExternalCallMetricsRecorder;
import it.govpay.console.audit.AuditService;
import it.govpay.console.config.OperazioniProperties;
import it.govpay.console.config.OperazioniProperties.OperazioneConfig;
import it.govpay.console.entity.RtRecupero;
import it.govpay.console.model.RecuperoRicevutaRequest;
import it.govpay.console.model.Ricevuta;
import it.govpay.console.operazioni.OperazioneBatchClient;
import it.govpay.console.operazioni.OperazioneTriggerNonConfiguratoException;
import it.govpay.console.operazioni.OperazioneTriggerNonRaggiungibileException;
import it.govpay.console.repository.RtRecuperoRepository;
import it.govpay.console.security.CurrentOperatorService;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.web.ConflictException;
import it.govpay.console.web.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Entry point di {@code POST /ricevute/recuperi} (issue #59 §H): pre-flight +
 * upsert della tripla in {@code rt_recuperi} (delegati a
 * {@link RtRecuperoUpsertService}, transazionale), poi trigger del {@code /run}
 * già esposto da {@code govpay-rt-batch} e attesa breve sull'esito.
 *
 * <p>Nessun client verso BizEvents/pagoPA qui: quel lavoro resta interamente
 * nel batch (altro repo, {@code govpay-rt-batch#17}), che elabora la riga e la
 * elimina (successo) o la marca con {@code esito}.
 */
@Service
public class RecuperoRicevutaService {

    private static final Logger log = LoggerFactory.getLogger(RecuperoRicevutaService.class);

    static final String ID_OPERAZIONE_RECUPERO_RT = "RECUPERO_RT";
    private static final String AZIONE_AUDIT_RECUPERA = "RICEVUTA_RECUPERA";
    private static final String ESITO_NON_DISPONIBILE = "NON_DISPONIBILE";
    private static final String CANONICAL_PATH = "/ricevute/{idDominio}/{iuv}/{idRicevuta}";

    private final RtRecuperoUpsertService upsertService;
    private final RtRecuperoRepository rtRecuperoRepository;
    private final it.govpay.console.ricevuta.RicevutaService ricevutaService;
    private final OperazioniProperties operazioniProperties;
    private final OperazioneBatchClient operazioneBatchClient;
    private final ExternalCallMetricsRecorder externalCallMetricsRecorder;
    private final AuditService auditService;
    private final CurrentOperatorService currentOperatorService;
    private final long pollIntervalMs;
    private final long pollTimeoutMs;

    public RecuperoRicevutaService(RtRecuperoUpsertService upsertService,
                                   RtRecuperoRepository rtRecuperoRepository,
                                   it.govpay.console.ricevuta.RicevutaService ricevutaService,
                                   OperazioniProperties operazioniProperties,
                                   OperazioneBatchClient operazioneBatchClient,
                                   ExternalCallMetricsRecorder externalCallMetricsRecorder,
                                   AuditService auditService,
                                   CurrentOperatorService currentOperatorService,
                                   @Value("${app.ricevute.recupero.poll-interval-ms:200}") long pollIntervalMs,
                                   @Value("${app.ricevute.recupero.poll-timeout-ms:2000}") long pollTimeoutMs) {
        this.upsertService = upsertService;
        this.rtRecuperoRepository = rtRecuperoRepository;
        this.ricevutaService = ricevutaService;
        this.operazioniProperties = operazioniProperties;
        this.operazioneBatchClient = operazioneBatchClient;
        this.externalCallMetricsRecorder = externalCallMetricsRecorder;
        this.auditService = auditService;
        this.currentOperatorService = currentOperatorService;
        this.pollIntervalMs = pollIntervalMs;
        this.pollTimeoutMs = pollTimeoutMs;
    }

    public ResponseEntity<Ricevuta> recupera(RecuperoRicevutaRequest body, HttpServletRequest request) {
        OperatoreCorrente operatore = currentOperatorService.get();
        String idDominio = body.getIdDominio();
        String iuv = body.getIuv();
        String idRicevuta = body.getIdRicevuta();

        Long idRiga = null;
        try {
            RtRecupero riga = upsertService.upsert(idDominio, iuv, idRicevuta, operatore);
            idRiga = riga.getId();
            triggerBatch();
            ResponseEntity<Ricevuta> esito = attendiEsito(idDominio, iuv, idRicevuta, riga.getId(), request);
            registraAudit(idDominio, iuv, idRicevuta, idRiga, String.valueOf(esito.getStatusCode().value()), operatore, request);
            return esito;
        } catch (RuntimeException e) {
            registraAudit(idDominio, iuv, idRicevuta, idRiga, esitoDa(e), operatore, request);
            throw e;
        }
    }

    private void triggerBatch() {
        OperazioneConfig config = operazioniProperties.find(ID_OPERAZIONE_RECUPERO_RT)
                .filter(c -> c.getUrl() != null && c.isAbilitata())
                .orElseThrow(() -> new OperazioneTriggerNonConfiguratoException(ID_OPERAZIONE_RECUPERO_RT));
        try {
            externalCallMetricsRecorder.record("operazioni", ID_OPERAZIONE_RECUPERO_RT,
                    () -> operazioneBatchClient.run(config.getUrl(), false));
        } catch (OperazioneTriggerNonRaggiungibileException | ConflictException e) {
            // La riga in rt_recuperi e' comunque gia' committata. Irraggiungibile:
            // verra' raccolta alla prossima esecuzione schedulata. Gia' in
            // esecuzione (409 da /run, ConflictException): il batch e' comunque
            // attivo, non serve un secondo trigger, la riga verra' raccolta da
            // questa o dalla prossima passata. In entrambi i casi non e' un
            // errore per l'operatore (issue #59 §H): si prosegue col poll.
            log.info("Trigger '{}' non ha avviato una nuova esecuzione ({}), la richiesta resta in coda: {}",
                    ID_OPERAZIONE_RECUPERO_RT, e.getClass().getSimpleName(), e.getMessage());
        }
    }

    /**
     * Attesa breve sulla riga appena scritta. {@code esito} sconosciuto (né
     * assente né {@code NON_DISPONIBILE}, valore non ancora definito da
     * {@code govpay-rt-batch#17}) è trattato come presa in carico, non come
     * errore: il {@code /run} è comunque già partito, la ricevuta comparirà
     * sul path canonico a esecuzione del batch terminata.
     */
    private ResponseEntity<Ricevuta> attendiEsito(String idDominio, String iuv, String idRicevuta, Long idRiga,
                                                   HttpServletRequest request) {
        long deadline = System.currentTimeMillis() + pollTimeoutMs;
        do {
            Optional<RtRecupero> corrente = rtRecuperoRepository.findById(idRiga);
            if (corrente.isEmpty()) {
                Ricevuta dto = ricevutaService.getDetail(idDominio, iuv, idRicevuta, request);
                return ResponseEntity.created(locationCanonica(idDominio, iuv, idRicevuta)).body(dto);
            }
            String esito = corrente.get().getEsito();
            if (ESITO_NON_DISPONIBILE.equals(esito)) {
                throw new NotFoundException("Ricevuta non disponibile su pagoPA: idDominio=" + idDominio
                        + ", iuv=" + iuv + ", idRicevuta=" + idRicevuta + ".");
            }
            if (esito != null) {
                return presaInCarico(idDominio, iuv, idRicevuta);
            }
            sleep(pollIntervalMs);
        } while (System.currentTimeMillis() < deadline);
        return presaInCarico(idDominio, iuv, idRicevuta);
    }

    private static ResponseEntity<Ricevuta> presaInCarico(String idDominio, String iuv, String idRicevuta) {
        return ResponseEntity.accepted().location(locationCanonica(idDominio, iuv, idRicevuta)).build();
    }

    private static URI locationCanonica(String idDominio, String iuv, String idRicevuta) {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(CANONICAL_PATH)
                .buildAndExpand(idDominio, iuv, idRicevuta)
                .toUri();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void registraAudit(String idDominio, String iuv, String idRicevuta, Long idRiga, String esito,
                               OperatoreCorrente operatore, HttpServletRequest request) {
        Map<String, Object> dettaglio = new HashMap<>();
        dettaglio.put("idDominio", idDominio);
        dettaglio.put("iuv", iuv);
        dettaglio.put("idRicevuta", idRicevuta);
        dettaglio.put("esito", esito);
        auditService.registra(AZIONE_AUDIT_RECUPERA, idRiga != null ? idRiga : 0L, dettaglio, operatore, request);
    }

    private static String esitoDa(RuntimeException e) {
        if (e instanceof AccessDeniedException) {
            return "403";
        }
        if (e instanceof NotFoundException) {
            return "404";
        }
        if (e instanceof ConflictException) {
            return "409";
        }
        if (e instanceof OperazioneTriggerNonConfiguratoException) {
            return "503";
        }
        return "500";
    }
}
