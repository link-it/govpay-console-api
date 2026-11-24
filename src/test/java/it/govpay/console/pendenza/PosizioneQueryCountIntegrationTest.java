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
import it.govpay.console.repository.ConsoleApplicazioneRepository;
import it.govpay.console.repository.ConsoleDominioRepository;
import it.govpay.console.repository.TipoVersamentoDominioRepository;
import it.govpay.console.repository.TipoVersamentoRepository;
import it.govpay.console.repository.UnitaOperativaRepository;
import it.govpay.console.security.CurrentOperatorService;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.pendenze.entity.Pendenza;
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
class PosizioneQueryCountIntegrationTest {

    private static final int RIGHE = 6;

    private String iupd;

    @Autowired private PosizioneService service;
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

        PendenzaV3Fixtures.Dati base = new PendenzaV3Fixtures.Dati(applicazione.getId(), dominio.getId(),
                tipo.getId(), tipoDominio.getId(), uo.getId(), "PEND-COUNT-0", null,
                "RSSMRA80A01H501U", 10.0, StatoPendenza.NON_ESEGUITO, 0);
        Pendenza prima = PendenzaV3Fixtures.pendenzaDelSecondoDebitore(em, base, "VRDLGI90B02H501T", false);
        em.flush();
        iupd = "APP-COUNT" + dominio.getCodDominio() + "DOC-PEND-COUNT-0";
        for (int i = 1; i <= RIGHE; i++) {
            PendenzaV3Fixtures.pendenzaNellaStessaPosizione(em, prima, new PendenzaV3Fixtures.Dati(
                    applicazione.getId(), dominio.getId(), tipo.getId(), tipoDominio.getId(), uo.getId(),
                    "PEND-COUNT-" + i, null, "RSSMRA80A01H501U", 10.0, StatoPendenza.NON_ESEGUITO, i));
        }
        em.flush();
    }

    /** Il grafo si legge in blocco: le query non dipendono dal numero di opzioni e pendenze. */
    @Test
    void numeroDiQueryIndipendenteDalNumeroDiOpzioni() {
        long conPoche = contaQuery();
        assertThat(conPoche).as("query della prima lettura").isBetween(5L, 12L);
        // Altre opzioni nella stessa posizione.
        Pendenza qualunque = em.createQuery("select p from Pendenza p where p.idPendenza = 'PEND-COUNT-0'", Pendenza.class)
                .getSingleResult();
        for (int i = 1; i <= RIGHE; i++) {
            PendenzaV3Fixtures.pendenzaNellaStessaPosizione(em, qualunque, new PendenzaV3Fixtures.Dati(
                    qualunque.getIdApplicazione(), qualunque.getIdDominio(), qualunque.getIdTipoVersamento(),
                    qualunque.getIdTipoPendenza(), null, "PEND-COUNT-X" + i, null, "RSSMRA80A01H501U", 10.0,
                    StatoPendenza.NON_ESEGUITO, i));
        }
        em.flush();

        assertThat(contaQuery()).isEqualTo(conPoche);
    }

    private long contaQuery() {
        Statistics statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        em.flush();
        em.clear();
        statistics.clear();
        service.get("APP-COUNT", iupd, new MockHttpServletRequest());
        return statistics.getPrepareStatementCount();
    }
}
