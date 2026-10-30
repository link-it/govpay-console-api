package it.govpay.console.logging;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.annotation.EndpointWebExtension;
import org.springframework.boot.actuate.logging.LoggersEndpoint;
import org.springframework.boot.logging.LogLevel;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import it.govpay.console.model.AclServizio;
import it.govpay.console.security.AclAuthorizer;

/**
 * Estensione web dell'endpoint actuator {@code loggers}: applica le stesse
 * regole di {@link LogLevelController} ({@code /admin/logging}), cioe' il
 * diritto ACL sul servizio {@code Configurazione e manutenzione} — lettura
 * per le consultazioni, scrittura per le modifiche.
 * <p>
 * Senza questa estensione {@code /actuator/loggers}, esposto sulla porta di
 * management, era accessibile a qualsiasi operatore autenticato, senza
 * verifica delle ACL. L'autenticazione resta quella della catena di
 * sicurezza (l'endpoint non e' tra i path pubblici).
 */
@Component
@ConditionalOnAvailableEndpoint(endpoint = LoggersEndpoint.class)
@EndpointWebExtension(endpoint = LoggersEndpoint.class)
public class LoggersEndpointAclWebExtension {

    private static final AclServizio SERVIZIO = AclServizio.CONFIGURAZIONE_E_MANUTENZIONE;
    private static final int STATUS_FORBIDDEN = 403;

    private final LoggersEndpoint delegate;
    private final AclAuthorizer aclAuthorizer;

    public LoggersEndpointAclWebExtension(LoggersEndpoint delegate, AclAuthorizer aclAuthorizer) {
        this.delegate = delegate;
        this.aclAuthorizer = aclAuthorizer;
    }

    @ReadOperation
    public WebEndpointResponse<Object> loggers() {
        return autorizzato(false, delegate::loggers);
    }

    @ReadOperation
    public WebEndpointResponse<Object> loggerLevels(@Selector String name) {
        return autorizzato(false, () -> delegate.loggerLevels(name));
    }

    @WriteOperation
    public WebEndpointResponse<Object> configureLogLevel(@Selector String name, @Nullable LogLevel configuredLevel) {
        return autorizzato(true, () -> {
            delegate.configureLogLevel(name, configuredLevel);
            return null;
        });
    }

    private WebEndpointResponse<Object> autorizzato(boolean scrittura, Supplier<Object> operazione) {
        try {
            if (scrittura) {
                aclAuthorizer.requireScrittura(SERVIZIO);
            } else {
                aclAuthorizer.requireLettura(SERVIZIO);
            }
        } catch (AccessDeniedException e) {
            Map<String, Object> problem = new LinkedHashMap<>();
            problem.put("status", STATUS_FORBIDDEN);
            problem.put("title", "Forbidden");
            problem.put("detail", e.getMessage());
            return new WebEndpointResponse<>(problem, STATUS_FORBIDDEN);
        }
        Object risultato = operazione.get();
        if (risultato == null) {
            return new WebEndpointResponse<>(scrittura
                    ? WebEndpointResponse.STATUS_NO_CONTENT
                    : WebEndpointResponse.STATUS_NOT_FOUND);
        }
        return new WebEndpointResponse<>(risultato, WebEndpointResponse.STATUS_OK);
    }
}
