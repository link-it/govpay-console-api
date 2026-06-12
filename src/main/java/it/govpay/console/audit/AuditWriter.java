package it.govpay.console.audit;

import java.time.OffsetDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.entity.GpAudit;
import it.govpay.console.repository.GpAuditRepository;

/**
 * Persiste asincronamente una riga di {@code gp_audit}. Estratto da
 * {@link AuditService} in scope H della issue #9 per:
 * <ul>
 *   <li>far girare il {@code save} in un thread separato senza bloccare la
 *       response dell'endpoint chiamante;</li>
 *   <li>aprire una nuova transazione propria (la transazione di lettura del
 *       caller potrebbe gia' essere conclusa quando il thread async parte);</li>
 *   <li>contenere le {@code Exception} (audit non deve mai propagare errori
 *       all'utente).</li>
 * </ul>
 */
@Component
public class AuditWriter {

    private static final Logger log = LoggerFactory.getLogger(AuditWriter.class);

    private final GpAuditRepository repository;

    public AuditWriter(GpAuditRepository repository) {
        this.repository = repository;
    }

    @Async("auditExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(String azione, long idOggetto, String oggettoSerializzato,
                      Long idOperatore, String ipRichiedente) {
        try {
            GpAudit row = new GpAudit();
            row.setData(OffsetDateTime.now());
            row.setIdOggetto(idOggetto);
            row.setTipoOggetto(azione);
            row.setOggetto(oggettoSerializzato);
            row.setIdOperatore(idOperatore);
            row.setIpRichiedente(ipRichiedente);
            repository.save(row);
            log.debug("audit persistito azione={} idOggetto={} idOperatore={} ip={}",
                    azione, idOggetto, idOperatore, ipRichiedente);
        } catch (RuntimeException e) {
            // Audit non bloccante: log e drop. La response del caller non
            // viene impattata.
            log.error("Audit fallito ma non bloccante azione={} idOggetto={}: {}",
                    azione, idOggetto, e.getMessage(), e);
        }
    }
}
