package it.govpay.console.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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
    @DisplayName("Lettura senza diritto: 403 e nessuna chiamata all'endpoint")
    void letturaNegata() {
        doThrow(new AccessDeniedException("negato")).when(aclAuthorizer)
                .requireLettura(AclServizio.CONFIGURAZIONE_E_MANUTENZIONE);
        assertEquals(403, extension.loggers().getStatus());
        assertEquals(403, extension.loggerLevels("it.govpay").getStatus());
        verify(delegate, never()).loggers();
        verify(delegate, never()).loggerLevels("it.govpay");
    }

    @Test
    @DisplayName("Lettura con diritto: 200 con la risposta dell'endpoint, 404 per un logger inesistente")
    void letturaConsentita() {
        LoggersEndpoint.LoggersDescriptor descriptor = mock(LoggersEndpoint.LoggersDescriptor.class);
        when(delegate.loggers()).thenReturn(descriptor);
        assertEquals(200, extension.loggers().getStatus());
        assertSame(descriptor, extension.loggers().getBody());
        assertEquals(404, extension.loggerLevels("inesistente").getStatus());
    }

    @Test
    @DisplayName("Scrittura senza diritto: 403 e livello non modificato")
    void scritturaNegata() {
        doThrow(new AccessDeniedException("negato")).when(aclAuthorizer)
                .requireScrittura(AclServizio.CONFIGURAZIONE_E_MANUTENZIONE);
        assertEquals(403, extension.configureLogLevel("it.govpay", LogLevel.DEBUG).getStatus());
        verify(delegate, never()).configureLogLevel("it.govpay", LogLevel.DEBUG);
    }

    @Test
    @DisplayName("Scrittura con diritto: 204 e livello modificato")
    void scritturaConsentita() {
        assertEquals(204, extension.configureLogLevel("it.govpay", LogLevel.DEBUG).getStatus());
        verify(delegate).configureLogLevel("it.govpay", LogLevel.DEBUG);
    }
}
