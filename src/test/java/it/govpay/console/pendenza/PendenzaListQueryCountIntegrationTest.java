package it.govpay.console.pendenza;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.entity.Applicazione;
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.TipoVersamento;
import it.govpay.console.entity.TipoVersamentoDominio;
import it.govpay.console.entity.UnitaOperativa;
import it.govpay.console.model.ListPendenze200Response;
import it.govpay.console.repository.ConsoleApplicazioneRepository;
import it.govpay.console.repository.ConsoleDominioRepository;
import it.govpay.console.repository.TipoVersamentoDominioRepository;
import it.govpay.console.repository.TipoVersamentoRepository;
import it.govpay.console.repository.UnitaOperativaRepository;
import it.govpay.console.security.CurrentOperatorService;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.pendenze.model.StatoPendenza;
import jakarta.persistence.EntityManager;

/**
 * Numero di query della lista pendenze. Si invoca direttamente il servizio con l'operatore
 * gia' risolto (sostituito nel test): cosi' il contatore misura solo il caricamento della
 * lista e non autenticazione, risoluzione dell'operatore e filtri HTTP, che dipendono da
 * cache e ordine di esecuzione dei test.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PendenzaListQueryCountIntegrationTest {

    private static final int RIGHE = 6;

    @Autowired private PendenzaService service;
    @Autowired private EntityManager em;
    @Autowired private ConsoleDominioRepository dominioRepository;
    @Autowired private ConsoleApplicazioneRepository applicazioneRepository;
    @Autowired private TipoVersamentoRepository tipoVersamentoRepository;
    @Autowired private TipoVersamentoDominioRepository tipoVersamentoDominioRepository;
    @Autowired private UnitaOperativaRepository unitaOperativaRepository;

    @MockitoBean private CurrentOperatorService currentOperatorService;

    @BeforeEach
    void setup() {
        org.mockito.Mockito.when(currentOperatorService.get()).thenReturn(new OperatoreCorrente(
                "operatore-count", 1L, null, null, true, Set.of(), Set.of(), Set.of(), true, Set.of()));

        Dominio dominio = new Dominio();
        dominio.setCodDominio("55555555555");
        dominio.setRagioneSociale("Dominio conteggio");
        dominio.setAuxDigit(0);
        dominioRepository.save(dominio);

        Applicazione applicazione = new Applicazione();
        applicazione.setCodApplicazione("APP-COUNT");
        applicazioneRepository.save(applicazione);

        TipoVersamento tipo = new TipoVersamento();
        tipo.setCodTipoVersamento("TARI");
        tipo.setDescrizione("TARI");
        tipoVersamentoRepository.save(tipo);

        TipoVersamentoDominio tipoDominio = new TipoVersamentoDominio();
        tipoDominio.setDominio(dominio);
        tipoDominio.setTipoVersamento(tipo);
        tipoVersamentoDominioRepository.save(tipoDominio);

        UnitaOperativa uo = new UnitaOperativa();
        uo.setCodUo("UO-COUNT");
        uo.setUoDenominazione("Ufficio");
        uo.setDominio(dominio);
        unitaOperativaRepository.save(uo);

        for (int i = 1; i <= RIGHE; i++) {
            PendenzaV3Fixtures.pendenza(em, new PendenzaV3Fixtures.Dati(applicazione.getId(), dominio.getId(),
                    tipo.getId(), tipoDominio.getId(), uo.getId(), "PEND-COUNT-" + i, null,
                    "RSSMRA80A01H501U", 10.0, StatoPendenza.NON_ESEGUITO, i));
        }
        em.flush();
    }

    /** Le anagrafiche di dominio, tipo, UO e applicazione si leggono in blocco: le query non crescono con le righe. */
    @Test
    void numeroDiQueryIndipendenteDalNumeroDiRighe() {
        long conUnaRiga = contaQuery(1, false);
        long conTutte = contaQuery(RIGHE, false);

        assertThat(conTutte).isEqualTo(conUnaRiga);
    }

    /** Il totale costa una sola query di conteggio. */
    @Test
    void ilTotaleAggiungeUnaQuery() {
        long senzaTotale = contaQuery(RIGHE, false);
        long conTotale = contaQuery(RIGHE, true);

        assertThat(conTotale).isEqualTo(senzaTotale + 1);
    }

    @Test
    void laListaRestituisceLeRigheAttese() {
        ListPendenze200Response risposta = service.list(query(RIGHE, false), new MockHttpServletRequest());

        assertThat(risposta.getResults()).hasSize(RIGHE);
    }

    private long contaQuery(int limit, boolean total) {
        Statistics statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        // Primo livello vuoto: le letture ricaricano dal database e vanno contate.
        em.flush();
        em.clear();
        statistics.clear();
        ListPendenze200Response risposta = service.list(query(limit, total), new MockHttpServletRequest());
        assertThat(risposta.getResults()).hasSize(Math.min(limit, RIGHE));
        return statistics.getPrepareStatementCount();
    }

    private static PendenzaListQuery query(int limit, boolean total) {
        return new PendenzaListQuery(1, limit, null, total, null, "PEND-COUNT", null, null, null, null,
                null, null, null, null, null, null, null);
    }
}
