package it.govpay.console.avviso;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.function.Consumer;

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
import org.springframework.transaction.annotation.Transactional;

import java.io.OutputStream;

import it.govpay.console.entity.Applicazione;
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.IbanAccredito;
import it.govpay.console.entity.Intermediario;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Stazione;
import it.govpay.console.entity.TipoTributo;
import it.govpay.console.entity.TipoVersamento;
import it.govpay.console.entity.Tributo;
import it.govpay.console.entity.TipoVersamentoDominio;
import it.govpay.console.entity.UnitaOperativa;
import it.govpay.console.entity.Utenza;
import it.govpay.console.entity.UtenzaDominio;
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
import it.govpay.console.repository.UtenzaRepository;
import it.govpay.common.auth.GovpayPasswordEncoder;
import it.govpay.console.pendenza.PendenzaV3Fixtures;
import it.govpay.console.pendenza.PendenzaV3Fixtures.Dati;
import it.govpay.pendenze.entity.Debitore;
import it.govpay.pendenze.entity.Pendenza;
import it.govpay.pendenze.entity.VocePendenza;
import it.govpay.pendenze.model.StatoPendenza;
import it.govpay.pendenze.model.StatoVocePendenza;
import it.govpay.pendenze.model.TipologiaOpzionePagamento;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import it.govpay.stampe.client.model.Languages;
import it.govpay.stampe.client.model.PaymentNotice;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"app.stampe.base-url=http://stampe.mock"})
@Transactional
class AvvisoIntegrationTest {

    private static final String PRINCIPAL = "operatore1";
    private static final String PASSWORD = "secret";
    private static final String APP_COD = "APP-A";

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
    @Autowired
    private UnitaOperativaRepository unitaOperativaRepository;
    @PersistenceContext
    private EntityManager em;
    @Autowired
    private IbanAccreditoRepository ibanAccreditoRepository;

    @Autowired
    private TributoRepository tributoRepository;

    @Autowired
    private TipoTributoRepository tipoTributoRepository;

    @MockitoBean
    private StampeClient stampeClient;

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

    private Pendenza newPendenzaConAvviso(String idPendenza, String numeroAvviso) {
        Pendenza p = PendenzaV3Fixtures.pendenza(em, dati(idPendenza, numeroAvviso));
        p.setCausaleVersamento("TARI 2026 — saldo");
        return p;
    }

    private VocePendenza aggiungiVoce(Pendenza p, Consumer<VocePendenza> personalizza) {
        VocePendenza voce = new VocePendenza();
        voce.setIdVocePendenza("VOCE-" + p.getIdPendenza());
        voce.setIndice(1);
        voce.setImporto(100.0);
        voce.setDescrizione("Voce");
        voce.setStato(StatoVocePendenza.NON_ESEGUITO);
        personalizza.accept(voce);
        p.addVocePendenza(voce);
        em.persist(voce);
        return voce;
    }

