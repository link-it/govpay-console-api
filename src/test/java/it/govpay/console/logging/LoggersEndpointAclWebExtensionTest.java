package it.govpay.console.logging;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.logging.LoggersEndpoint;
import org.springframework.boot.logging.LogLevel;
import org.springframework.security.access.AccessDeniedException;

import it.govpay.console.model.AclServizio;
import it.govpay.console.security.AclAuthorizer;

/**
 * L'endpoint actuator {@code loggers} richiede lo stesso diritto ACL di
 * {@code /admin/logging}: lettura per le consultazioni, scrittura per le
 * modifiche.
 */
@DisplayName("Test ACL dell'endpoint actuator loggers")
class LoggersEndpointAclWebExtensionTest {

    private LoggersEndpoint delegate;
    private AclAuthorizer aclAuthorizer;
    private LoggersEndpointAclWebExtension extension;

    @BeforeEach
    void setUp() {
        delegate = mock(LoggersEndpoint.class);
        aclAuthorizer = mock(AclAuthorizer.class);
        extension = new LoggersEndpointAclWebExtension(delegate, aclAuthorizer);
    }

    @Test
    @DisplayName("Lettura senza diritto: negata e nessuna chiamata all'endpoint")
    void letturaNegata() {
        doThrow(new AccessDeniedException("negato")).when(aclAuthorizer)
                .requireLettura(AclServizio.CONFIGURAZIONE_E_MANUTENZIONE);
        assertThrows(AccessDeniedException.class, () -> extension.loggers());
        assertThrows(AccessDeniedException.class, () -> extension.loggerLevels("it.govpay"));
        verify(delegate, never()).loggers();
        verify(delegate, never()).loggerLevels("it.govpay");
    }

    @Test
    @DisplayName("Lettura con diritto: risposta dell'endpoint")
    void letturaConsentita() {
        LoggersEndpoint.LoggersDescriptor descriptor = mock(LoggersEndpoint.LoggersDescriptor.class);
        when(delegate.loggers()).thenReturn(descriptor);
        assertSame(descriptor, extension.loggers());
        verify(aclAuthorizer).requireLettura(AclServizio.CONFIGURAZIONE_E_MANUTENZIONE);
    }

    @Test
    @DisplayName("Scrittura senza diritto: negata e livello non modificato")
    void scritturaNegata() {
        doThrow(new AccessDeniedException("negato")).when(aclAuthorizer)
                .requireScrittura(AclServizio.CONFIGURAZIONE_E_MANUTENZIONE);
        assertThrows(AccessDeniedException.class, () -> extension.configureLogLevel("it.govpay", LogLevel.DEBUG));
        verify(delegate, never()).configureLogLevel("it.govpay", LogLevel.DEBUG);
    }

    @Test
    @DisplayName("Scrittura con diritto: livello modificato")
    void scritturaConsentita() {
        extension.configureLogLevel("it.govpay", LogLevel.DEBUG);
        verify(aclAuthorizer).requireScrittura(AclServizio.CONFIGURAZIONE_E_MANUTENZIONE);
        verify(delegate).configureLogLevel("it.govpay", LogLevel.DEBUG);
    }
}
