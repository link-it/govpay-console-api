package it.govpay.console.ricevuta.recupero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.common.auth.GovpayPasswordEncoder;
import it.govpay.console.entity.Acl;
import it.govpay.console.entity.Applicazione;
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.GpAudit;
import it.govpay.console.entity.Operatore;
import it.govpay.pendenze.entity.Rpt;
import it.govpay.console.entity.TipoVersamento;
import it.govpay.console.entity.TipoVersamentoDominio;
import it.govpay.console.entity.Utenza;
import it.govpay.console.entity.UtenzaDominio;
import it.govpay.pendenze.entity.Pendenza;
import it.govpay.pendenze.model.StatoPendenza;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import it.govpay.console.pendenza.PendenzaV3Fixtures;
import it.govpay.console.pendenza.PendenzaV3Fixtures.Dati;
import it.govpay.console.model.AclServizio;
import it.govpay.console.repository.AclRepository;
import it.govpay.console.repository.ConsoleApplicazioneRepository;
import it.govpay.console.repository.ConsoleDominioRepository;
import it.govpay.console.repository.GpAuditRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.ConsoleRptRepository;
import it.govpay.console.repository.RtRecuperoRepository;
import it.govpay.console.repository.TipoVersamentoDominioRepository;
import it.govpay.console.repository.TipoVersamentoRepository;
import it.govpay.console.repository.UtenzaDominioRepository;
import it.govpay.console.repository.UtenzaRepository;

