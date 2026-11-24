package it.govpay.console.avviso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.io.OutputStream;

import it.govpay.console.entity.Applicazione;
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.Intermediario;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Stazione;
import it.govpay.console.entity.TipoVersamento;
import it.govpay.console.entity.TipoVersamentoDominio;
import it.govpay.console.entity.Utenza;
import it.govpay.console.repository.ConsoleApplicazioneRepository;
import it.govpay.console.repository.ConsoleDominioRepository;
import it.govpay.console.repository.IbanAccreditoRepository;
import it.govpay.console.repository.TipoTributoRepository;
import it.govpay.console.repository.TributoRepository;
import it.govpay.console.repository.IntermediarioRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.StazioneRepository;
import it.govpay.console.repository.TipoVersamentoDominioRepository;
import it.govpay.console.repository.TipoVersamentoRepository;
import it.govpay.console.repository.UnitaOperativaRepository;
import it.govpay.console.repository.UtenzaDominioRepository;
import it.govpay.console.entity.UtenzaTipoVersamento;
import it.govpay.console.repository.UtenzaRepository;
import it.govpay.console.repository.UtenzaTipoVersamentoRepository;
import it.govpay.common.auth.GovpayPasswordEncoder;
import it.govpay.console.pendenza.PendenzaV3Fixtures;
import it.govpay.console.pendenza.PendenzaV3Fixtures.Dati;
import it.govpay.pendenze.entity.Pendenza;
import it.govpay.pendenze.entity.VocePendenza;
import it.govpay.pendenze.model.StatoPendenza;
import it.govpay.pendenze.model.StatoVocePendenza;
import it.govpay.pendenze.model.TipologiaOpzionePagamento;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import it.govpay.stampe.client.model.CdsViolation;
import it.govpay.stampe.client.model.PaymentNotice;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"app.stampe.base-url=http://stampe.mock"})
@Transactional
class PosizioneAvvisoIntegrationTest {

    private static final String PRINCIPAL = "operatore-avv-pos";
    private static final String PASSWORD = "secret";
    private static final String APP_COD = "APP-POS";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private GovpayPasswordEncoder encoder;
    @Autowired
    private UtenzaRepository utenzaRepository;
    @Autowired
    private UtenzaDominioRepository utenzaDominioRepository;
    @Autowired
    private OperatoreRepository operatoreRepository;
    @Autowired
    private ConsoleDominioRepository dominioRepository;
    @Autowired
    private StazioneRepository stazioneRepository;
    @Autowired
    private IntermediarioRepository intermediarioRepository;
    @Autowired
    private ConsoleApplicazioneRepository applicazioneRepository;
    @Autowired
    private TipoVersamentoRepository tipoVersamentoRepository;
    @Autowired
    private TipoVersamentoDominioRepository tipoVersamentoDominioRepository;
    @PersistenceContext
    private EntityManager em;
    @MockitoBean
    private StampeClient stampeClient;

    @Autowired
    private UtenzaTipoVersamentoRepository utenzaTipoVersamentoRepository;

    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-', '1', '.', '4'};

    /**
     * Helper: scrive i {@code bytes} nello OutputStream passato come secondo
     * argomento di {@code streamPaymentNotice(PaymentNotice, OutputStream)}.
     */
    private static Answer<Void> writePdf(byte[] bytes) {
        return invocation -> {
            OutputStream out = invocation.getArgument(1);
            out.write(bytes);
            return null;
        };
    }

    private Dominio domA;
    private TipoVersamento tv;
    private TipoVersamentoDominio tvd;
    private Applicazione app;

