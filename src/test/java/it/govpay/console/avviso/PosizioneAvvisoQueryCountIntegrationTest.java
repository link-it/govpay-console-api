package it.govpay.console.avviso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import java.util.Set;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.entity.Applicazione;
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.IbanAccredito;
import it.govpay.console.entity.TipoVersamento;
import it.govpay.console.entity.TipoVersamentoDominio;
import it.govpay.console.pendenza.PendenzaV3Fixtures;
import it.govpay.console.pendenza.PendenzaV3Fixtures.Dati;
import it.govpay.console.repository.ConsoleApplicazioneRepository;
import it.govpay.console.repository.ConsoleDominioRepository;
import it.govpay.console.repository.IbanAccreditoRepository;
import it.govpay.console.repository.TipoVersamentoDominioRepository;
import it.govpay.console.repository.TipoVersamentoRepository;
import it.govpay.console.security.CurrentOperatorService;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.pendenze.entity.Pendenza;
import it.govpay.pendenze.entity.VocePendenza;
import it.govpay.pendenze.model.StatoPendenza;
import it.govpay.pendenze.model.StatoVocePendenza;
import it.govpay.pendenze.model.TipologiaOpzionePagamento;
import it.govpay.stampe.client.model.PaymentNotice;
import jakarta.persistence.EntityManager;

/**
 * Numero di query della stampa della posizione: voci, IBAN postali e tributi si leggono in
 * blocco, quindi le query non crescono con il numero di rate. Si invoca il servizio con
 * l'operatore gia' risolto (sostituito), cosi' il contatore misura solo la stampa.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {"app.stampe.base-url=http://stampe.mock"})
@Transactional
class PosizioneAvvisoQueryCountIntegrationTest {

    private static final String APP = "APP-AVV-COUNT";
    private static final String DOMINIO = "55555555555";

    @Autowired private PosizioneAvvisoService service;
    @Autowired private EntityManager em;
    @Autowired private ConsoleDominioRepository dominioRepository;
    @Autowired private ConsoleApplicazioneRepository applicazioneRepository;
    @Autowired private TipoVersamentoRepository tipoVersamentoRepository;
    @Autowired private TipoVersamentoDominioRepository tipoVersamentoDominioRepository;
    @Autowired private IbanAccreditoRepository ibanAccreditoRepository;

    @MockitoBean private CurrentOperatorService currentOperatorService;
    @MockitoBean private StampeClient stampeClient;

    private Dominio dominio;
    private Applicazione applicazione;
    private TipoVersamento tipo;
    private TipoVersamentoDominio tipoDominio;
    private int contatore;

    @BeforeEach
    void setup() {
        org.mockito.Mockito.when(currentOperatorService.get()).thenReturn(new OperatoreCorrente(
                "operatore-count", 1L, null, null, true, Set.of(), Set.of(), Set.of(), true, Set.of()));

        dominio = new Dominio();
        dominio.setCodDominio(DOMINIO);
        dominio.setRagioneSociale("Dominio conteggio");
        dominio.setAuxDigit(0);
        dominioRepository.save(dominio);

        applicazione = new Applicazione();
        applicazione.setCodApplicazione(APP);
        applicazioneRepository.save(applicazione);

        tipo = new TipoVersamento();
        tipo.setCodTipoVersamento("TARI");
        tipo.setDescrizione("TARI");
        tipoVersamentoRepository.save(tipo);

        tipoDominio = new TipoVersamentoDominio();
        tipoDominio.setDominio(dominio);
        tipoDominio.setTipoVersamento(tipo);
        tipoVersamentoDominioRepository.save(tipoDominio);
    }

    /** Posizione con un piano rateale di {@code rate} rate, ciascuna con una voce e un IBAN postale proprio. */
    private String posizioneConRate(String id, int rate) {
        Pendenza prima = null;
        for (int i = 1; i <= rate; i++) {
            contatore++;
            Dati dati = new Dati(applicazione.getId(), dominio.getId(), tipo.getId(), tipoDominio.getId(), null,
                    id + "-" + i, String.format("0123456789012%05d", contatore), "RSSMRA80A01H501U", 10.0,
                    StatoPendenza.NON_ESEGUITO, 0);
            Pendenza p = prima == null
                    ? PendenzaV3Fixtures.pendenza(em, dati.conTipologia(TipologiaOpzionePagamento.PIANO_RATEALE))
                    : PendenzaV3Fixtures.rataNellaStessaOpzione(em, prima, dati, i);
            prima = prima == null ? p : prima;

            IbanAccredito iban = new IbanAccredito();
            iban.setCodIban("IT60X05428111010000001" + String.format("%05d", contatore));
            iban.setPostale(true);
            iban.setDominio(dominio);
            ibanAccreditoRepository.save(iban);
            VocePendenza voce = new VocePendenza();
            voce.setIdVocePendenza("V-" + contatore);
            voce.setIndice(1);
            voce.setImporto(10.0);
            voce.setDescrizione("Voce");
            voce.setStato(StatoVocePendenza.NON_ESEGUITO);
            voce.setIdIbanAccredito(iban.getId());
            p.addVocePendenza(voce);
            em.persist(voce);
        }
        return APP + DOMINIO + "DOC-" + id + "-1";
    }

    private long contaQuery(String iupd) {
        Statistics statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        em.flush();
        em.clear();
        statistics.clear();
        service.stampa(APP, iupd, null, new MockHttpServletRequest(), new MockHttpServletResponse());
        return statistics.getPrepareStatementCount();
    }

    @Test
    void numeroDiQueryIndipendenteDalNumeroDiRate() {
        String conUnaRata = posizioneConRate("UNA", 1);
        String conSeiRate = posizioneConRate("SEI", 6);

        long unaRata = contaQuery(conUnaRata);
        long seiRate = contaQuery(conSeiRate);

        assertThat(seiRate).isEqualTo(unaRata);
    }

    @Test
    void ogniRataPortaIlProprioIbanPostale() {
        String iupd = posizioneConRate("IBAN", 3);
        em.flush();
        em.clear();

        service.stampa(APP, iupd, null, new MockHttpServletRequest(), new MockHttpServletResponse());

        ArgumentCaptor<PaymentNotice> captor = ArgumentCaptor.forClass(PaymentNotice.class);
        verify(stampeClient).streamPaymentNotice(captor.capture(), any());
        PaymentNotice notice = captor.getValue();
        assertThat(notice.getPostal()).isTrue();
        assertThat(notice.getInstalments()).hasSize(3);
        assertThat(notice.getInstalments()).extracting(i -> i.getIban().getIbanCode()).doesNotHaveDuplicates();
    }
}
