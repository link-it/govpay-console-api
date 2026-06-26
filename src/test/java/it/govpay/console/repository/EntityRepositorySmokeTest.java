package it.govpay.console.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import it.govpay.console.entity.Applicazione;
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.GpAudit;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.SingoloVersamento;
import it.govpay.console.entity.TipoVersamento;
import it.govpay.console.entity.TipoVersamentoDominio;
import it.govpay.console.entity.UnitaOperativa;
import it.govpay.console.entity.Versamento;

@DataJpaTest
@ActiveProfiles("test")
class EntityRepositorySmokeTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private VersamentoRepository versamentoRepository;

    @Autowired
    private GpAuditRepository gpAuditRepository;

    @Test
    void persistAndLoadFullGraph() {
        Dominio dominio = new Dominio();
        dominio.setCodDominio("12345678901");
        dominio.setRagioneSociale("Comune di Test");
        dominio.setAuxDigit(0);
        em.persist(dominio);

        Applicazione applicazione = new Applicazione();
        applicazione.setCodApplicazione("APP-TEST");
        em.persist(applicazione);

        TipoVersamento tipoVersamento = new TipoVersamento();
        tipoVersamento.setCodTipoVersamento("TARI");
        tipoVersamento.setDescrizione("Tassa rifiuti");
        em.persist(tipoVersamento);

        TipoVersamentoDominio tipoVersamentoDominio = new TipoVersamentoDominio();
        tipoVersamentoDominio.setDominio(dominio);
        tipoVersamentoDominio.setTipoVersamento(tipoVersamento);
        em.persist(tipoVersamentoDominio);

        UnitaOperativa uo = new UnitaOperativa();
        uo.setCodUo("UO1");
        uo.setUoDenominazione("Ufficio Tributi");
        uo.setDominio(dominio);
        em.persist(uo);

        Versamento versamento = new Versamento();
        versamento.setCodVersamentoEnte("PEND-0001");
        versamento.setImportoTotale(123.45);
        versamento.setStatoVersamento("NON_ESEGUITO");
        versamento.setDataCreazione(OffsetDateTime.now());
        versamento.setDataOraUltimoAggiornamento(OffsetDateTime.now());
        versamento.setDebitoreIdentificativo("RSSMRA80A01H501U");
        versamento.setDebitoreAnagrafica("Mario Rossi");
        versamento.setSrcDebitoreIdentificativo("RSSMRA80A01H501U");
        versamento.setImportoPagato(0.0);
        versamento.setAnomalo(false);
        versamento.setAck(false);
        versamento.setTipo("DOVUTO");
        versamento.setDominio(dominio);
        versamento.setApplicazione(applicazione);
        versamento.setTipoVersamento(tipoVersamento);
        versamento.setTipoVersamentoDominio(tipoVersamentoDominio);
        versamento.setUnitaOperativa(uo);
        em.persist(versamento);

        SingoloVersamento voce = new SingoloVersamento();
        voce.setCodSingoloVersamentoEnte("VOCE-0001");
        voce.setStatoSingoloVersamento("NON_ESEGUITO");
        voce.setImportoSingoloVersamento(123.45);
        voce.setIndiceDati(1);
        voce.setVersamento(versamento);
        em.persist(voce);

        em.flush();
        em.clear();

        Versamento loaded = versamentoRepository.findById(versamento.getId()).orElseThrow();
        assertThat(loaded.getCodVersamentoEnte()).isEqualTo("PEND-0001");
        assertThat(loaded.getDominio().getRagioneSociale()).isEqualTo("Comune di Test");
        assertThat(loaded.getApplicazione().getCodApplicazione()).isEqualTo("APP-TEST");
        assertThat(loaded.getTipoVersamento().getDescrizione()).isEqualTo("Tassa rifiuti");
        assertThat(loaded.getUnitaOperativa().getCodUo()).isEqualTo("UO1");
        assertThat(loaded.getSingoliVersamenti()).hasSize(1);
        assertThat(loaded.getSingoliVersamenti().get(0).getCodSingoloVersamentoEnte()).isEqualTo("VOCE-0001");
    }

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
