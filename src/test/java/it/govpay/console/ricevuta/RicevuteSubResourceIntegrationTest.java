package it.govpay.console.ricevuta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import it.gov.pagopa.pagopa_api.pa.pafornode.CtPaymentPA;
import it.gov.pagopa.pagopa_api.pa.pafornode.CtReceipt;
import it.gov.pagopa.pagopa_api.pa.pafornode.PaGetPaymentRes;
import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTReq;
import it.gov.pagopa.pagopa_api.pa.pafornode.StOutcome;
import it.govpay.common.auth.GovpayPasswordEncoder;
import it.govpay.console.avviso.StampeClient;
import it.govpay.console.avviso.StampeUnavailableException;
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
import it.govpay.console.repository.ApplicazioneRepository;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.repository.GpAuditRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.RptRepository;
import it.govpay.console.repository.TipoVersamentoDominioRepository;
import it.govpay.console.repository.TipoVersamentoRepository;
import it.govpay.console.repository.UtenzaDominioRepository;
import it.govpay.console.repository.UtenzaRepository;
import it.govpay.console.repository.VersamentoRepository;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;

/**
 * Integration test dei sub-resource {@code /rpt} e {@code /rt} (scope D/E issue #12):
 * content negotiation JSON/XML(/PDF), 404/406/502, ACL, audit con risorsa+formato.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"app.stampe.base-url=http://stampe.mock"})
@Transactional
class RicevuteSubResourceIntegrationTest {

    private static final String PASSWORD = "secret";
    private static final String DOM = "11111111111";
    private static final String DOM_ALTRO = "22222222222";
    private static final String PA_FOR_NODE_PKG = "it.gov.pagopa.pagopa_api.pa.pafornode";

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
    @Autowired private GpAuditRepository gpAuditRepository;

    @MockitoBean private StampeClient stampeClient;

    private Dominio dom;
    private Dominio domAltro;
    private Applicazione app;
    private TipoVersamento tv;
    private TipoVersamentoDominio tvd;

    private byte[] xmlRpt;
    private byte[] xmlRt;

    @BeforeEach
    void setup() {
        dom = newDominio(DOM, "Dominio A");
        domAltro = newDominio(DOM_ALTRO, "Dominio B");
        app = new Applicazione();
        app.setCodApplicazione("APP-1");
        applicazioneRepository.save(app);
        tv = new TipoVersamento();
        tv.setCodTipoVersamento("TARI");
        tv.setDescrizione("TARI");
        tipoVersamentoRepository.save(tv);
        tvd = new TipoVersamentoDominio();
        tvd.setDominio(dom);
        tvd.setTipoVersamento(tv);
        tipoVersamentoDominioRepository.save(tvd);

        Utenza u = baseUtenza("op-all", true);
        utenzaRepository.save(u);
        attachOperatore("op-all", u);
    }

    // ----- /rpt --------------------------------------------------------------

    @Test
    void rptJsonOk() throws Exception {
        newRpt(dom, "IUVRPT00001", "CCP-R", true);
        mvc.perform(get("/ricevute/" + DOM + "/IUVRPT00001/CCP-R/rpt")
                        .accept(MediaType.APPLICATION_JSON).with(httpBasic("op-all", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.creditorReferenceId", org.hamcrest.Matchers.is("IUV-RPT")));
        assertThat(auditFor("IUVRPT00001")).anyMatch(a -> a.getOggetto().contains("\"risorsa\":\"rpt\"")
                && a.getOggetto().contains("\"formato\":\"json\""));
    }

    @Test
    void rptXmlByteIdentico() throws Exception {
        newRpt(dom, "IUVRPT00002", "CCP-RX", true);
        byte[] body = mvc.perform(get("/ricevute/" + DOM + "/IUVRPT00002/CCP-RX/rpt")
                        .accept(MediaType.APPLICATION_XML).with(httpBasic("op-all", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_XML))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("rpt-IUVRPT00002.xml")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(body).isEqualTo(xmlRpt);
    }

    @Test
    void rptPdf406() throws Exception {
        newRpt(dom, "IUVRPT00003", "CCP-RP", true);
        mvc.perform(get("/ricevute/" + DOM + "/IUVRPT00003/CCP-RP/rpt")
                        .accept(MediaType.APPLICATION_PDF).with(httpBasic("op-all", PASSWORD)))
                .andExpect(status().isNotAcceptable());
    }

    @Test
    void rptStandinJson404() throws Exception {
        newRpt(dom, "IUVRPT00004", "CCP-RS", false /* xml_rpt null */);
        mvc.perform(get("/ricevute/" + DOM + "/IUVRPT00004/CCP-RS/rpt")
                        .accept(MediaType.APPLICATION_JSON).with(httpBasic("op-all", PASSWORD)))
                .andExpect(status().isNotFound());
    }

    // ----- /rt ---------------------------------------------------------------

    @Test
    void rtJsonOk() throws Exception {
        newRpt(dom, "IUVRT000001", "CCP-T", true);
        mvc.perform(get("/ricevute/" + DOM + "/IUVRT000001/CCP-T/rt")
                        .accept(MediaType.APPLICATION_JSON).with(httpBasic("op-all", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receiptId", org.hamcrest.Matchers.is("REC-1")))
                .andExpect(jsonPath("$.outcome", org.hamcrest.Matchers.is("OK")));
        assertThat(auditFor("IUVRT000001")).anyMatch(a -> a.getOggetto().contains("\"risorsa\":\"rt\"")
                && a.getOggetto().contains("\"formato\":\"json\""));
    }

    @Test
    void rtXmlByteIdentico() throws Exception {
        newRpt(dom, "IUVRT000002", "CCP-TX", true);
        byte[] body = mvc.perform(get("/ricevute/" + DOM + "/IUVRT000002/CCP-TX/rt")
                        .accept(MediaType.APPLICATION_XML).with(httpBasic("op-all", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_XML))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("rt-IUVRT000002.xml")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(body).isEqualTo(xmlRt);
    }

    @Test
    void rtPdfOk() throws Exception {
        newRpt(dom, "IUVRT000003", "CCP-TP", true);
        doAnswer(writePdf("%PDF-FAKE".getBytes())).when(stampeClient).streamReceipt(any(), any());

        byte[] body = mvc.perform(get("/ricevute/" + DOM + "/IUVRT000003/CCP-TP/rt")
                        .accept(MediaType.APPLICATION_PDF).with(httpBasic("op-all", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("ricevuta-IUVRT000003.pdf")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(body).isEqualTo("%PDF-FAKE".getBytes());
        assertThat(auditFor("IUVRT000003")).anyMatch(a -> a.getOggetto().contains("\"formato\":\"pdf\""));
    }

    @Test
    void rtPdf502SeStampeIrraggiungibile() throws Exception {
        newRpt(dom, "IUVRT000004", "CCP-TE", true);
        doThrow(new StampeUnavailableException("ko", new RuntimeException()))
                .when(stampeClient).streamReceipt(any(), any());

        mvc.perform(get("/ricevute/" + DOM + "/IUVRT000004/CCP-TE/rt")
                        .accept(MediaType.APPLICATION_PDF).with(httpBasic("op-all", PASSWORD)))
                .andExpect(status().isBadGateway());
        // 502: nessun audit (lo scriviamo solo dopo lo streaming riuscito)
        assertThat(auditFor("IUVRT000004")).isEmpty();
    }

    // ----- 404 / ACL ---------------------------------------------------------

    @Test
    void subResource404SeInesistente() throws Exception {
        mvc.perform(get("/ricevute/" + DOM + "/IUVZZZ/CCP-Z/rt").with(httpBasic("op-all", PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void subResourceAcl404() throws Exception {
        newRpt(dom, "IUVACL00001", "CCP-A", true);
        Utenza u = baseUtenza("op-altro", false);
        utenzaRepository.save(u);
        attachOperatore("op-altro", u);
        UtenzaDominio link = new UtenzaDominio();
        link.setIdUtenza(u.getId());
        link.setIdDominio(domAltro.getId());
        utenzaDominioRepository.save(link);

        mvc.perform(get("/ricevute/" + DOM + "/IUVACL00001/CCP-A/rt").with(httpBasic("op-altro", PASSWORD)))
                .andExpect(status().isNotFound());
    }

    // ----- helpers -----------------------------------------------------------

    private List<GpAudit> auditFor(String iuv) {
        return gpAuditRepository.findAll().stream()
                .filter(a -> RicevutaService.AZIONE_AUDIT_VISUALIZZA.equals(a.getTipoOggetto()))
                .filter(a -> a.getOggetto() != null && a.getOggetto().contains(iuv))
                .toList();
    }

    private static Answer<Void> writePdf(byte[] bytes) {
        return invocation -> {
            OutputStream out = invocation.getArgument(1);
            out.write(bytes);
            return null;
        };
    }

    private Dominio newDominio(String cod, String ragione) {
        Dominio d = new Dominio();
        d.setCodDominio(cod);
        d.setRagioneSociale(ragione);
        d.setAuxDigit(0);
        return dominioRepository.save(d);
    }

    private Rpt newRpt(Dominio dominio, String iuv, String ccp, boolean conRpt) throws JAXBException {
        xmlRpt = xmlRptBytes();
        xmlRt = xmlRtBytes();
        OffsetDateTime data = OffsetDateTime.of(2026, 6, 20, 12, 0, 0, 0, ZoneOffset.UTC);
        Versamento v = new Versamento();
        v.setCodVersamentoEnte("PEND-" + ccp);
        v.setImportoTotale(123.45);
        v.setImportoPagato(123.45);
        v.setStatoVersamento("ESEGUITO");
        v.setDataCreazione(data);
        v.setDataOraUltimoAggiornamento(data);
        v.setDebitoreIdentificativo("RSSMRA80A01H501U");
        v.setDebitoreAnagrafica("Mario Rossi");
        v.setSrcDebitoreIdentificativo("RSSMRA80A01H501U");
        v.setAnomalo(false);
        v.setAck(true);
        v.setTipo("DOVUTO");
        v.setCausaleVersamento("TARI 2026");
        v.setDominio(dominio);
        v.setApplicazione(app);
        v.setTipoVersamento(tv);
        v.setTipoVersamentoDominio(tvd);
        versamentoRepository.save(v);

        Rpt r = new Rpt();
        r.setIuv(iuv);
        r.setCcp(ccp);
        r.setCodDominio(dominio.getCodDominio());
        r.setXmlRpt(conRpt ? xmlRpt : null);
        r.setXmlRt(xmlRt);
        r.setCodEsitoPagamento(0);
        r.setImportoTotalePagato(123.45);
        r.setDataMsgRichiesta(data.minusHours(1));
        r.setDataMsgRicevuta(data);
        r.setVersione("SANP_240");
        r.setStato("RT_ACCETTATA_PA");
        r.setDescrizioneStato("Ricevuta accettata dalla PA");
        r.setCodPsp("PSP-X");
        r.setVersamento(v);
        return rptRepository.save(r);
    }

    private static byte[] xmlRptBytes() throws JAXBException {
        CtPaymentPA data = new CtPaymentPA();
        data.setCreditorReferenceId("IUV-RPT");
        PaGetPaymentRes res = new PaGetPaymentRes();
        res.setData(data);
        return marshal(res);
    }

    private static byte[] xmlRtBytes() throws JAXBException {
        CtReceipt receipt = new CtReceipt();
        receipt.setReceiptId("REC-1");
        receipt.setOutcome(StOutcome.OK);
        receipt.setPaymentDateTime(LocalDateTime.of(2026, 6, 20, 12, 0, 0));
        PaSendRTReq req = new PaSendRTReq();
        req.setReceipt(receipt);
        return marshal(req);
    }

    private static byte[] marshal(Object jaxbRoot) throws JAXBException {
        Marshaller m = JAXBContext.newInstance(PA_FOR_NODE_PKG).createMarshaller();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        m.marshal(jaxbRoot, baos);
        return baos.toByteArray();
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
