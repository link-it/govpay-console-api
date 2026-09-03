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
import it.govpay.console.entity.Rpt;
import it.govpay.console.entity.TipoVersamento;
import it.govpay.console.entity.TipoVersamentoDominio;
import it.govpay.console.entity.Utenza;
import it.govpay.console.entity.UtenzaDominio;
import it.govpay.console.entity.Versamento;
import it.govpay.console.model.AclServizio;
import it.govpay.console.repository.AclRepository;
import it.govpay.console.repository.ApplicazioneRepository;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.repository.GpAuditRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.RptRepository;
import it.govpay.console.repository.RtRecuperoRepository;
import it.govpay.console.repository.TipoVersamentoDominioRepository;
import it.govpay.console.repository.TipoVersamentoRepository;
import it.govpay.console.repository.UtenzaDominioRepository;
import it.govpay.console.repository.UtenzaRepository;
import it.govpay.console.repository.VersamentoRepository;

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
    @Autowired private DominioRepository dominioRepository;
    @Autowired private ApplicazioneRepository applicazioneRepository;
    @Autowired private TipoVersamentoRepository tipoVersamentoRepository;
    @Autowired private TipoVersamentoDominioRepository tipoVersamentoDominioRepository;
    @Autowired private VersamentoRepository versamentoRepository;
    @Autowired private RptRepository rptRepository;
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
        Versamento v = newVersamento("PEND-409");
        newRpt(v, "IUV-409", "CCP-409", "<RT/>".getBytes(), OffsetDateTime.now());

        String p = utenteScrittura("u-409-rt");
        mvc.perform(recupero(DOM_A, "IUV-409", "IUR-409", p))
                .andExpect(status().isConflict());

        assertThat(rtRecuperoRepository.findByCodDominioAndIuvAndIur(DOM_A, "IUV-409", "IUR-409")).isEmpty();
    }

    @Test
    void richiestaAccettataScriveLaRigaERisponde202AllScadereDelPoll() throws Exception {
        Versamento v = newVersamento("PEND-202");
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
        Versamento v = newVersamento("PEND-REPEAT");
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
        Versamento v = newVersamento("PEND-REACTIVATE");
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
        Versamento v = newVersamento("PEND-403B");
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
        Versamento v = newVersamento("PEND-AUDIT409");
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

    private Versamento newVersamento(String idPendenza) {
        Versamento v = new Versamento();
        v.setCodVersamentoEnte(idPendenza);
        v.setImportoTotale(100.0);
        v.setImportoPagato(0.0);
        v.setStatoVersamento("ESEGUITO");
        v.setDataCreazione(OffsetDateTime.now());
        v.setDataOraUltimoAggiornamento(OffsetDateTime.now());
        v.setDebitoreIdentificativo("RSSMRA80A01H501U");
        v.setDebitoreAnagrafica("Mario Rossi");
        v.setDebitoreIndirizzo("Via Roma");
        v.setDebitoreCivico("1");
        v.setDebitoreCap("00100");
        v.setDebitoreLocalita("Roma");
        v.setDebitoreProvincia("RM");
        v.setSrcDebitoreIdentificativo("RSSMRA80A01H501U");
        v.setAnomalo(false);
        v.setAck(true);
        v.setTipo("DOVUTO");
        v.setCausaleVersamento("TARI 2026");
        v.setIuvVersamento(idPendenza);
        v.setNumeroAvviso("012345678901234567");
        v.setDominio(domA);
        v.setApplicazione(app);
        v.setTipoVersamento(tv);
        v.setTipoVersamentoDominio(tvd);
        return versamentoRepository.save(v);
    }

    private Rpt newRpt(Versamento v, String iuv, String ccp, byte[] xmlRt, OffsetDateTime dataMsgRicevuta) {
        Rpt r = new Rpt();
        r.setIuv(iuv);
        r.setCcp(ccp);
        r.setCodDominio(DOM_A);
        r.setXmlRt(xmlRt);
        r.setCodEsitoPagamento(0);
        r.setImportoTotalePagato(100.0);
        r.setDataMsgRichiesta(dataMsgRicevuta.minusHours(1));
        r.setDataMsgRicevuta(dataMsgRicevuta);
        r.setVersamento(v);
        r.setVersione("SANP_240");
        r.setStato("RT_ACCETTATA_PA");
        return rptRepository.save(r);
    }

    /** RPT senza RT: e' il caso che il recupero puntuale deve poter agganciare. */
    private Rpt newRptSenzaRt(Versamento v, String iuv, String ccp) {
        Rpt r = new Rpt();
        r.setIuv(iuv);
        r.setCcp(ccp);
        r.setCodDominio(DOM_A);
        r.setDataMsgRichiesta(OffsetDateTime.now());
        r.setVersamento(v);
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
