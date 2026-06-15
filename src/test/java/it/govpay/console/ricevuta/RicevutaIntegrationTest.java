package it.govpay.console.ricevuta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
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

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.avviso.StampeClient;
import it.govpay.console.avviso.StampeUnavailableException;
import it.govpay.console.entity.Applicazione;
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Rpt;
import it.govpay.console.entity.SingoloVersamento;
import it.govpay.console.entity.TipoVersamento;
import it.govpay.console.entity.TipoVersamentoDominio;
import it.govpay.console.entity.Utenza;
import it.govpay.console.entity.Versamento;
import it.govpay.console.repository.ApplicazioneRepository;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.RptRepository;
import it.govpay.console.repository.TipoVersamentoDominioRepository;
import it.govpay.console.repository.TipoVersamentoRepository;
import it.govpay.console.repository.UtenzaRepository;
import it.govpay.console.repository.VersamentoRepository;
import it.govpay.console.security.GovpayPasswordEncoder;
import it.govpay.stampe.client.model.Receipt;
import it.govpay.stampe.client.model.ReceiptStatus;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"app.stampe.base-url=http://stampe.mock"})
@Transactional
class RicevutaIntegrationTest {

    private static final String PRINCIPAL = "operatore-r";
    private static final String PASSWORD = "secret";
    private static final String APP_COD = "APP-R";
    private static final String DOM = "98765432101";
    private static final byte[] XML_RT = "<RT><id>fake</id></RT>".getBytes(StandardCharsets.UTF_8);

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

    @MockitoBean private StampeClient stampeClient;

    private Dominio dom;
    private TipoVersamento tv;
    private TipoVersamentoDominio tvd;
    private Applicazione app;

    @BeforeEach
    void setup() {
        Utenza u = new Utenza();
        u.setPrincipal(PRINCIPAL);
        u.setPrincipalOriginale(PRINCIPAL);
        u.setAbilitato(true);
        u.setAutorizzazioneDominiStar(true);
        u.setAutorizzazioneTipiVersStar(true);
        u.setRuoli("OPERATORE");
        u.setPassword(encoder.encode(PASSWORD));
        utenzaRepository.save(u);

        Operatore op = new Operatore();
        op.setNome("Op");
        op.setIdUtenza(u.getId());
        operatoreRepository.save(op);

        dom = new Dominio();
        dom.setCodDominio(DOM);
        dom.setRagioneSociale("Comune di Test R");
        dom.setAuxDigit(0);
        dominioRepository.save(dom);

        app = new Applicazione();
        app.setCodApplicazione(APP_COD);
        applicazioneRepository.save(app);

        tv = new TipoVersamento();
        tv.setCodTipoVersamento("TARI");
        tv.setDescrizione("TARI");
        tipoVersamentoRepository.save(tv);

        tvd = new TipoVersamentoDominio();
        tvd.setDominio(dom);
        tvd.setTipoVersamento(tv);
        tipoVersamentoDominioRepository.save(tvd);
    }

    private Versamento newPendenza(String idPendenza) {
        Versamento v = new Versamento();
        v.setCodVersamentoEnte(idPendenza);
        v.setImportoTotale(100.0);
        v.setImportoPagato(100.0);
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
        v.setCausaleVersamento("TARI 2026 — saldo");
        v.setIuvVersamento("1234567890123");
        v.setNumeroAvviso("012345678901234567");
        v.setDominio(dom);
        v.setApplicazione(app);
        v.setTipoVersamento(tv);
        v.setTipoVersamentoDominio(tvd);
        return versamentoRepository.save(v);
    }

    private Rpt newRpt(Versamento v, String iuv, String ccp,
                       int esito, OffsetDateTime dataRicevuta, byte[] xmlRt) {
        Rpt r = new Rpt();
        r.setIuv(iuv);
        r.setCcp(ccp);
        r.setCodDominio(DOM);
        r.setXmlRt(xmlRt);
        r.setCodEsitoPagamento(esito);
        r.setImportoTotalePagato(100.0);
        r.setDataMsgRichiesta(dataRicevuta.minusHours(1));
        r.setDataMsgRicevuta(dataRicevuta);
        r.setVersamento(v);
        r.setVersione("SANP_240_V2");
        r.setDenominazioneAttestante("Banca Test S.p.A.");
        r.setCodPsp("BNCITEST01");
        r.setCodTransazioneRt("TX-001");
        return rptRepository.save(r);
    }

    private SingoloVersamento newSv(Versamento v, int indice, String descrizione, double importo) {
        SingoloVersamento sv = new SingoloVersamento();
        sv.setCodSingoloVersamentoEnte("SV-" + indice);
        sv.setStatoSingoloVersamento("ESEGUITO");
        sv.setImportoSingoloVersamento(importo);
        sv.setDescrizione(descrizione);
        sv.setIndiceDati(indice);
        sv.setVersamento(v);
        v.getSingoliVersamenti().add(sv);
        return sv;
    }

