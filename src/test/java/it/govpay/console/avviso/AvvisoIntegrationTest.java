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
import it.govpay.console.entity.Documento;
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.IbanAccredito;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.SingoloVersamento;
import it.govpay.console.entity.Stazione;
import it.govpay.console.entity.TipoVersamento;
import it.govpay.console.entity.TipoVersamentoDominio;
import it.govpay.console.entity.UnitaOperativa;
import it.govpay.console.entity.Utenza;
import it.govpay.console.entity.UtenzaDominio;
import it.govpay.console.entity.Versamento;
import it.govpay.console.repository.ApplicazioneRepository;
import it.govpay.console.repository.DocumentoRepository;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.repository.IbanAccreditoRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.StazioneRepository;
import it.govpay.console.repository.TipoVersamentoDominioRepository;
import it.govpay.console.repository.TipoVersamentoRepository;
import it.govpay.console.repository.UnitaOperativaRepository;
import it.govpay.console.repository.UtenzaDominioRepository;
import it.govpay.console.repository.UtenzaRepository;
import it.govpay.console.repository.VersamentoRepository;
import it.govpay.common.auth.GovpayPasswordEncoder;
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
    private DominioRepository dominioRepository;
    @Autowired
    private StazioneRepository stazioneRepository;
    @Autowired
    private ApplicazioneRepository applicazioneRepository;
    @Autowired
    private TipoVersamentoRepository tipoVersamentoRepository;
    @Autowired
    private TipoVersamentoDominioRepository tipoVersamentoDominioRepository;
    @Autowired
    private UnitaOperativaRepository unitaOperativaRepository;
    @Autowired
    private VersamentoRepository versamentoRepository;
    @Autowired
    private DocumentoRepository documentoRepository;
    @Autowired
    private IbanAccreditoRepository ibanAccreditoRepository;

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

        Stazione stazione = new Stazione();
        stazione.setCodStazione("STZ-1");
        stazione.setApplicationCode(7);
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

    private Versamento newPendenzaConAvviso(String idPendenza, String numeroAvviso) {
        Versamento v = new Versamento();
        v.setCodVersamentoEnte(idPendenza);
        v.setImportoTotale(100.0);
        v.setImportoPagato(0.0);
        v.setStatoVersamento("NON_ESEGUITO");
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
        v.setAck(false);
        v.setTipo("DOVUTO");
        v.setCausaleVersamento("TARI 2026 — saldo");
        v.setIuvVersamento("1234567890123");
        v.setNumeroAvviso(numeroAvviso);
        v.setDominio(domA);
        v.setApplicazione(app);
        v.setTipoVersamento(tv);
        v.setTipoVersamentoDominio(tvd);
        return versamentoRepository.save(v);
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
        byte[] fakePdf = new byte[]{'%', 'P', 'D', 'F', '-', '1', '.', '4'};
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
     * V1 ({@code AvvisiDAO.java:180-184}): pendenza con documento → filename
     * {@code <codDominio>_DOC_<codDocumento>.pdf}. La generazione PDF non
     * cambia (singolo avviso della rata), cambia solo il nome del file.
     *
     * <p>Divergenza esplicita V2: aggiungiamo il suffisso {@code _<codRata>}
     * per evitare collisioni quando si scaricano piu' rate dello stesso
     * documento (bug noto di V1).
     */
    @Test
    void returnsPdfWithDocFilenameWhenPendenzaBelongsToDocumento() throws Exception {
        Documento doc = new Documento();
        doc.setCodDocumento("DOC-2026-001");
        documentoRepository.save(doc);

        Versamento v = newPendenzaConAvviso("PEND-DOC", "012345678901234567");
        v.setDocumento(doc);
        v.setCodRata("R02");
        versamentoRepository.save(v);

        byte[] fakePdf = new byte[]{'%', 'P', 'D', 'F', '-', '1', '.', '4'};
        doAnswer(writePdf(fakePdf)).when(stampeClient).streamPaymentNotice(any(), any());

        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-DOC/avviso")
                        .accept(MediaType.APPLICATION_PDF)
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"12345678901_DOC_DOC-2026-001_R02.pdf\""))
                .andExpect(content().bytes(fakePdf));
    }

    /**
     * V1 ({@code AvvisiDAO.java:190} + {@code VersamentoUtils.isPendenzaMBT}):
     * pendenza Marca da Bollo Telematica → 422 prima di chiamare il
     * microservizio.
     */
    @Test
    void returns422ForMarcaDaBolloTelematica() throws Exception {
        Versamento v = newPendenzaConAvviso("PEND-MBT", "012345678901234567");
        SingoloVersamento sv = new SingoloVersamento();
        sv.setCodSingoloVersamentoEnte("SV-MBT");
        sv.setStatoSingoloVersamento("NON_ESEGUITO");
        sv.setImportoSingoloVersamento(16.0);
        sv.setIndiceDati(1);
        sv.setVersamento(v);
        sv.setHashDocumento("HASH-001");
        sv.setProvinciaResidenza("RM");
        sv.setTipoBollo("01");
        v.getSingoliVersamenti().add(sv);
        versamentoRepository.save(v);

        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-MBT/avviso")
                        .accept(MediaType.APPLICATION_PDF)
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentType("application/problem+json"));
        verify(stampeClient, never()).streamPaymentNotice(any(), any());
    }

    /**
     * V1 ({@code AvvisoPagamentoV2Utils.java:441-446}): se il primo
     * SingoloVersamento ha un IBAN postale (di accredito o di appoggio), il
     * payload PDF deve avere {@code postal=true}.
     */
    @Test
    void postalTrueWhenSingoloVersamentoHasIbanPostale() throws Exception {
        IbanAccredito ibanPostale = new IbanAccredito();
        ibanPostale.setPostale(true);
        ibanAccreditoRepository.save(ibanPostale);

        Versamento v = newPendenzaConAvviso("PEND-POST", "012345678901234567");
        SingoloVersamento sv = new SingoloVersamento();
        sv.setCodSingoloVersamentoEnte("SV-1");
        sv.setStatoSingoloVersamento("NON_ESEGUITO");
        sv.setImportoSingoloVersamento(100.0);
        sv.setIndiceDati(1);
        sv.setVersamento(v);
        sv.setIbanAccredito(ibanPostale);
        v.getSingoliVersamenti().add(sv);
        versamentoRepository.save(v);

        doAnswer(writePdf(new byte[]{'%', 'P', 'D', 'F'}))
                .when(stampeClient).streamPaymentNotice(any(), any());

        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-POST/avviso")
                        .accept(MediaType.APPLICATION_PDF)
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk());

        ArgumentCaptor<PaymentNotice> captor = ArgumentCaptor.forClass(PaymentNotice.class);
        verify(stampeClient).streamPaymentNotice(captor.capture(), any());
        assertThat(captor.getValue().getPostal()).isTrue();
    }

    /**
     * Pendenza in un documento ma senza codRata valorizzato: fallback al
     * numeroAvviso (sempre univoco) per evitare ambiguita'.
     */
    @Test
    void docFilenameFallsBackToNumeroAvvisoWhenCodRataIsNull() throws Exception {
        Documento doc = new Documento();
        doc.setCodDocumento("DOC-NORATA");
        documentoRepository.save(doc);

        Versamento v = newPendenzaConAvviso("PEND-NORATA", "012345678901234567");
        v.setDocumento(doc);
        versamentoRepository.save(v);

        doAnswer(writePdf(new byte[]{'%', 'P', 'D', 'F'}))
                .when(stampeClient).streamPaymentNotice(any(), any());

        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-NORATA/avviso")
                        .accept(MediaType.APPLICATION_PDF)
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"12345678901_DOC_DOC-NORATA_012345678901234567.pdf\""));
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
        newPendenzaConAvviso("PEND-DE", "012345678901234567");
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

    // 503 (stampe non configurato) testato in AvvisoNotConfiguredTest con
    // app.stampe.base-url vuoto per non duplicare il context Spring qui.
}
