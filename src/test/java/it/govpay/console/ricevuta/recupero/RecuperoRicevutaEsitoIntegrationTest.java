package it.govpay.console.ricevuta.recupero;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import it.govpay.common.auth.GovpayPasswordEncoder;
import it.govpay.console.entity.Acl;
import it.govpay.console.entity.Applicazione;
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Rpt;
import it.govpay.console.entity.TipoVersamento;
import it.govpay.console.entity.TipoVersamentoDominio;
import it.govpay.console.entity.Utenza;
import it.govpay.console.entity.Versamento;
import it.govpay.console.config.OperazioniProperties;
import it.govpay.console.config.OperazioniProperties.OperazioneConfig;
import it.govpay.console.model.AclServizio;
import it.govpay.console.operazioni.OperazioneBatchClient;
import it.govpay.console.repository.AclRepository;
import it.govpay.console.repository.ApplicazioneRepository;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.RptRepository;
import it.govpay.console.repository.RtRecuperoRepository;
import it.govpay.console.repository.TipoVersamentoDominioRepository;
import it.govpay.console.repository.TipoVersamentoRepository;
import it.govpay.console.repository.UtenzaRepository;
import it.govpay.console.repository.VersamentoRepository;

/**
 * Test dedicato agli esiti di {@code POST /ricevute/recuperi} che dipendono
 * dall'elaborazione del batch {@code RECUPERO_RT} (issue #59 §H): riga
 * scomparsa → 201, marcata {@code NON_DISPONIBILE} → 404, marcata con un
 * esito sconosciuto → 202. {@code govpay-rt-batch} non esiste ancora in
 * questo repo, quindi la sua elaborazione e' simulata in un thread separato,
 * innescato dall'invocazione mockata di {@code OperazioneBatchClient.run()}.
 *
 * <p>Volutamente **non** {@code @Transactional}: il thread che simula il
 * batch deve vedere davvero la riga scritta dalla richiesta (connessione/
 * transazione diversa), cosa che il rollback di fine metodo di un test
 * {@code @Transactional} impedirebbe. Cleanup manuale in {@code @AfterEach}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecuperoRicevutaEsitoIntegrationTest {

    private static final String PASSWORD = "secret";
    private static final String DOM = "66666666666";
    private static final String APP_COD = "APP-RECUPERO-ESITO";

    @Autowired private MockMvc mvc;
    @Autowired private GovpayPasswordEncoder encoder;
    @Autowired private UtenzaRepository utenzaRepository;
    @Autowired private OperatoreRepository operatoreRepository;
    @Autowired private DominioRepository dominioRepository;
    @Autowired private ApplicazioneRepository applicazioneRepository;
    @Autowired private TipoVersamentoRepository tipoVersamentoRepository;
    @Autowired private TipoVersamentoDominioRepository tipoVersamentoDominioRepository;
    @Autowired private VersamentoRepository versamentoRepository;
    @Autowired private RptRepository rptRepository;
    @Autowired private RtRecuperoRepository rtRecuperoRepository;
    @Autowired private AclRepository aclRepository;
    @Autowired private OperazioniProperties operazioniProperties;

    @MockitoBean private OperazioneBatchClient operazioneBatchClient;

    private Dominio dominio;
    private Applicazione app;
    private TipoVersamento tv;
    private TipoVersamentoDominio tvd;

    @BeforeEach
    void setup() {
        // OperazioniProperties e' un bean singleton condiviso: altri test
        // (es. RiconciliazionePutHookIntegrationTest) svuotano il catalogo
        // senza ripristinarlo. Va quindi censita esplicitamente qui, non
        // assunta dal default di application.properties.
        OperazioneConfig config = new OperazioneConfig();
        config.setId(RecuperoRicevutaService.ID_OPERAZIONE_RECUPERO_RT);
        config.setUrl("http://rt-batch-mock:8080/api/batch");
        config.setAbilitata(true);
        operazioniProperties.setCatalogo(java.util.List.of(config));

        dominio = new Dominio();
        dominio.setCodDominio(DOM);
        dominio.setRagioneSociale("Dominio Esito");
        dominio.setAuxDigit(0);
        dominioRepository.save(dominio);

        app = new Applicazione();
        app.setCodApplicazione(APP_COD);
        applicazioneRepository.save(app);

        tv = new TipoVersamento();
        tv.setCodTipoVersamento("TARI");
        tv.setDescrizione("TARI");
        tipoVersamentoRepository.save(tv);

        tvd = new TipoVersamentoDominio();
        tvd.setDominio(dominio);
        tvd.setTipoVersamento(tv);
        tipoVersamentoDominioRepository.save(tvd);
    }

    @AfterEach
    void cleanup() {
        rtRecuperoRepository.findByCodDominioAndIuvAndIur(DOM, "IUV-ESITO-201", "IUR-201")
                .forEach(rtRecuperoRepository::delete);
        rtRecuperoRepository.findByCodDominioAndIuvAndIur(DOM, "IUV-ESITO-404", "IUR-404")
                .forEach(rtRecuperoRepository::delete);
        rtRecuperoRepository.findByCodDominioAndIuvAndIur(DOM, "IUV-ESITO-202", "IUR-202")
                .forEach(rtRecuperoRepository::delete);
        rtRecuperoRepository.findByCodDominioAndIuvAndIur(DOM, "IUV-ESITO-409", "IUR-409")
                .forEach(rtRecuperoRepository::delete);
        rptRepository.findAll().stream()
                .filter(r -> DOM.equals(r.getCodDominio()))
                .forEach(rptRepository::delete);
        versamentoRepository.findAll().stream()
                .filter(v -> dominio.getId().equals(v.getDominio().getId()))
                .forEach(versamentoRepository::delete);
        tipoVersamentoDominioRepository.delete(tvd);
        tipoVersamentoRepository.delete(tv);
        applicazioneRepository.delete(app);
        dominioRepository.delete(dominio);
    }

    @Test
    void rigaScomparsaRitorna201ConLaRicevuta() throws Exception {
        Versamento v = newVersamento("PEND-ESITO-201");
        newRptSenzaRt(v, "IUV-ESITO-201", "CCP-ESITO-201");
        // Simula l'acquisizione reale fatta dal batch (core, fuori scope qui):
        // la RT diventa disponibile sulla riga rpt *prima* che la riga di
        // rt_recuperi sparisca, cosi' come nel flusso vero.
        simulaBatch((idDominio, iuv, iur) -> {
            rptRepository.findByDominioAndIuv(idDominio, iuv).ifPresent(rpt -> {
                rpt.setCcp(iur);
                rpt.setXmlRt("<RT/>".getBytes());
                rpt.setDataMsgRicevuta(OffsetDateTime.now());
                rptRepository.save(rpt);
            });
            rtRecuperoRepository.findByCodDominioAndIuvAndIur(idDominio, iuv, iur)
                    .forEach(rtRecuperoRepository::delete);
        });

        String p = utenteScrittura("u-esito-201");
        mvc.perform(recupero(DOM, "IUV-ESITO-201", "IUR-201", p))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/ricevute/" + DOM + "/IUV-ESITO-201/IUR-201")))
                .andExpect(jsonPath("$.idDominio", org.hamcrest.Matchers.is(DOM)))
                .andExpect(jsonPath("$.iuv", org.hamcrest.Matchers.is("IUV-ESITO-201")));
    }

    @Test
    void rigaMarcataNonDisponibileRitorna404() throws Exception {
        Versamento v = newVersamento("PEND-ESITO-404");
        newRptSenzaRt(v, "IUV-ESITO-404", "CCP-ESITO-404");
        simulaBatch((idDominio, iuv, iur) -> rtRecuperoRepository
                .findByCodDominioAndIuvAndIur(idDominio, iuv, iur)
                .forEach(riga -> {
                    riga.setEsito("NON_DISPONIBILE");
                    riga.setDataUltimoTentativo(OffsetDateTime.now());
                    rtRecuperoRepository.save(riga);
                }));

        String p = utenteScrittura("u-esito-404");
        mvc.perform(recupero(DOM, "IUV-ESITO-404", "IUR-404", p))
                .andExpect(status().isNotFound());
    }

    @Test
    void rigaConEsitoSconosciutoRitorna202NonErrore() throws Exception {
        Versamento v = newVersamento("PEND-ESITO-202");
        newRptSenzaRt(v, "IUV-ESITO-202", "CCP-ESITO-202");
        simulaBatch((idDominio, iuv, iur) -> rtRecuperoRepository
                .findByCodDominioAndIuvAndIur(idDominio, iuv, iur)
                .forEach(riga -> {
                    riga.setEsito("ERRORE_NON_ANCORA_DEFINITO");
                    rtRecuperoRepository.save(riga);
                }));

        String p = utenteScrittura("u-esito-202");
        mvc.perform(recupero(DOM, "IUV-ESITO-202", "IUR-202", p))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/ricevute/" + DOM + "/IUV-ESITO-202/IUR-202")));
    }

    /**
     * Issue #59 §H, review: un 409 da {@code /run} (batch gia' in esecuzione,
     * {@code ConflictException} da {@code OperazioneBatchClient}) non deve
     * diventare la risposta dell'endpoint. La riga resta comunque scritta e
     * si prosegue col poll: qui nessun batch reale la elabora, quindi l'esito
     * atteso e' 202 (presa in carico), non 409.
     */
    @Test
    void batchGiaInEsecuzioneNonRitorna409() throws Exception {
        Versamento v = newVersamento("PEND-ESITO-409");
        newRptSenzaRt(v, "IUV-ESITO-409", "CCP-ESITO-409");
        doThrow(new it.govpay.console.web.ConflictException("batch gia' in esecuzione"))
                .when(operazioneBatchClient).run(anyString(), anyBoolean());

        String p = utenteScrittura("u-esito-409");
        mvc.perform(recupero(DOM, "IUV-ESITO-409", "IUR-409", p))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/ricevute/" + DOM + "/IUV-ESITO-409/IUR-409")));
    }

    // ----- simulazione batch ----------------------------------------------------------

    @FunctionalInterface
    private interface AzioneBatch {
        void elabora(String idDominio, String iuv, String iur);
    }

    /** Innesca l'azione appena il controller chiama {@code /run}, su un thread separato (connessione diversa). */
    private void simulaBatch(AzioneBatch azione) {
        doAnswer(invocation -> {
            CompletableFuture.runAsync(() -> azione.elabora(DOM, ultimoIuvRichiesto, ultimoIurRichiesto));
            return null;
        }).when(operazioneBatchClient).run(anyString(), anyBoolean());
    }

    // La tripla e' nota al momento della chiamata HTTP: il mock non la riceve
    // (run() prende solo url/force), quindi la teniamo qui per il thread di simulazione.
    private String ultimoIuvRichiesto;
    private String ultimoIurRichiesto;

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder recupero(
            String idDominio, String iuv, String idRicevuta, String principal) {
        this.ultimoIuvRichiesto = iuv;
        this.ultimoIurRichiesto = idRicevuta;
        return post("/ricevute/recuperi")
                .with(httpBasic(principal, PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"idDominio": "%s", "iuv": "%s", "idRicevuta": "%s"}""".formatted(idDominio, iuv, idRicevuta));
    }

    // ----- fixture helpers -------------------------------------------------------------

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
        v.setDominio(dominio);
        v.setApplicazione(app);
        v.setTipoVersamento(tv);
        v.setTipoVersamentoDominio(tvd);
        return versamentoRepository.save(v);
    }

    private Rpt newRptSenzaRt(Versamento v, String iuv, String ccp) {
        Rpt r = new Rpt();
        r.setIuv(iuv);
        r.setCcp(ccp);
        r.setCodDominio(DOM);
        r.setDataMsgRichiesta(OffsetDateTime.now());
        r.setVersamento(v);
        r.setVersione("SANP_240");
        r.setStato("RT_MANCANTE");
        return rptRepository.save(r);
    }

    private String utenteScrittura(String principal) {
        Utenza u = new Utenza();
        u.setPrincipal(principal);
        u.setPrincipalOriginale(principal);
        u.setAbilitato(true);
        u.setAutorizzazioneDominiStar(true);
        u.setAutorizzazioneTipiVersStar(true);
        u.setRuoli("OPERATORE");
        u.setPassword(encoder.encode(PASSWORD));
        utenzaRepository.save(u);

        Operatore op = new Operatore();
        op.setNome(principal);
        op.setIdUtenza(u.getId());
        operatoreRepository.save(op);

        Acl acl = new Acl();
        acl.setIdUtenza(u.getId());
        acl.setServizio(AclServizio.PAGAMENTI.getValue());
        acl.setDiritti("RW");
        aclRepository.save(acl);

        return principal;
    }
}