/**
 * Integration test di {@code POST /ricevute/recuperi} (issue #59 §H):
 * pre-flight, ACL, audit, upsert-riuso sulla stessa tripla, e l'esito "presa
 * in carico" naturale nei test (il batch {@code RECUPERO_RT} non e'
 * raggiungibile: nessuna riga viene mai marcata/eliminata, quindi la
 * risposta di default e' 202 allo scadere del poll ridotto di test).
 *
 * <p>I casi che richiedono simulare l'elaborazione del batch (riga
 * scomparsa → 201, marcata {@code NON_DISPONIBILE} → 404) sono in
 * {@link RecuperoRicevutaEsitoIntegrationTest}, deliberatamente non
 * transazionale.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RecuperoRicevutaIntegrationTest {

    private static final String PASSWORD = "secret";
    private static final String DOM_A = "44444444444";
    private static final String DOM_B = "55555555555";
    private static final String APP_COD = "APP-RECUPERO";

    @Autowired private MockMvc mvc;
    @Autowired private GovpayPasswordEncoder encoder;
    @Autowired private UtenzaRepository utenzaRepository;
    @Autowired private UtenzaDominioRepository utenzaDominioRepository;
    @Autowired private OperatoreRepository operatoreRepository;
    @Autowired private ConsoleDominioRepository dominioRepository;
    @Autowired private ConsoleApplicazioneRepository applicazioneRepository;
    @Autowired private TipoVersamentoRepository tipoVersamentoRepository;
    @Autowired private TipoVersamentoDominioRepository tipoVersamentoDominioRepository;
    @PersistenceContext private EntityManager em;
    @Autowired private ConsoleRptRepository rptRepository;
    @Autowired private RtRecuperoRepository rtRecuperoRepository;
    @Autowired private AclRepository aclRepository;
    @Autowired private GpAuditRepository gpAuditRepository;
    @Autowired private it.govpay.console.config.OperazioniProperties operazioniProperties;

    private Dominio domA;
    private Dominio domB;
    private Applicazione app;
    private TipoVersamento tv;
    private TipoVersamentoDominio tvd;

    @BeforeEach
    void setup() {
        // OperazioniProperties e' un bean singleton condiviso fra le classi di
        // test: altri test (es. RiconciliazionePutHookIntegrationTest) svuotano
        // il catalogo senza ripristinarlo. Va quindi censita esplicitamente
        // qui, non assunta dal default di application.properties.
        it.govpay.console.config.OperazioniProperties.OperazioneConfig config =
                new it.govpay.console.config.OperazioniProperties.OperazioneConfig();
        config.setId(RecuperoRicevutaService.ID_OPERAZIONE_RECUPERO_RT);
        config.setUrl("http://rt-batch-mock:8080/api/batch");
        config.setAbilitata(true);
        operazioniProperties.setCatalogo(java.util.List.of(config));

        domA = newDominio(DOM_A, "Dominio A");
        domB = newDominio(DOM_B, "Dominio B");

        app = new Applicazione();
        app.setCodApplicazione(APP_COD);
        applicazioneRepository.save(app);

        tv = new TipoVersamento();
        tv.setCodTipoVersamento("TARI");
        tv.setDescrizione("TARI");
        tipoVersamentoRepository.save(tv);

        tvd = new TipoVersamentoDominio();
        tvd.setDominio(domA);
        tvd.setTipoVersamento(tv);
        tipoVersamentoDominioRepository.save(tvd);
    }

    // ----- pre-flight --------------------------------------------------------------

    @Test
    void pagamentoInesistenteRitorna404() throws Exception {
        String p = utenteScrittura("u-404-pag");
        mvc.perform(recupero(DOM_A, "IUV-INESISTENTE", "IUR-1", p))
                .andExpect(status().isNotFound());

        assertThat(rtRecuperoRepository.findByCodDominioAndIuvAndIur(DOM_A, "IUV-INESISTENTE", "IUR-1")).isEmpty();
        GpAudit riga = ultimoAudit("IUV-INESISTENTE");
        assertThat(riga.getOggetto()).contains("\"esito\":\"404\"");
    }

    @Test
    void rtGiaAcquisitaRitorna409() throws Exception {
        Pendenza v = newVersamento("PEND-409");
        newRpt(v, "IUV-409", "CCP-409", "<RT/>".getBytes(), OffsetDateTime.now());

        String p = utenteScrittura("u-409-rt");
        mvc.perform(recupero(DOM_A, "IUV-409", "IUR-409", p))
                .andExpect(status().isConflict());

        assertThat(rtRecuperoRepository.findByCodDominioAndIuvAndIur(DOM_A, "IUV-409", "IUR-409")).isEmpty();
    }

    @Test
    void richiestaAccettataScriveLaRigaERisponde202AllScadereDelPoll() throws Exception {
        Pendenza v = newVersamento("PEND-202");
        newRptSenzaRt(v, "IUV-202", "CCP-202");

        String p = utenteScrittura("u-202");
        mvc.perform(recupero(DOM_A, "IUV-202", "IUR-202", p))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", endsWith("/ricevute/" + DOM_A + "/IUV-202/IUR-202")));

        var righe = rtRecuperoRepository.findByCodDominioAndIuvAndIur(DOM_A, "IUV-202", "IUR-202");
        assertThat(righe).hasSize(1);
        var riga = righe.get(0);
        assertThat(riga.getEsito()).isNull();
        assertThat(riga.getDataRichiesta()).isNotNull();
        assertThat(riga.getIdOperatore()).isNotNull();
    }

    /**
     * Issue #59 §10 / #17 §A: righe ancora pendenti (esito IS NULL) NON vanno
     * deduplicate. Due richieste sulla stessa tripla, entrambe non ancora
     * elaborate dal batch, producono due righe indipendenti.
     */
    @Test
    void richiesteRipetuteSuTriplaAncoraPendenteCreanoRigheIndipendenti() throws Exception {
        Pendenza v = newVersamento("PEND-REPEAT");
        newRptSenzaRt(v, "IUV-REPEAT", "CCP-REPEAT");

        String p = utenteScrittura("u-repeat");
        mvc.perform(recupero(DOM_A, "IUV-REPEAT", "IUR-REPEAT", p)).andExpect(status().isAccepted());
        mvc.perform(recupero(DOM_A, "IUV-REPEAT", "IUR-REPEAT", p)).andExpect(status().isAccepted());

        var righe = rtRecuperoRepository.findByCodDominioAndIuvAndIur(DOM_A, "IUV-REPEAT", "IUR-REPEAT");
        assertThat(righe).hasSize(2);
        assertThat(righe).allMatch(r -> r.getEsito() == null);
    }

    /**
     * Issue #59 §10 / #17 §A: una riga già MARCATA viene riattivata (stesso
     * id, esito riportato a NULL) invece di accumularsi a ogni tentativo.
     */
    @Test
    void richiestaSuTriplaConRigaMarcataRiattivaLaRigaEsistente() throws Exception {
        Pendenza v = newVersamento("PEND-REACTIVATE");
        newRptSenzaRt(v, "IUV-REACTIVATE", "CCP-REACTIVATE");

        var marcata = new it.govpay.console.entity.RtRecupero();
        marcata.setCodDominio(DOM_A);
        marcata.setIuv("IUV-REACTIVATE");
        marcata.setIur("IUR-REACTIVATE");
        marcata.setDataRichiesta(OffsetDateTime.now().minusDays(1));
        marcata.setEsito("NON_DISPONIBILE");
        marcata.setDataUltimoTentativo(OffsetDateTime.now().minusDays(1));
        Long idMarcata = rtRecuperoRepository.save(marcata).getId();

        String p = utenteScrittura("u-reactivate");
        mvc.perform(recupero(DOM_A, "IUV-REACTIVATE", "IUR-REACTIVATE", p)).andExpect(status().isAccepted());

        var righe = rtRecuperoRepository.findByCodDominioAndIuvAndIur(DOM_A, "IUV-REACTIVATE", "IUR-REACTIVATE");
        assertThat(righe).hasSize(1);
        assertThat(righe.get(0).getId()).isEqualTo(idMarcata);
        assertThat(righe.get(0).getEsito()).isNull();
    }

    // ----- ACL --------------------------------------------------------------------

    @Test
    void aclScritturaNegataRitorna403() throws Exception {
        String p = utenteSoloLettura("u-403-servizio");
        mvc.perform(recupero(DOM_A, "IUV-403A", "IUR-403A", p))
                .andExpect(status().isForbidden());
    }

    @Test
    void dominioNonVisibileRitorna403() throws Exception {
        Pendenza v = newVersamento("PEND-403B");
        newRptSenzaRt(v, "IUV-403B", "CCP-403B");

        String p = utenteScritturaSoloDominio("u-403-dominio", domB);
        mvc.perform(recupero(DOM_A, "IUV-403B", "IUR-403B", p))
                .andExpect(status().isForbidden());
    }

    // ----- validazione body ---------------------------------------------------------

    @Test
    void bodySenzaIdRicevutaRitorna400() throws Exception {
        String p = utenteScrittura("u-400");
        mvc.perform(post("/ricevute/recuperi")
                        .with(httpBasic(p, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idDominio": "%s", "iuv": "IUV-400"}""".formatted(DOM_A)))
                .andExpect(status().isBadRequest());
    }

    // ----- audit ---------------------------------------------------------------------

    @Test
    void auditRegistratoAncheSuiRifiutiDiPreFlight() throws Exception {
        String p = utenteScrittura("u-audit-409");
        Pendenza v = newVersamento("PEND-AUDIT409");
        newRpt(v, "IUV-AUDIT409", "CCP-AUDIT409", "<RT/>".getBytes(), OffsetDateTime.now());

        mvc.perform(recupero(DOM_A, "IUV-AUDIT409", "IUR-AUDIT409", p))
                .andExpect(status().isConflict());

        GpAudit riga = ultimoAudit("IUV-AUDIT409");
        assertThat(riga.getOggetto()).contains("\"esito\":\"409\"");
    }

    // ----- helpers HTTP --------------------------------------------------------------

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder recupero(
            String idDominio, String iuv, String idRicevuta, String principal) {
        return post("/ricevute/recuperi")
                .with(httpBasic(principal, PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"idDominio": "%s", "iuv": "%s", "idRicevuta": "%s"}""".formatted(idDominio, iuv, idRicevuta));
    }

    private GpAudit ultimoAudit(String iuv) {
        return gpAuditRepository.findAll().stream()
                .filter(a -> "RICEVUTA_RECUPERA".equals(a.getTipoOggetto())
                        && a.getOggetto() != null && a.getOggetto().contains("\"iuv\":\"" + iuv + "\""))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("Nessuna riga di audit trovata per iuv=" + iuv));
    }

    // ----- fixture helpers -------------------------------------------------------------

    private Dominio newDominio(String cod, String ragione) {
        Dominio d = new Dominio();
        d.setCodDominio(cod);
        d.setRagioneSociale(ragione);
        d.setAuxDigit(0);
        return dominioRepository.save(d);
    }

    private Pendenza newVersamento(String idPendenza) {
        Pendenza v = PendenzaV3Fixtures.pendenza(em, new Dati(app.getId(), domA.getId(),
                tv.getId(), tvd.getId(), null, idPendenza, "012345678901234567", "RSSMRA80A01H501U", 100.0,
                StatoPendenza.ESEGUITO, 0));
        v.setImportoPagato(0.0);
        v.setDataCreazione(OffsetDateTime.now());
        v.setDataUltimoAggiornamento(OffsetDateTime.now());
        v.getOpzionePagamento().getPdDebitore().getDebitore().setAnagrafica("Mario Rossi");
        v.getOpzionePagamento().getPdDebitore().getDebitore().setIndirizzo("Via Roma");
        v.getOpzionePagamento().getPdDebitore().getDebitore().setCivico("1");
        v.getOpzionePagamento().getPdDebitore().getDebitore().setCap("00100");
        v.getOpzionePagamento().getPdDebitore().getDebitore().setLocalita("Roma");
        v.getOpzionePagamento().getPdDebitore().getDebitore().setProvincia("RM");
        v.setCausaleVersamento("TARI 2026");
        v.setIuv(idPendenza);
        return v;
    }

    private Rpt newRpt(Pendenza v, String iuv, String ccp, byte[] xmlRt, OffsetDateTime dataMsgRicevuta) {
        Rpt r = new Rpt();
        r.setCodStazione("STAZIONE-TEST");
        r.setCodMsgRichiesta("MSG-RICHIESTA");
        r.setDataAggiornamentoStato(java.time.OffsetDateTime.now());
        r.setIuv(iuv);
        r.setIur(ccp);
        r.setCodDominio(DOM_A);
        r.setXmlRt(xmlRt);
        r.setCodEsitoPagamento(0);
        r.setImportoTotalePagato(100.0);
        r.setDataMsgRichiesta(dataMsgRicevuta.minusHours(1));
        r.setDataMsgRicevuta(dataMsgRicevuta);
        r.setIdVersamento(v.getId());
        r.setVersione("SANP_240");
        r.setStato("RT_ACCETTATA_PA");
        return rptRepository.save(r);
    }

    /** RPT senza RT: e' il caso che il recupero puntuale deve poter agganciare. */
    private Rpt newRptSenzaRt(Pendenza v, String iuv, String ccp) {
        Rpt r = new Rpt();
        r.setCodStazione("STAZIONE-TEST");
        r.setCodMsgRichiesta("MSG-RICHIESTA");
        r.setDataAggiornamentoStato(java.time.OffsetDateTime.now());
        r.setIuv(iuv);
        r.setIur(ccp);
        r.setCodDominio(DOM_A);
        r.setDataMsgRichiesta(OffsetDateTime.now());
        r.setIdVersamento(v.getId());
        r.setVersione("SANP_240");
        r.setStato("RT_MANCANTE");
        return rptRepository.save(r);
    }

    private String utenteScrittura(String principal) {
        Utenza u = baseUtenza(principal, true);
        utenzaRepository.save(u);
        attachOperatore(principal, u);
        grant(u, "RW");
        return principal;
    }

    private String utenteScritturaSoloDominio(String principal, Dominio dominio) {
        Utenza u = baseUtenza(principal, false);
        utenzaRepository.save(u);
        attachOperatore(principal, u);
        grant(u, "RW");
        UtenzaDominio link = new UtenzaDominio();
        link.setIdUtenza(u.getId());
        link.setIdDominio(dominio.getId());
        utenzaDominioRepository.save(link);
        return principal;
    }

    private String utenteSoloLettura(String principal) {
        Utenza u = baseUtenza(principal, true);
        utenzaRepository.save(u);
        attachOperatore(principal, u);
        grant(u, "R");
        return principal;
    }

    private void grant(Utenza utenza, String diritti) {
        Acl acl = new Acl();
        acl.setIdUtenza(utenza.getId());
        acl.setServizio(AclServizio.PAGAMENTI.getValue());
        acl.setDiritti(diritti);
        aclRepository.save(acl);
    }

    private Utenza baseUtenza(String principal, boolean tuttiDomini) {
        Utenza u = new Utenza();
        u.setPrincipal(principal);
        u.setPrincipalOriginale(principal);
        u.setAbilitato(true);
        u.setAutorizzazioneDominiStar(tuttiDomini);
        u.setAutorizzazioneTipiVersStar(true);
        u.setRuoli("OPERATORE");
        u.setPassword(encoder.encode(PASSWORD));
        return u;
    }

    private void attachOperatore(String nome, Utenza u) {
        Operatore op = new Operatore();
        op.setNome(nome);
        op.setIdUtenza(u.getId());
        operatoreRepository.save(op);
    }
}