    @BeforeEach
    void setup() {
        Utenza utenza = new Utenza();
        utenza.setPrincipal(PRINCIPAL);
        utenza.setPrincipalOriginale(PRINCIPAL);
        utenza.setAbilitato(true);
        utenza.setAutorizzazioneDominiStar(true);
        utenza.setAutorizzazioneTipiVersStar(true);
        utenza.setRuoli("OPERATORE");
        utenza.setPassword(encoder.encode(PASSWORD));
        utenzaRepository.save(utenza);

        Operatore op = new Operatore();
        op.setNome("Operatore");
        op.setIdUtenza(utenza.getId());
        operatoreRepository.save(op);

        Intermediario intermediario = new Intermediario();
        intermediario.setCodIntermediario("INT-AVV");
        intermediario.setDenominazione("Intermediario Avviso");
        intermediario.setPrincipal("p");
        intermediario.setPrincipalOriginale("p");
        intermediario.setCodConnettorePdd("C");
        intermediario.setAbilitato(true);
        intermediarioRepository.save(intermediario);

        Stazione stazione = new Stazione();
        stazione.setCodStazione("STZ-1");
        stazione.setApplicationCode(7);
        stazione.setPassword("");
        stazione.setAbilitato(true);
        stazione.setVersione("V2");
        stazione.setIntermediario(intermediario);
        stazioneRepository.save(stazione);

        domA = new Dominio();
        domA.setCodDominio("12345678901");
        domA.setRagioneSociale("Comune di Test");
        domA.setAuxDigit(0);
        domA.setGln("0123456789012");
        domA.setStazione(stazione);
        dominioRepository.save(domA);

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

    private Dati dati(String idPendenza, String numeroAvviso) {
        return new Dati(app.getId(), domA.getId(), tv.getId(), tvd.getId(), null, idPendenza, numeroAvviso,
                "RSSMRA80A01H501U", 100.0, StatoPendenza.NON_ESEGUITO, 0);
    }

    private static String iupd(String idPendenza) {
        return APP_COD + "12345678901" + "DOC-" + idPendenza;
    }

    private static String url(String idPendenza) {
        return "/posizioni-debitorie/" + APP_COD + "/" + iupd(idPendenza) + "/avviso";
    }

    private Pendenza opzioneUnica(String idPendenza, String nav, TipologiaOpzionePagamento tipologia) {
        return PendenzaV3Fixtures.pendenza(em, dati(idPendenza, nav).conTipologia(tipologia));
    }

    /** Aggiunge alla posizione di {@code base} una nuova opzione di una pendenza. */
    private Pendenza altraOpzione(Pendenza base, String idPendenza, String nav, TipologiaOpzionePagamento tipologia) {
        return PendenzaV3Fixtures.pendenzaNellaStessaPosizione(em, base, dati(idPendenza, nav).conTipologia(tipologia));
    }

    private ResultActions stampa(String idPendenza) throws Exception {
        return mvc.perform(get(url(idPendenza)).accept(MediaType.APPLICATION_PDF).with(httpBasic(PRINCIPAL, PASSWORD)));
    }

    private PaymentNotice payloadInviato() {
        ArgumentCaptor<PaymentNotice> captor = ArgumentCaptor.forClass(PaymentNotice.class);
        verify(stampeClient).streamPaymentNotice(captor.capture(), any());
        return captor.getValue();
    }

    private void stampeRisponde() {
        doAnswer(writePdf(PDF)).when(stampeClient).streamPaymentNotice(any(), any());
        doAnswer(writePdf(PDF)).when(stampeClient).streamCdsViolation(any(), any());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get(url("P-1"))).andExpect(status().isUnauthorized());
    }

