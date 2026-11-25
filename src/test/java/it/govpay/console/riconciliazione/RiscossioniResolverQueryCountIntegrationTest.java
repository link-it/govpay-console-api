package it.govpay.console.riconciliazione;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.entity.Applicazione;
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.TipoVersamento;
import it.govpay.console.entity.TipoVersamentoDominio;
import it.govpay.console.pendenza.PendenzaV3Fixtures;
import it.govpay.console.pendenza.PendenzaV3Fixtures.Dati;
import it.govpay.console.repository.ConsoleApplicazioneRepository;
import it.govpay.console.repository.ConsoleDominioRepository;
import it.govpay.console.repository.TipoVersamentoDominioRepository;
import it.govpay.console.repository.TipoVersamentoRepository;
import it.govpay.console.riconciliazione.RiscossioniResolver.RiferimentoPendenza;
import it.govpay.pendenze.entity.Pagamento;
import it.govpay.pendenze.entity.Pendenza;
import it.govpay.pendenze.entity.VocePendenza;
import it.govpay.pendenze.model.StatoPendenza;
import it.govpay.pendenze.model.StatoVocePendenza;
import jakarta.persistence.EntityManager;

/**
 * Il link alla pendenza delle riscossioni si risolve con una sola query, qualunque sia il numero
 * di riscossioni: il passaggio da relazione a chiavi piatte non introduce un N+1.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RiscossioniResolverQueryCountIntegrationTest {

    @Autowired private RiscossioniResolver resolver;
    @Autowired private EntityManager em;
    @Autowired private ConsoleDominioRepository dominioRepository;
    @Autowired private ConsoleApplicazioneRepository applicazioneRepository;
    @Autowired private TipoVersamentoRepository tipoVersamentoRepository;
    @Autowired private TipoVersamentoDominioRepository tipoVersamentoDominioRepository;

    private List<Pagamento> riscossioni(int quante) {
        Dominio dominio = new Dominio();
        dominio.setCodDominio("44444444444");
        dominio.setRagioneSociale("Dominio resolver");
        dominio.setAuxDigit(0);
        dominioRepository.save(dominio);
        Applicazione app = new Applicazione();
        app.setCodApplicazione("APP-RES");
        applicazioneRepository.save(app);
        TipoVersamento tv = new TipoVersamento();
        tv.setCodTipoVersamento("TARI");
        tv.setDescrizione("TARI");
        tipoVersamentoRepository.save(tv);
        TipoVersamentoDominio tvd = new TipoVersamentoDominio();
        tvd.setDominio(dominio);
        tvd.setTipoVersamento(tv);
        tipoVersamentoDominioRepository.save(tvd);

        List<Pagamento> risultato = new ArrayList<>();
        for (int i = 1; i <= quante; i++) {
            Pendenza p = PendenzaV3Fixtures.pendenza(em, new Dati(app.getId(), dominio.getId(), tv.getId(),
                    tvd.getId(), null, "PEND-RES-" + i, null, "RSSMRA80A01H501U", 10.0,
                    StatoPendenza.ESEGUITO, 0));
            VocePendenza voce = new VocePendenza();
            voce.setIdVocePendenza("V-" + i);
            voce.setIndice(1);
            voce.setImporto(10.0);
            voce.setDescrizione("Voce");
            voce.setStato(StatoVocePendenza.ESEGUITO);
            p.addVocePendenza(voce);
            em.persist(voce);
            Pagamento pagamento = new Pagamento();
            pagamento.setIdSingoloVersamento(voce.getId());
            risultato.add(pagamento);
        }
        em.flush();
        em.clear();
        return risultato;
    }

    private long contaQuery(List<Pagamento> riscossioni, Map<Long, RiferimentoPendenza>[] risultato) {
        Statistics statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        risultato[0] = resolver.loadPendenze(riscossioni);
        return statistics.getPrepareStatementCount();
    }

    @Test
    @SuppressWarnings("unchecked")
    void unaSolaQueryQualunqueSiaIlNumeroDiRiscossioni() {
        Map<Long, RiferimentoPendenza>[] risultato = new Map[1];

        List<Pagamento> sei = riscossioni(6);
        long query = contaQuery(sei, risultato);

        assertThat(query).isEqualTo(1);
        assertThat(risultato[0]).hasSize(6);
        assertThat(risultato[0].get(sei.get(2).getIdSingoloVersamento()))
                .isEqualTo(new RiferimentoPendenza("APP-RES", "PEND-RES-3"));
    }

    @Test
    void senzaRiscossioniConVoceNonInterrogaIlDatabase() {
        assertThat(resolver.loadPendenze(List.of(new Pagamento()))).isEmpty();
    }
}