    private static Answer<Void> writePdf(byte[] bytes) {
        return invocation -> {
            OutputStream out = invocation.getArgument(1);
            out.write(bytes);
            return null;
        };
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-1/ricevuta"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsRicevutaJsonByDefault() throws Exception {
        Versamento v = newPendenza("PEND-1");
        newSv(v, 1, "Voce 1", 100.0);
        versamentoRepository.save(v);
        newRpt(v, "1234567890123", "CCP-001", 0, OffsetDateTime.now(), XML_RT);

        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-1/ricevuta")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.iuv", is("1234567890123")))
                .andExpect(jsonPath("$.ccp", is("CCP-001")))
                .andExpect(jsonPath("$.idDominio", is(DOM)))
                .andExpect(jsonPath("$.importoTotalePagato", is(100.0)))
                .andExpect(jsonPath("$.causale", is("TARI 2026 — saldo")))
                .andExpect(jsonPath("$.psp.idPsp", is("BNCITEST01")))
                .andExpect(jsonPath("$.psp.ragioneSociale", is("Banca Test S.p.A.")))
                .andExpect(jsonPath("$.riferimentoTransazione", is("TX-001")))
                .andExpect(jsonPath("$.singoliVersamenti[0].iur", is("1")))
                .andExpect(jsonPath("$.singoliVersamenti[0].importo", is(100.0)))
                .andExpect(jsonPath("$.singoliVersamenti[0].causale", is("Voce 1")));
    }

    @Test
    void returnsRawXmlWhenAcceptIsXml() throws Exception {
        Versamento v = newPendenza("PEND-XML");
        newRpt(v, "1234567890123", "CCP-XML", 0, OffsetDateTime.now(), XML_RT);

        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-XML/ricevuta")
                        .accept(MediaType.APPLICATION_XML)
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_XML))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"" + DOM + "_1234567890123_CCP-XML.xml\""))
                .andExpect(content().bytes(XML_RT));
    }

    @Test
    void returnsPdfWhenAcceptIsPdf() throws Exception {
        Versamento v = newPendenza("PEND-PDF");
        newSv(v, 1, "Voce 1", 100.0);
        versamentoRepository.save(v);
        newRpt(v, "1234567890123", "CCP-PDF", 0, OffsetDateTime.now(), XML_RT);

        byte[] fakePdf = new byte[]{'%', 'P', 'D', 'F'};
        doAnswer(writePdf(fakePdf)).when(stampeClient).streamReceipt(any(), any());

        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-PDF/ricevuta")
                        .accept(MediaType.APPLICATION_PDF)
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"" + DOM + "_1234567890123_CCP-PDF.pdf\""))
                .andExpect(content().bytes(fakePdf));

        ArgumentCaptor<Receipt> captor = ArgumentCaptor.forClass(Receipt.class);
        verify(stampeClient).streamReceipt(captor.capture(), any());
        Receipt r = captor.getValue();
        assertThat(r.getStatus()).isEqualTo(ReceiptStatus.EXECUTED);
        assertThat(r.getCreditorReferenceId()).isEqualTo("1234567890123");
        assertThat(r.getReceiptId()).isEqualTo("CCP-PDF");
        assertThat(r.getItems()).hasSize(1);
        assertThat(r.getItems().get(0).getIur()).isEqualTo("1");
    }

    @Test
    void picksMostRecentRtWhenMultiple() throws Exception {
        Versamento v = newPendenza("PEND-MULTI");
        OffsetDateTime older = OffsetDateTime.now().minusDays(2);
        OffsetDateTime newer = OffsetDateTime.now();
        newRpt(v, "OLD", "CCP-OLD", 0, older, XML_RT);
        newRpt(v, "NEW", "CCP-NEW", 0, newer, XML_RT);

        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-MULTI/ricevuta")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.iuv", is("NEW")));
    }

    @Test
    void excludesRtWithEsitoNonEseguito() throws Exception {
        Versamento v = newPendenza("PEND-FAIL");
        newRpt(v, "FAIL", "CCP-FAIL", 1 /* Non eseguito */,
                OffsetDateTime.now(), XML_RT);

        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-FAIL/ricevuta")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void returns404WhenNoRt() throws Exception {
        newPendenza("PEND-NO-RT");
        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-NO-RT/ricevuta")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void returns404WhenPendenzaDoesNotExist() throws Exception {
        mvc.perform(get("/pendenze/" + APP_COD + "/UNKNOWN/ricevuta")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void returns406ForUnsupportedAccept() throws Exception {
        Versamento v = newPendenza("PEND-406");
        newRpt(v, "1234567890123", "CCP-406", 0, OffsetDateTime.now(), XML_RT);

        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-406/ricevuta")
                        .accept(MediaType.TEXT_PLAIN)
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentType("application/problem+json"));
        verify(stampeClient, never()).streamReceipt(any(), any());
    }

    @Test
    void returns502WhenStampeFailsOnPdf() throws Exception {
        Versamento v = newPendenza("PEND-502");
        newRpt(v, "1234567890123", "CCP-502", 0, OffsetDateTime.now(), XML_RT);
        doThrow(new StampeUnavailableException("boom", new RuntimeException()))
                .when(stampeClient).streamReceipt(any(), any());

        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-502/ricevuta")
                        .accept(MediaType.APPLICATION_PDF)
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void jsonAndXmlNotAffectedByStampeFailure() throws Exception {
        Versamento v = newPendenza("PEND-JSON-XML");
        newRpt(v, "1234567890123", "CCP-OK", 0, OffsetDateTime.now(), XML_RT);
        doThrow(new StampeUnavailableException("boom", new RuntimeException()))
                .when(stampeClient).streamReceipt(any(), any());

        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-JSON-XML/ricevuta")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk());
        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-JSON-XML/ricevuta")
                        .accept(MediaType.APPLICATION_XML)
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk());
        verify(stampeClient, never()).streamReceipt(any(), any());
    }
}