    @Test
    void unaSolaPendenzaStampaUnAvvisoARataUnica() throws Exception {
        opzioneUnica("POS-1", "012345678901234567", TipologiaOpzionePagamento.SOLUZIONE_UNICA);
        stampeRisponde();

        stampa("POS-1")
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"" + iupd("POS-1") + ".pdf\""))
                .andExpect(content().bytes(PDF));

        PaymentNotice payload = payloadInviato();
        assertThat(payload.getFull().getNoticeNumber()).isEqualTo("012345678901234567");
        assertThat(payload.getTitle()).isEqualTo("Posizione POS-1");
    }

    @Test
    void ilPianoRatealeStampaUnAvvisoConTutteLeRate() throws Exception {
        Pendenza prima = opzioneUnica("POS-RATE", "012345678901234567", TipologiaOpzionePagamento.PIANO_RATEALE);
        PendenzaV3Fixtures.rataNellaStessaOpzione(em, prima, dati("POS-RATE-2", "012345678901234568"), 2);
        PendenzaV3Fixtures.rataNellaStessaOpzione(em, prima, dati("POS-RATE-3", "012345678901234569"), 3);
        stampeRisponde();

        stampa("POS-RATE").andExpect(status().isOk());

        PaymentNotice payload = payloadInviato();
        assertThat(payload.getFull()).isNull();
        assertThat(payload.getInstalments()).hasSize(3);
        assertThat(payload.getInstalments().get(2).getNoticeNumber()).isEqualTo("012345678901234569");
    }

    @Test
    void sonoStampateSoloLeRatePagabili() throws Exception {
        Pendenza prima = opzioneUnica("POS-PAG", "012345678901234567", TipologiaOpzionePagamento.PIANO_RATEALE);
        prima.setStato(StatoPendenza.ESEGUITO);
        PendenzaV3Fixtures.rataNellaStessaOpzione(em, prima, dati("POS-PAG-2", "012345678901234568"), 2);
        PendenzaV3Fixtures.rataNellaStessaOpzione(em, prima, dati("POS-PAG-3", "012345678901234569"), 3);
        stampeRisponde();

        stampa("POS-PAG").andExpect(status().isOk());

        PaymentNotice payload = payloadInviato();
        assertThat(payload.getInstalments()).hasSize(2);
        assertThat(payload.getInstalments().get(0).getInstalmentNumber()).isEqualTo(2);
    }

    @Test
    void piuOpzioniAlternativeStampanoUnaPaginaPerOpzione() throws Exception {
        Pendenza unica = opzioneUnica("POS-OPZ", "012345678901234567", TipologiaOpzionePagamento.SOLUZIONE_UNICA);
        Pendenza rate = altraOpzione(unica, "POS-OPZ-R1", "012345678901234568", TipologiaOpzionePagamento.PIANO_RATEALE);
        PendenzaV3Fixtures.rataNellaStessaOpzione(em, rate, dati("POS-OPZ-R2", "012345678901234569"), 2);
        stampeRisponde();

        stampa("POS-OPZ").andExpect(status().isOk());

        PaymentNotice payload = payloadInviato();
        assertThat(payload.getOptions()).hasSize(2);
        assertThat(payload.getOptions().get(0).getFull().getNoticeNumber()).isEqualTo("012345678901234567");
        assertThat(payload.getOptions().get(1).getInstalments()).hasSize(2);
    }

    @Test
    void entroEOltreStampanoDuePagamentiASogliaSuUnaPagina() throws Exception {
        Pendenza entro = opzioneUnica("POS-SOGLIE", "012345678901234567", TipologiaOpzionePagamento.SOLUZIONE_UNICA_ENTRO);
        entro.getOpzionePagamento().setGiorni(5);
        Pendenza oltre = altraOpzione(entro, "POS-SOGLIE-O", "012345678901234568",
                TipologiaOpzionePagamento.SOLUZIONE_UNICA_OLTRE);
        oltre.getOpzionePagamento().setGiorni(30);
        stampeRisponde();

        stampa("POS-SOGLIE").andExpect(status().isOk());

        PaymentNotice payload = payloadInviato();
        assertThat(payload.getOptions()).isEmpty();
        assertThat(payload.getReducedPayments()).hasSize(2);
        assertThat(payload.getReducedPayments().get(0).getThresholdDays()).isEqualTo(5);
    }

    @Test
    void ridottoEScontatoUsanoLaViolazioneDelCodiceDellaStrada() throws Exception {
        Pendenza ridotto = opzioneUnica("POS-CDS", "012345678901234567", TipologiaOpzionePagamento.SOLUZIONE_UNICA_RIDOTTO);
        altraOpzione(ridotto, "POS-CDS-S", "012345678901234568", TipologiaOpzionePagamento.SOLUZIONE_UNICA_SCONTATO);
        stampeRisponde();

        stampa("POS-CDS").andExpect(status().isOk()).andExpect(content().bytes(PDF));

        ArgumentCaptor<CdsViolation> captor = ArgumentCaptor.forClass(CdsViolation.class);
        verify(stampeClient).streamCdsViolation(captor.capture(), any());
        assertThat(captor.getValue().getReducedAmount().getNoticeNumber()).isEqualTo("012345678901234567");
        assertThat(captor.getValue().getDiscountedAmount().getNoticeNumber()).isEqualTo("012345678901234568");
        verify(stampeClient, never()).streamPaymentNotice(any(), any());
    }

    @Test
    void ilCreditoreRiportaIlPagamentoSulSitoDelDominio() throws Exception {
        domA.setPagamentoSito(Boolean.TRUE);
        opzioneUnica("POS-SITO", "012345678901234567", TipologiaOpzionePagamento.SOLUZIONE_UNICA);
        stampeRisponde();

        stampa("POS-SITO").andExpect(status().isOk());

        assertThat(payloadInviato().getCreditor().getPayOnWebsite()).isTrue();
    }

    @Test
    void returns422SeNonCiSonoPendenzePagabili() throws Exception {
        Pendenza p = opzioneUnica("POS-NOPAG", "012345678901234567", TipologiaOpzionePagamento.SOLUZIONE_UNICA);
        p.setStato(StatoPendenza.ESEGUITO);

        stampa("POS-NOPAG").andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentType("application/problem+json"));
        verify(stampeClient, never()).streamPaymentNotice(any(), any());
    }

    @Test
    void returns422PerMarcaDaBollo() throws Exception {
        Pendenza p = opzioneUnica("POS-MBT", "012345678901234567", TipologiaOpzionePagamento.SOLUZIONE_UNICA);
        VocePendenza voce = new VocePendenza();
        voce.setIdVocePendenza("V-MBT");
        voce.setIndice(1);
        voce.setImporto(16.0);
        voce.setDescrizione("Bollo");
        voce.setStato(StatoVocePendenza.NON_ESEGUITO);
        voce.setHashDocumento("HASH");
        voce.setProvinciaResidenza("RM");
        voce.setTipoBollo("01");
        p.addVocePendenza(voce);
        em.persist(voce);

        stampa("POS-MBT").andExpect(status().isUnprocessableEntity());
        verify(stampeClient, never()).streamPaymentNotice(any(), any());
    }

    @Test
    void returns422SeIlDebitoreNonHaAnagrafica() throws Exception {
        Pendenza p = opzioneUnica("POS-ANAG", "012345678901234567", TipologiaOpzionePagamento.SOLUZIONE_UNICA);
        p.getOpzionePagamento().getPdDebitore().getDebitore().setAnagrafica(null);

        stampa("POS-ANAG").andExpect(status().isUnprocessableEntity());
        verify(stampeClient, never()).streamPaymentNotice(any(), any());
    }

    // --- scelta del debitore ---

    @Test
    void conPiuDebitoriAttiviIdDebitoreEObbligatorio() throws Exception {
        PendenzaV3Fixtures.pendenzaDelSecondoDebitore(em, dati("POS-2D", "012345678901234567"),
                "VRDLGI90B02H501T", false);

        stampa("POS-2D").andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void conIdDebitoreSiStampaLAvvisoDiQuelDebitore() throws Exception {
        PendenzaV3Fixtures.pendenzaDelSecondoDebitore(em, dati("POS-2D", "012345678901234567"),
                "VRDLGI90B02H501T", false);
        stampeRisponde();

        mvc.perform(get(url("POS-2D")).param("idDebitore", "rssmra80a01h501u")
                        .accept(MediaType.APPLICATION_PDF).with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk());

        PaymentNotice payload = payloadInviato();
        assertThat(payload.getDebtor().getFiscalCode()).isEqualTo("RSSMRA80A01H501U");
        assertThat(payload.getFull().getNoticeNumber()).isEqualTo("012345678901234567");
    }

    @Test
    void returns404PerIdDebitoreSconosciuto() throws Exception {
        PendenzaV3Fixtures.pendenzaDelSecondoDebitore(em, dati("POS-2D", "012345678901234567"),
                "VRDLGI90B02H501T", false);

        mvc.perform(get(url("POS-2D")).param("idDebitore", "XXXXXXXXXXXXXXXX")
                        .accept(MediaType.APPLICATION_PDF).with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void ilDebitoreRimossoNonSiStampa() throws Exception {
        PendenzaV3Fixtures.pendenzaDelSecondoDebitore(em, dati("POS-RIM", "012345678901234567"),
                "VRDLGI90B02H501T", true);

        mvc.perform(get(url("POS-RIM")).param("idDebitore", "RSSMRA80A01H501U")
                        .accept(MediaType.APPLICATION_PDF).with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    // --- altri errori ---

    @Test
    void returns406PerAcceptNonPdf() throws Exception {
        opzioneUnica("POS-406", "012345678901234567", TipologiaOpzionePagamento.SOLUZIONE_UNICA);

        mvc.perform(get(url("POS-406")).accept(MediaType.APPLICATION_JSON).with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotAcceptable());
        verify(stampeClient, never()).streamPaymentNotice(any(), any());
    }

    @Test
    void returns404PerPosizioneInesistente() throws Exception {
        stampa("INESISTENTE").andExpect(status().isNotFound());
    }

    @Test
    void returns502SeStampeFallisce() throws Exception {
        opzioneUnica("POS-502", "012345678901234567", TipologiaOpzionePagamento.SOLUZIONE_UNICA);
        doThrow(new StampeUnavailableException("Connection refused", new RuntimeException()))
                .when(stampeClient).streamPaymentNotice(any(), any());

        stampa("POS-502").andExpect(status().isBadGateway());
    }

    // --- visibilita' ---

    private void operatoreSoloTari() {
        Utenza limitata = new Utenza();
        limitata.setPrincipal("solo-tari");
        limitata.setPrincipalOriginale("solo-tari");
        limitata.setAbilitato(true);
        limitata.setAutorizzazioneDominiStar(true);
        limitata.setAutorizzazioneTipiVersStar(false);
        limitata.setRuoli("OPERATORE");
        limitata.setPassword(encoder.encode(PASSWORD));
        utenzaRepository.save(limitata);
        Operatore op = new Operatore();
        op.setNome("Solo TARI");
        op.setIdUtenza(limitata.getId());
        operatoreRepository.save(op);
        UtenzaTipoVersamento utv = new UtenzaTipoVersamento();
        utv.setIdUtenza(limitata.getId());
        utv.setIdTipoVersamento(tv.getId());
        utenzaTipoVersamentoRepository.save(utv);
    }

    private TipoVersamentoDominio tipoImu() {
        TipoVersamento imu = new TipoVersamento();
        imu.setCodTipoVersamento("IMU");
        imu.setDescrizione("IMU");
        tipoVersamentoRepository.save(imu);
        TipoVersamentoDominio tvdImu = new TipoVersamentoDominio();
        tvdImu.setDominio(domA);
        tvdImu.setTipoVersamento(imu);
        tipoVersamentoDominioRepository.save(tvdImu);
        return tvdImu;
    }

    /** Un avviso con rate mancanti non e' ammesso: se una pendenza da stampare non e' visibile, 404. */
    @Test
    void returns404SeUnaPendenzaDaStampareNonEVisibileAllOperatore() throws Exception {
        operatoreSoloTari();
        TipoVersamentoDominio tvdImu = tipoImu();
        Pendenza tari = opzioneUnica("POS-MISTA", "012345678901234567", TipologiaOpzionePagamento.SOLUZIONE_UNICA);
        PendenzaV3Fixtures.pendenzaNellaStessaPosizione(em, tari, new Dati(app.getId(), domA.getId(),
                tvdImu.getTipoVersamento().getId(), tvdImu.getId(), null, "POS-MISTA-IMU", "012345678901234568",
                "RSSMRA80A01H501U", 100.0, StatoPendenza.NON_ESEGUITO, 0));
        stampeRisponde();

        mvc.perform(get(url("POS-MISTA")).accept(MediaType.APPLICATION_PDF).with(httpBasic("solo-tari", PASSWORD)))
                .andExpect(status().isNotFound());
        verify(stampeClient, never()).streamPaymentNotice(any(), any());

        // L'operatore che vede tutto la stampa.
        stampa("POS-MISTA").andExpect(status().isOk());
    }

    @Test
    void returns200SeLOperatoreVedeTutteLePendenzeDaStampare() throws Exception {
        operatoreSoloTari();
        opzioneUnica("POS-TARI", "012345678901234567", TipologiaOpzionePagamento.SOLUZIONE_UNICA);
        stampeRisponde();

        mvc.perform(get(url("POS-TARI")).accept(MediaType.APPLICATION_PDF).with(httpBasic("solo-tari", PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void returns404SeNessunaPendenzaEVisibile() throws Exception {
        operatoreSoloTari();
        TipoVersamentoDominio tvdImu = tipoImu();
        PendenzaV3Fixtures.pendenza(em, new Dati(app.getId(), domA.getId(), tvdImu.getTipoVersamento().getId(),
                tvdImu.getId(), null, "POS-IMU", "012345678901234567", "RSSMRA80A01H501U", 100.0,
                StatoPendenza.NON_ESEGUITO, 0));

        mvc.perform(get(url("POS-IMU")).accept(MediaType.APPLICATION_PDF).with(httpBasic("solo-tari", PASSWORD)))
                .andExpect(status().isNotFound());
    }
}
