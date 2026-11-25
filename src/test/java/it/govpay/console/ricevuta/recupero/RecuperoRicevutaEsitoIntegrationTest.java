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
import it.govpay.pendenze.entity.Rpt;
import it.govpay.console.entity.TipoVersamento;
import it.govpay.console.entity.TipoVersamentoDominio;
import it.govpay.console.entity.Utenza;
import it.govpay.pendenze.entity.Pendenza;
import it.govpay.pendenze.model.StatoPendenza;
import it.govpay.pendenze.repository.PosizioneDebitoriaRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import it.govpay.console.pendenza.PendenzaV3Fixtures;
import it.govpay.console.pendenza.PendenzaV3Fixtures.Dati;
import it.govpay.console.config.OperazioniProperties;
import it.govpay.console.config.OperazioniProperties.OperazioneConfig;
import it.govpay.console.model.AclServizio;
import it.govpay.console.operazioni.OperazioneBatchClient;
import it.govpay.console.repository.AclRepository;
import it.govpay.console.repository.ConsoleApplicazioneRepository;
import it.govpay.console.repository.ConsoleDominioRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.ConsoleRptRepository;
import it.govpay.console.repository.RtRecuperoRepository;
import it.govpay.console.repository.TipoVersamentoDominioRepository;
import it.govpay.console.repository.TipoVersamentoRepository;
import it.govpay.console.repository.UtenzaRepository;

/**
 * Test dedicato agli esiti di {@code POST /ricevute/recuperi} che dipendono
 * dall'elaborazione del batch {@code RECUPERO_RT} (issue #59 §H): riga
 * scomparsa → 201, marcata {@code NON_DISPONIBILE} → 404, marcata con un
 * esito sconosciuto → 202. {@code govpay-rt-batch} non esiste ancora in
 * questo repo, quindi la sua elaborazione e' simulata **sincronamente**
 * dentro l'invocazione mockata di {@code OperazioneBatchClient.run()} (vedi
 * {@link #simulaBatch}): nessuna race col poll successivo, deterministico.
 *
 * <p>Non {@code @Transactional}: la classe usa {@code @MockitoBean} e
 * cleanup manuale in {@code @AfterEach} per coerenza con le fixture delle
 * altre classi di test di questo package, non per necessita' di
 * visibilita' cross-thread (la simulazione e' sincrona, stesso thread/
 * transazione della richiesta).
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
    @Autowired private ConsoleDominioRepository dominioRepository;
    @Autowired private ConsoleApplicazioneRepository applicazioneRepository;
    @Autowired private TipoVersamentoRepository tipoVersamentoRepository;
    @Autowired private TipoVersamentoDominioRepository tipoVersamentoDominioRepository;
    @PersistenceContext private EntityManager em;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private PosizioneDebitoriaRepository posizioneDebitoriaRepository;
    @Autowired private ConsoleRptRepository rptRepository;
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
        posizioneDebitoriaRepository.findAll().stream()
                .filter(p -> dominio.getId().equals(p.getIdDominio()))
                .forEach(posizioneDebitoriaRepository::delete);
        tipoVersamentoDominioRepository.delete(tvd);
        tipoVersamentoRepository.delete(tv);
        applicazioneRepository.delete(app);
        dominioRepository.delete(dominio);
    }

    @Test
    void rigaScomparsaRitorna201ConLaRicevuta() throws Exception {
        Pendenza v = newVersamento("PEND-ESITO-201");
        newRptSenzaRt(v, "IUV-ESITO-201", "CCP-ESITO-201");
        // Simula l'acquisizione reale fatta dal batch (core, fuori scope qui):
        // la RT diventa disponibile sulla riga rpt *prima* che la riga di
        // rt_recuperi sparisca, cosi' come nel flusso vero.
        simulaBatch((idDominio, iuv, iur) -> {
            rptRepository.findByDominioAndIuv(idDominio, iuv).ifPresent(rpt -> {
                rpt.setIur(iur);
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
        Pendenza v = newVersamento("PEND-ESITO-404");
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
        Pendenza v = newVersamento("PEND-ESITO-202");
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
        Pendenza v = newVersamento("PEND-ESITO-409");
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

    /**
     * Simula l'elaborazione del batch eseguendola **sincronamente** dentro la
     * chiamata mockata a {@code /run} — non su un thread separato. La riga e'
     * gia' committata a quel punto (il servizio committa prima di chiamare
     * {@code /run}, issue #59 §10), quindi la simulazione la vede e la
     * modifica senza bisogno di alcuna race col poll successivo: il primo
     * controllo del poll la trova gia' nello stato atteso, deterministico.
     *
     * <p>Una versione precedente usava {@code CompletableFuture.runAsync},
     * corretta in locale ma intermittente in CI (più lenta/contesa): il poll
     * timeout di test (50ms) a volte scadeva prima che il thread asincrono
     * completasse. Sincrono elimina la race invece di allargare la finestra.
     */
    private void simulaBatch(AzioneBatch azione) {
        doAnswer(invocation -> {
            azione.elabora(DOM, ultimoIuvRichiesto, ultimoIurRichiesto);
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

    private Pendenza newVersamento(String idPendenza) {
        return new TransactionTemplate(transactionManager).execute(stato -> {
            Pendenza v = PendenzaV3Fixtures.pendenza(em, new Dati(app.getId(), dominio.getId(),
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
        });
    }

    private Rpt newRptSenzaRt(Pendenza v, String iuv, String ccp) {
        Rpt r = new Rpt();
        r.setCodStazione("STAZIONE-TEST");
        r.setCodMsgRichiesta("MSG-RICHIESTA");
        r.setDataAggiornamentoStato(java.time.OffsetDateTime.now());
        r.setIuv(iuv);
        r.setIur(ccp);
        r.setCodDominio(DOM);
        r.setDataMsgRichiesta(OffsetDateTime.now());
        r.setIdVersamento(v.getId());
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
