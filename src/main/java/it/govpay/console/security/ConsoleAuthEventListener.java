package it.govpay.console.security;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import it.govpay.common.auth.spi.AuthEventListener;
import it.govpay.common.auth.spi.AuthType;
import it.govpay.common.auth.spi.FailureReason;
import it.govpay.console.audit.AuditWriter;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Utenza;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.UtenzaRepository;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Bridge tra l'{@link AuthEventListener} della libreria common-auth e
 * l'{@link AuditWriter}: traduce gli eventi di autenticazione in righe
 * {@code gp_audit}.
 *
 * <p>Politica:
 * <ul>
 *   <li>{@code onLoginSuccess} → {@code PROFILO_LOGIN} con
 *       {@code dettaglio.metodo} = tipo di auth; idOperatore risolto via
 *       {@link CurrentOperatorService}.</li>
 *   <li>{@code onLoginFailed} → {@code PROFILO_LOGIN_FAILED} best-effort:
 *       se l'{@code attemptedPrincipal} esiste in {@code utenze} si
 *       risolve l'{@code Operatore} e si scrive con
 *       {@code dettaglio.motivo}; per principal sconosciuti o senza
 *       Operatore associato si registra solo a log (lo schema di
 *       {@code gp_audit} non ammette riferimenti anonimi).</li>
 *   <li>{@code onLogout} → {@code PROFILO_LOGOUT} con
 *       {@code dettaglio.motivo = USER_REQUEST}; il principal arriva come
 *       argomento del callback (catturato pre-clear del context), lookup
 *       Utenza+Operatore in DB.</li>
 * </ul>
 *
 * <p>Audit failure-safe: ogni branch e' wrappato in try/catch e non
 * propaga eccezioni al chiamante.
 */
@Component
public class ConsoleAuthEventListener implements AuthEventListener {

    private static final Logger log = LoggerFactory.getLogger(ConsoleAuthEventListener.class);

    private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";

    private final AuditWriter auditWriter;
    private final ObjectMapper objectMapper;
    private final CurrentOperatorService currentOperatorService;
    private final UtenzaRepository utenzaRepository;
    private final OperatoreRepository operatoreRepository;

    public ConsoleAuthEventListener(AuditWriter auditWriter,
                                    ObjectMapper objectMapper,
                                    CurrentOperatorService currentOperatorService,
                                    UtenzaRepository utenzaRepository,
                                    OperatoreRepository operatoreRepository) {
        this.auditWriter = auditWriter;
        this.objectMapper = objectMapper;
        this.currentOperatorService = currentOperatorService;
        this.utenzaRepository = utenzaRepository;
        this.operatoreRepository = operatoreRepository;
    }

    @Override
    public void onLoginSuccess(String principal, AuthType authType, HttpServletRequest request) {
        log.info("Login OK principal={} metodo={}", principal, authType);
        try {
            OperatoreCorrente operatore = currentOperatorService.get();
            if (operatore.idOperatore() == null) {
                log.warn("Audit PROFILO_LOGIN saltato: nessun operatore per utenza principal='{}'", principal);
                return;
            }
            Map<String, Object> dettaglio = new LinkedHashMap<>();
            dettaglio.put("metodo", authType.name());
            String oggetto = serialize(dettaglio);
            auditWriter.write("PROFILO_LOGIN", operatore.idUtenza(), oggetto,
                    operatore.idOperatore(), resolveClientIp(request));
        } catch (RuntimeException e) {
            log.warn("Audit PROFILO_LOGIN fallito per principal='{}' (non bloccante)", principal, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void onLoginFailed(String attemptedPrincipal,
                              AuthType authType,
                              FailureReason reason,
                              HttpServletRequest request) {
        log.warn("Login fallito principal={} metodo={} motivo={}",
                attemptedPrincipal, authType, reason);
        if (attemptedPrincipal == null || attemptedPrincipal.isBlank()) {
            return;
        }
        try {
            Utenza utenza = utenzaRepository.findByPrincipal(attemptedPrincipal).orElse(null);
            if (utenza == null) {
                return;
            }
            Operatore operatore = operatoreRepository.findByIdUtenza(utenza.getId()).orElse(null);
            if (operatore == null) {
                return;
            }
            Map<String, Object> dettaglio = new LinkedHashMap<>();
            dettaglio.put("metodo", authType.name());
            dettaglio.put("motivo", reason.name());
            String oggetto = serialize(dettaglio);
            auditWriter.write("PROFILO_LOGIN_FAILED", utenza.getId(), oggetto,
                    operatore.getId(), resolveClientIp(request));
        } catch (RuntimeException e) {
            log.warn("Audit PROFILO_LOGIN_FAILED fallito per principal='{}' (non bloccante)",
                    attemptedPrincipal, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void onLogout(String principal, HttpServletRequest request) {
        log.info("Logout OK principal={}", principal);
        if (principal == null || principal.isBlank()) {
            return;
        }
        try {
            Utenza utenza = utenzaRepository.findByPrincipal(principal).orElse(null);
            if (utenza == null) {
                log.warn("Audit PROFILO_LOGOUT saltato: utenza non trovata per principal='{}'", principal);
                return;
            }
            Operatore operatore = operatoreRepository.findByIdUtenza(utenza.getId()).orElse(null);
            if (operatore == null) {
                log.warn("Audit PROFILO_LOGOUT saltato: nessun operatore per utenza principal='{}'", principal);
                return;
            }
            Map<String, Object> dettaglio = new LinkedHashMap<>();
            dettaglio.put("motivo", "USER_REQUEST");
            String oggetto = serialize(dettaglio);
            auditWriter.write("PROFILO_LOGOUT", utenza.getId(), oggetto,
                    operatore.getId(), resolveClientIp(request));
        } catch (RuntimeException e) {
            log.warn("Audit PROFILO_LOGOUT fallito per principal='{}' (non bloccante)", principal, e);
        }
    }

    private String serialize(Map<String, Object> dettaglio) {
        try {
            return objectMapper.writeValueAsString(dettaglio);
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
            int comma = forwarded.indexOf(',');
            return (comma < 0 ? forwarded : forwarded.substring(0, comma)).trim();
        }
        return request.getRemoteAddr();
    }
}
