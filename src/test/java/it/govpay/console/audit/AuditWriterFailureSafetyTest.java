package it.govpay.console.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import it.govpay.console.entity.GpAudit;
import it.govpay.console.repository.GpAuditRepository;

/**
 * Verifica il requisito chiave dello scope H: <b>l'audit fallito non deve mai
 * propagare l'errore al chiamante</b> (la response dell'endpoint non viene
 * impattata). Il fallimento e' loggato e contenuto in {@link AuditWriter}.
 */
@SpringBootTest
@ActiveProfiles("test")
class AuditWriterFailureSafetyTest {

    @Autowired
    private AuditWriter writer;

    @MockitoBean
    private GpAuditRepository gpAuditRepository;

    @Test
    void persistFailureDoesNotPropagate() {
        when(gpAuditRepository.save(any(GpAudit.class)))
                .thenThrow(new DataIntegrityViolationException("constraint violation simulata"));

        assertThatCode(() -> writer.write("PENDENZA_TEST", 42L, "{}", 1L, "1.2.3.4"))
                .as("audit fallito non deve propagare nessuna eccezione")
                .doesNotThrowAnyException();
    }

    @Test
    void runtimeExceptionDuringSaveIsContained() {
        when(gpAuditRepository.save(any(GpAudit.class)))
                .thenThrow(new RuntimeException("boom"));

        assertThatCode(() -> writer.write("PENDENZA_TEST", 42L, "{}", 1L, null))
                .doesNotThrowAnyException();
        assertThat(true).as("se siamo qui senza eccezione, l'audit ha contenuto l'errore").isTrue();
    }
}
