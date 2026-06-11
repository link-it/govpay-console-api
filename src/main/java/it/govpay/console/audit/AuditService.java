package it.govpay.console.audit;

import java.time.OffsetDateTime;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.govpay.console.entity.GpAudit;
import it.govpay.console.repository.GpAuditRepository;
import it.govpay.console.security.OperatoreCorrente;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Scrive una riga in {@code gp_audit}. In #9 e' sincrono; lo step H lo
 * trasformera' in {@code @Async} con failure handling (vedi
 * {@code project-sequence-issue-9}).
 *
 * Convenzione tipo_oggetto/azione: codici UPPER_SNAKE
 * (`PENDENZE_RICERCA_PER_DEBITORE`, `PENDENZA_VISUALIZZA_DEBITORE`, ...).
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";

    private final GpAuditRepository repository;
    private final ObjectMapper objectMapper;

    public AuditService(GpAuditRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void registra(String azione,
                         long idOggetto,
                         Map<String, Object> dettaglio,
                         OperatoreCorrente operatore,
                         HttpServletRequest request) {
        if (operatore.idOperatore() == null) {
            log.warn("Audit '{}' saltato: operatore non risolvibile per principal '{}'",
                    azione, operatore.principal());
            return;
        }
        GpAudit row = new GpAudit();
        row.setData(OffsetDateTime.now());
        row.setIdOggetto(idOggetto);
        row.setTipoOggetto(azione);
        row.setOggetto(serializeDettaglio(dettaglio));
        row.setIdOperatore(operatore.idOperatore());
        row.setIpRichiedente(resolveClientIp(request));
        repository.save(row);
        log.debug("audit registrato azione={} idOggetto={} operatore={} ip={}",
                azione, idOggetto, operatore.principal(), row.getIpRichiedente());
    }

    private String serializeDettaglio(Map<String, Object> dettaglio) {
        try {
            return objectMapper.writeValueAsString(dettaglio != null ? dettaglio : Map.of());
        } catch (JsonProcessingException e) {
            log.warn("Audit: serializzazione dettaglio fallita, scrivo placeholder", e);
            return "{}";
        }
    }

    private static String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader(HEADER_X_FORWARDED_FOR);
        if (forwarded != null && !forwarded.isBlank()) {
            // X-Forwarded-For puo' contenere una catena: il primo e' il client originale.
            int comma = forwarded.indexOf(',');
            return (comma < 0 ? forwarded : forwarded.substring(0, comma)).trim();
        }
        return request.getRemoteAddr();
    }
}
