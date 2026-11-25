package it.govpay.console.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import it.govpay.console.entity.GpAudit;
import it.govpay.console.entity.Operatore;

@DataJpaTest
@ActiveProfiles("test")
class EntityRepositorySmokeTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private GpAuditRepository gpAuditRepository;

    @Test
    void persistGpAuditWithIpRichiedente() {
        Operatore operatore = new Operatore();
        operatore.setNome("admin");
        operatore.setIdUtenza(1L);
        em.persist(operatore);

        GpAudit audit = new GpAudit();
        audit.setData(OffsetDateTime.now());
        audit.setIdOggetto(0L);
        audit.setTipoOggetto("PENDENZE_RICERCA_PER_DEBITORE");
        audit.setOggetto("{\"identificativoDebitore\":\"RSSMRA80A01H501U\"}");
        audit.setIdOperatore(operatore.getId());
        audit.setIpRichiedente("203.0.113.42");
        gpAuditRepository.save(audit);

        em.flush();
        em.clear();

        GpAudit loaded = gpAuditRepository.findById(audit.getId()).orElseThrow();
        assertThat(loaded.getTipoOggetto()).isEqualTo("PENDENZE_RICERCA_PER_DEBITORE");
        assertThat(loaded.getIpRichiedente()).isEqualTo("203.0.113.42");
    }
}
