package it.govpay.console.logging;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.web.annotation.EndpointWebExtension;
import org.springframework.boot.actuate.logging.LoggersEndpoint;
import org.springframework.boot.logging.LogLevel;
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
 * <p>
 * Le operazioni hanno le stesse firme e gli stessi tipi di ritorno di
 * {@link LoggersEndpoint}: le operazioni dell'estensione sostituiscono quelle
 * dell'endpoint solo se il predicato della richiesta coincide (path, metodo,
 * media type prodotti). Il diniego e' un'{@code AccessDeniedException}, che la
 * catena di sicurezza traduce in 403.
 */
@Component
@ConditionalOnAvailableEndpoint(endpoint = LoggersEndpoint.class)
@EndpointWebExtension(endpoint = LoggersEndpoint.class)
public class LoggersEndpointAclWebExtension {

    private static final AclServizio SERVIZIO = AclServizio.CONFIGURAZIONE_E_MANUTENZIONE;

    private final LoggersEndpoint delegate;
    private final AclAuthorizer aclAuthorizer;

    public LoggersEndpointAclWebExtension(LoggersEndpoint delegate, AclAuthorizer aclAuthorizer) {
        this.delegate = delegate;
        this.aclAuthorizer = aclAuthorizer;
    }

    @ReadOperation
    public LoggersEndpoint.LoggersDescriptor loggers() {
        aclAuthorizer.requireLettura(SERVIZIO);
        return delegate.loggers();
    }

    @ReadOperation
    public LoggersEndpoint.@Nullable LoggerLevelsDescriptor loggerLevels(@Selector String name) {
        aclAuthorizer.requireLettura(SERVIZIO);
        return delegate.loggerLevels(name);
    }

    @WriteOperation
    public void configureLogLevel(@Selector String name, @Nullable LogLevel configuredLevel) {
        aclAuthorizer.requireScrittura(SERVIZIO);
        delegate.configureLogLevel(name, configuredLevel);
    }
}
