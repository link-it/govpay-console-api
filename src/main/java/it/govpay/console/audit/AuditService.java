package it.govpay.console.audit;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import it.govpay.console.security.OperatoreCorrente;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Entry point per la scrittura di righe in {@code gp_audit}. In scope H della
 * issue #9 la persistence e' delegata ad {@link AuditWriter} asincrono:
 * questo service estrae IP e idOperatore (operazioni che richiedono la
 * request del thread chiamante) e poi delega al writer.
 *
 * <p>Convenzione tipo_oggetto/azione: codici UPPER_SNAKE
 * (`PENDENZE_RICERCA_PER_DEBITORE`, `PENDENZA_VISUALIZZA_DEBITORE`, ...).
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";

    private final AuditWriter writer;
    private final ObjectMapper objectMapper;

    public AuditService(AuditWriter writer, ObjectMapper objectMapper) {
        this.writer = writer;
        this.objectMapper = objectMapper;
    }

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
        String oggetto = serializeDettaglio(dettaglio);
        String ip = resolveClientIp(request);
        writer.write(azione, idOggetto, oggetto, operatore.idOperatore(), ip);
    }

    private String serializeDettaglio(Map<String, Object> dettaglio) {
        try {
            return objectMapper.writeValueAsString(dettaglio != null ? dettaglio : Map.of());
        } catch (JacksonException e) {
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