    private static Debitore debitore(Pendenza p) {
        return p.getOpzionePagamento().getPdDebitore().getDebitore();
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-1/avviso"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsAvvisoJsonByDefault() throws Exception {
        newPendenzaConAvviso("PEND-1", "012345678901234567");

        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-1/avviso").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.idDominio", is("12345678901")))
                .andExpect(jsonPath("$.numeroAvviso", is("012345678901234567")))
                .andExpect(jsonPath("$.importo", is(100.0)))
                .andExpect(jsonPath("$.descrizione", is("TARI 2026 — saldo")))
                .andExpect(jsonPath("$.stato", is("NON_PAGATO")))
                .andExpect(jsonPath("$.qrcode", startsWith("PAGOPA|002|012345678901234567|12345678901|")))
                .andExpect(jsonPath("$.barcode", startsWith("415" + "0123456789012" + "8020012345678901234567")));
    }

    @Test
    void returnsPdfWhenAcceptIsPdf() throws Exception {
        newPendenzaConAvviso("PEND-PDF", "012345678901234567");
        byte[] fakePdf = {'%', 'P', 'D', 'F', '-', '1', '.', '4'};
        doAnswer(writePdf(fakePdf)).when(stampeClient).streamPaymentNotice(any(), any());

        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-PDF/avviso")
                        .accept(MediaType.APPLICATION_PDF)
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"12345678901_012345678901234567.pdf\""))
                .andExpect(content().bytes(fakePdf));

        verify(stampeClient).streamPaymentNotice(any(), any());
    }

    @Test
    void returns404IfPendenzaDoesNotExist() throws Exception {
        mvc.perform(get("/pendenze/" + APP_COD + "/UNKNOWN/avviso").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void returns404IfNumeroAvvisoIsMissing() throws Exception {
        newPendenzaConAvviso("PEND-NO-AVV", null);
        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-NO-AVV/avviso").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void returns406ForUnsupportedAccept() throws Exception {
        newPendenzaConAvviso("PEND-1", "012345678901234567");
        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-1/avviso")
                        .accept(MediaType.TEXT_PLAIN)
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentType("application/problem+json"));
        verify(stampeClient, never()).streamPaymentNotice(any(), any());
    }

    /**
     * Ogni rata di un'opzione ha il proprio numero avviso, quindi il proprio file: nessuna
     * collisione quando si scaricano piu' rate della stessa posizione.
     */
    @Test
    void ogniRataHaIlProprioNomeFile() throws Exception {
        Pendenza prima = newPendenzaConAvviso("PEND-RATA-1", "012345678901234567");
        PendenzaV3Fixtures.pendenzaNellaStessaPosizione(em, prima, dati("PEND-RATA-2", "012345678901234568"));
        doAnswer(writePdf(new byte[]{'%', 'P', 'D', 'F'}))
                .when(stampeClient).streamPaymentNotice(any(), any());

        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-RATA-2/avviso")
                        .accept(MediaType.APPLICATION_PDF)
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"12345678901_012345678901234568.pdf\""));

        ArgumentCaptor<PaymentNotice> captor = ArgumentCaptor.forClass(PaymentNotice.class);
        verify(stampeClient).streamPaymentNotice(captor.capture(), any());
        assertThat(captor.getValue().getFull().getNoticeNumber()).isEqualTo("012345678901234568");
        assertThat(captor.getValue().getInstalments()).isEmpty();
    }

    /**
     * V1 ({@code AvvisiDAO.java:190} + {@code VersamentoUtils.isPendenzaMBT}):
     * pendenza Marca da Bollo Telematica → 422 prima di chiamare il
     * microservizio.
     */
    @Test
    void returns422ForMarcaDaBolloTelematica() throws Exception {
        Pendenza p = newPendenzaConAvviso("PEND-MBT", "012345678901234567");
        aggiungiVoce(p, voce -> {
            voce.setHashDocumento("HASH-001");
            voce.setProvinciaResidenza("RM");
            voce.setTipoBollo("01");
        });

        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-MBT/avviso")
                        .accept(MediaType.APPLICATION_PDF)
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentType("application/problem+json"));
        verify(stampeClient, never()).streamPaymentNotice(any(), any());
    }

    /**
     * Se la prima voce ha un IBAN postale (di accredito o di appoggio),
     * il payload PDF deve avere {@code postal=true}.
     */
    @Test
    void postalTrueWhenVoceHasIbanPostale() throws Exception {
        IbanAccredito ibanPostale = new IbanAccredito();
        ibanPostale.setPostale(true);
        ibanPostale.setCodIban("IT60X0542811101000000123456");
        ibanPostale.setDominio(domA);
        ibanAccreditoRepository.save(ibanPostale);

        Pendenza p = newPendenzaConAvviso("PEND-POST", "012345678901234567");
        aggiungiVoce(p, voce -> voce.setIdIbanAccredito(ibanPostale.getId()));

        doAnswer(writePdf(new byte[]{'%', 'P', 'D', 'F'}))
                .when(stampeClient).streamPaymentNotice(any(), any());

        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-POST/avviso")
                        .accept(MediaType.APPLICATION_PDF)
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk());

        ArgumentCaptor<PaymentNotice> captor = ArgumentCaptor.forClass(PaymentNotice.class);
        verify(stampeClient).streamPaymentNotice(captor.capture(), any());
        assertThat(captor.getValue().getPostal()).isTrue();
        // Con postal=true govpay-stampe esige full.iban: senza, risponde 422.
        assertThat(captor.getValue().getFull().getIban()).isNotNull();
        assertThat(captor.getValue().getFull().getIban().getIbanCode())
                .isEqualTo("IT60X0542811101000000123456");
    }

    /**
     * Pendenza a riferimento: {@code singoli_versamenti.id_iban_accredito} e'
     * nulla e l'IBAN postale va ereditato dal tipo entrata del dominio
     * ({@code tributi}), come per ogni pendenza a riferimento.
     * E' il caso che in esercizio produceva il 422 da govpay-stampe.
     */
    @Test
    void postalIbanEreditatoDalTipoEntrataDominioPerPendenzaARiferimento() throws Exception {
        IbanAccredito ibanPostale = new IbanAccredito();
        ibanPostale.setPostale(true);
        ibanPostale.setCodIban("IT60X0542811101000000999999");
        ibanPostale.setDominio(domA);
        ibanAccreditoRepository.save(ibanPostale);

        TipoTributo tipoTributo = new TipoTributo();
        tipoTributo.setCodTributo("TARI-RIF");
        tipoTributoRepository.save(tipoTributo);

        Tributo tributo = new Tributo();
        tributo.setDominio(domA);
        tributo.setTipoTributo(tipoTributo);
        tributo.setAbilitato(true);
        tributo.setIbanAccredito(ibanPostale);
        tributoRepository.save(tributo);

        Pendenza p = newPendenzaConAvviso("PEND-RIF", "012345678901234568");
        // Nessun iban sulla voce: solo il riferimento al tipo entrata.
        aggiungiVoce(p, voce -> voce.setIdTributo(tributo.getId()));

        doAnswer(writePdf(new byte[]{'%', 'P', 'D', 'F'}))
                .when(stampeClient).streamPaymentNotice(any(), any());

        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-RIF/avviso")
                        .accept(MediaType.APPLICATION_PDF)
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk());

        ArgumentCaptor<PaymentNotice> captor = ArgumentCaptor.forClass(PaymentNotice.class);
        verify(stampeClient).streamPaymentNotice(captor.capture(), any());
        assertThat(captor.getValue().getPostal()).isTrue();
        assertThat(captor.getValue().getFull().getIban().getIbanCode())
                .isEqualTo("IT60X0542811101000000999999");
    }

    @Test
    void returns502WhenStampeFails() throws Exception {
        newPendenzaConAvviso("PEND-PDF2", "012345678901234567");
        doThrow(new StampeUnavailableException("Connection refused", new RuntimeException()))
                .when(stampeClient).streamPaymentNotice(any(), any());

        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-PDF2/avviso")
                        .accept(MediaType.APPLICATION_PDF)
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void linguaSecondariaDePopulatesSecondLanguage() throws Exception {
        Pendenza p = newPendenzaConAvviso("PEND-DE", "012345678901234567");
        p.setProprieta("{\"linguaSecondariaCausale\": \"TARI payment 2026\"}");
        doAnswer(writePdf(new byte[]{'%', 'P', 'D', 'F'}))
                .when(stampeClient).streamPaymentNotice(any(), any());

        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-DE/avviso")
                        .param("linguaSecondaria", "DE")
                        .accept(MediaType.APPLICATION_PDF)
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk());

        ArgumentCaptor<PaymentNotice> captor = ArgumentCaptor.forClass(PaymentNotice.class);
        verify(stampeClient).streamPaymentNotice(captor.capture(), any());
        PaymentNotice payload = captor.getValue();
        assertThat(payload.getLanguage()).isEqualTo(Languages.IT);
        assertThat(payload.getSecondLanguage()).isNotNull();
        assertThat(payload.getSecondLanguage().getBilinguism()).isTrue();
        assertThat(payload.getSecondLanguage().getLanguage()).isEqualTo(Languages.DE);
        assertThat(payload.getSecondLanguage().getTitle()).isEqualTo("TARI payment 2026");
    }

    /**
     * {@code second_language.title} e' richiesto dallo schema di govpay-stampe: senza una
     * causale tradotta configurata, l'avviso bilingue non e' un documento valido — va rifiutato
     * qui (400), non lasciato arrivare a govpay-stampe per poi tradurre il suo 400 in un 502
     * generico al chiamante. {@code stampeClient} non va mai invocato.
     */
    @Test
    void linguaSecondariaSenzaCausaleTradottaRestituisce400() throws Exception {
        newPendenzaConAvviso("PEND-DE-NO-CAUSALE", "012345678901234568");

        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-DE-NO-CAUSALE/avviso")
                        .param("linguaSecondaria", "DE")
                        .accept(MediaType.APPLICATION_PDF)
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"));

        verify(stampeClient, never()).streamPaymentNotice(any(), any());
    }

    @Test
    void pagamentoSitoDelDominioArrivaANellaStampaDellaPendenza() throws Exception {
        domA.setPagamentoSito(Boolean.TRUE);
        newPendenzaConAvviso("PEND-SITO", "012345678901234567");
        doAnswer(writePdf(new byte[]{'%', 'P', 'D', 'F'}))
                .when(stampeClient).streamPaymentNotice(any(), any());

        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-SITO/avviso")
                        .accept(MediaType.APPLICATION_PDF)
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk());

        ArgumentCaptor<PaymentNotice> captor = ArgumentCaptor.forClass(PaymentNotice.class);
        verify(stampeClient).streamPaymentNotice(captor.capture(), any());
        assertThat(captor.getValue().getCreditor().getPayOnWebsite()).isTrue();
    }

    @Test
    void linguaSecondariaNoneSkipsSecondLanguage() throws Exception {
        newPendenzaConAvviso("PEND-NONE", "012345678901234567");
        doAnswer(writePdf(new byte[]{'%', 'P', 'D', 'F'}))
                .when(stampeClient).streamPaymentNotice(any(), any());

        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-NONE/avviso")
                        .param("linguaSecondaria", "NONE")
                        .accept(MediaType.APPLICATION_PDF)
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk());

        ArgumentCaptor<PaymentNotice> captor = ArgumentCaptor.forClass(PaymentNotice.class);
        verify(stampeClient).streamPaymentNotice(captor.capture(), any());
        assertThat(captor.getValue().getSecondLanguage()).isNull();
    }

    @Test
    void noLinguaSecondariaParamMeansItalianOnly() throws Exception {
        newPendenzaConAvviso("PEND-IT", "012345678901234567");
        doAnswer(writePdf(new byte[]{'%', 'P', 'D', 'F'}))
                .when(stampeClient).streamPaymentNotice(any(), any());

        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-IT/avviso")
                        .accept(MediaType.APPLICATION_PDF)
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk());

        ArgumentCaptor<PaymentNotice> captor = ArgumentCaptor.forClass(PaymentNotice.class);
        verify(stampeClient).streamPaymentNotice(captor.capture(), any());
        assertThat(captor.getValue().getSecondLanguage()).isNull();
    }

    /** Nel modello v3 l'anagrafica del debitore e' facoltativa, ma govpay-stampe la richiede. */
    @Test
    void returns422SeIlDebitoreNonHaAnagrafica() throws Exception {
        Pendenza p = newPendenzaConAvviso("PEND-SENZA-ANAG", "012345678901234567");
        debitore(p).setAnagrafica(null);

        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-SENZA-ANAG/avviso")
                        .accept(MediaType.APPLICATION_PDF)
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentType("application/problem+json"));
        verify(stampeClient, never()).streamPaymentNotice(any(), any());
    }

    /** Il debitore dell'avviso e' quello dell'opzione della pendenza, non il primo della posizione. */
    @Test
    void ilDebitoreDellAvvisoEQuelloDellaPendenza() throws Exception {
        PendenzaV3Fixtures.pendenzaDelSecondoDebitore(em, dati("PEND-SECONDO", "012345678901234567"),
                "VRDLGI90B02H501T", false);
        doAnswer(writePdf(new byte[]{'%', 'P', 'D', 'F'}))
                .when(stampeClient).streamPaymentNotice(any(), any());

        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-SECONDO/avviso")
                        .accept(MediaType.APPLICATION_PDF)
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk());

        ArgumentCaptor<PaymentNotice> captor = ArgumentCaptor.forClass(PaymentNotice.class);
        verify(stampeClient).streamPaymentNotice(captor.capture(), any());
        assertThat(captor.getValue().getDebtor().getFiscalCode()).isEqualTo("RSSMRA80A01H501U");
    }

    /** Un'opzione con soglia temporale stampa la sua pendenza come pagamento con soglia. */
    @Test
    void opzioneEntroStampaIlPagamentoConSoglia() throws Exception {
        Pendenza p = newPendenzaConAvviso("PEND-ENTRO", "012345678901234567");
        p.getOpzionePagamento().setTipologia(TipologiaOpzionePagamento.SOLUZIONE_UNICA_ENTRO);
        p.getOpzionePagamento().setGiorni(5);
        doAnswer(writePdf(new byte[]{'%', 'P', 'D', 'F'}))
                .when(stampeClient).streamPaymentNotice(any(), any());

        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-ENTRO/avviso")
                        .accept(MediaType.APPLICATION_PDF)
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk());

        ArgumentCaptor<PaymentNotice> captor = ArgumentCaptor.forClass(PaymentNotice.class);
        verify(stampeClient).streamPaymentNotice(captor.capture(), any());
        assertThat(captor.getValue().getReducedPayments()).hasSize(1);
        assertThat(captor.getValue().getReducedPayments().get(0).getThresholdDays()).isEqualTo(5);
    }

    @Test
    void jsonConScadenzaPassataEScaduto() throws Exception {
        Pendenza p = newPendenzaConAvviso("PEND-SCAD", "012345678901234567");
        p.setDataScadenzaAvviso(OffsetDateTime.now().minusDays(1));

        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-SCAD/avviso").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stato", is("SCADUTO")));
    }

    @Test
    void jsonDiPendenzaPagataEPagato() throws Exception {
        Pendenza p = newPendenzaConAvviso("PEND-PAG", "012345678901234567");
        p.setStato(StatoPendenza.ESEGUITO);

        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-PAG/avviso").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stato", is("PAGATO")));
    }

    @Test
    void returns404SeLaPendenzaNonEVisibileAllOperatore() throws Exception {
        newPendenzaConAvviso("PEND-NASCOSTA", "012345678901234567");
        Utenza limitata = new Utenza();
        limitata.setPrincipal("limitato");
        limitata.setPrincipalOriginale("limitato");
        limitata.setAbilitato(true);
        limitata.setAutorizzazioneDominiStar(false);
        limitata.setAutorizzazioneTipiVersStar(true);
        limitata.setRuoli("OPERATORE");
        limitata.setPassword(encoder.encode(PASSWORD));
        utenzaRepository.save(limitata);
        Operatore op = new Operatore();
        op.setNome("Limitato");
        op.setIdUtenza(limitata.getId());
        operatoreRepository.save(op);

        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-NASCOSTA/avviso").with(httpBasic("limitato", PASSWORD)))
                .andExpect(status().isNotFound());
    }

    // 503 (stampe non configurato) testato in AvvisoNotConfiguredTest con
    // app.stampe.base-url vuoto per non duplicare il context Spring qui.
}
