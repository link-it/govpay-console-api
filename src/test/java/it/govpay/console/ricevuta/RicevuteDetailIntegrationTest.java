package it.govpay.console.ricevuta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import it.gov.pagopa.pagopa_api.pa.pafornode.CtPaymentPA;
import it.gov.pagopa.pagopa_api.pa.pafornode.CtReceipt;
import it.gov.pagopa.pagopa_api.pa.pafornode.PaGetPaymentRes;
import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTReq;
import it.gov.pagopa.pagopa_api.pa.pafornode.StOutcome;
import it.govpay.common.auth.GovpayPasswordEncoder;
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
 * Integration test del dettaglio {@code GET /ricevute/{idDominio}/{iuv}/{idRicevuta}}
 * (scope C issue #12): forma ricca con rpt/rt JSON convertiti da XML reale, _links,
 * ACL anti-leak e audit {@code RICEVUTA_VISUALIZZA}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RicevuteDetailIntegrationTest {

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

    private Dominio dom;
    private Dominio domAltro;
    private Applicazione app;
    private TipoVersamento tv;
    private TipoVersamentoDominio tvd;
    private Operatore operatore;

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
        operatore = attachOperatore("op-all", u);
    }

    @Test
    void dettaglio200ConRptRtJsonInline() throws Exception {
        Rpt rpt = newRpt(dom, "IUV0000001", "CCP-1");

        mvc.perform(get("/ricevute/" + DOM + "/IUV0000001/CCP-1").with(httpBasic("op-all", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("max-age=60")))
                // metadati
                .andExpect(jsonPath("$.idDominio", org.hamcrest.Matchers.is(DOM)))
                .andExpect(jsonPath("$.iuv", org.hamcrest.Matchers.is("IUV0000001")))
                .andExpect(jsonPath("$.idRicevuta", org.hamcrest.Matchers.is("CCP-1")))
                .andExpect(jsonPath("$.versione", org.hamcrest.Matchers.is("1.0")))
                .andExpect(jsonPath("$.stato", org.hamcrest.Matchers.is("RT_ACCETTATA_PA")))
                // rpt/rt JSON convertiti dall'XML reale
                .andExpect(jsonPath("$.rpt.creditorReferenceId", org.hamcrest.Matchers.is("IUV-RPT")))
                .andExpect(jsonPath("$.rt.receiptId", org.hamcrest.Matchers.is("REC-1")))
                .andExpect(jsonPath("$.rt.outcome", org.hamcrest.Matchers.is("OK")))
                // pendenza ref + links
                .andExpect(jsonPath("$.pendenza.idA2A", org.hamcrest.Matchers.is("APP-1")))
                .andExpect(jsonPath("$.pendenza.idPendenza", org.hamcrest.Matchers.is("PEND-CCP-1")))
                .andExpect(jsonPath("$._links.rpt.href",
                        org.hamcrest.Matchers.is("/ricevute/" + DOM + "/IUV0000001/CCP-1/rpt")))
                .andExpect(jsonPath("$._links.rt.href",
                        org.hamcrest.Matchers.is("/ricevute/" + DOM + "/IUV0000001/CCP-1/rt")))
                .andExpect(jsonPath("$._links.pendenza.href",
                        org.hamcrest.Matchers.is("/pendenze/APP-1/PEND-CCP-1")));

        assertThat(rpt.getId()).isNotNull();
    }

    @Test
    void dettaglio404SeSolaRichiestaSenzaRt() throws Exception {
        // riga rpt con sola richiesta: non è una ricevuta → 404, niente audit
        Versamento v = new Versamento();
        v.setCodVersamentoEnte("PEND-NORT");
        v.setImportoTotale(10.0);
        v.setImportoPagato(0.0);
        v.setStatoVersamento("NON_ESEGUITO");
        v.setDataCreazione(OffsetDateTime.of(2026, 6, 19, 12, 0, 0, 0, ZoneOffset.UTC));
        v.setDataOraUltimoAggiornamento(v.getDataCreazione());
        v.setDebitoreIdentificativo("RSSMRA80A01H501U");
        v.setDebitoreAnagrafica("Mario Rossi");
        v.setSrcDebitoreIdentificativo("RSSMRA80A01H501U");
        v.setAnomalo(false);
        v.setAck(true);
        v.setTipo("DOVUTO");
        v.setDominio(dom);
        v.setApplicazione(app);
        v.setTipoVersamento(tv);
        v.setTipoVersamentoDominio(tvd);
        versamentoRepository.save(v);
        Rpt r = new Rpt();
        r.setIuv("NORTIUV0002");
        r.setCcp("CCP-NORT2");
        r.setCodDominio(DOM);
        r.setXmlRpt("<RPT/>".getBytes());
        r.setXmlRt(null);
        r.setDataMsgRichiesta(v.getDataCreazione());
        r.setVersione("SANP_240");
        r.setStato("RPT_ATTIVATA");
        r.setVersamento(v);
        rptRepository.save(r);

        mvc.perform(get("/ricevute/" + DOM + "/NORTIUV0002/CCP-NORT2").with(httpBasic("op-all", PASSWORD)))
                .andExpect(status().isNotFound());
        assertThat(auditPerIuv("NORTIUV0002")).isEmpty();
    }

    @Test
    void dettaglio200RptNullSeStandinSenzaRichiesta() throws Exception {
        // ricevuta valida (xml_rt presente) ma senza RPT (standin, xml_rpt null):
        // rpt è l'unico campo che può mancare → "rpt": null, rt valorizzato.
        Versamento v = new Versamento();
        v.setCodVersamentoEnte("PEND-STANDIN");
        v.setImportoTotale(123.45);
        v.setImportoPagato(123.45);
        v.setStatoVersamento("ESEGUITO");
        v.setDataCreazione(OffsetDateTime.of(2026, 6, 20, 12, 0, 0, 0, ZoneOffset.UTC));
        v.setDataOraUltimoAggiornamento(v.getDataCreazione());
        v.setDebitoreIdentificativo("RSSMRA80A01H501U");
        v.setDebitoreAnagrafica("Mario Rossi");
        v.setSrcDebitoreIdentificativo("RSSMRA80A01H501U");
        v.setAnomalo(false);
        v.setAck(true);
        v.setTipo("DOVUTO");
        v.setCausaleVersamento("TARI 2026");
        v.setDominio(dom);
        v.setApplicazione(app);
        v.setTipoVersamento(tv);
        v.setTipoVersamentoDominio(tvd);
        versamentoRepository.save(v);
        Rpt r = new Rpt();
        r.setIuv("IUVSTANDIN1");
        r.setCcp("CCP-ST");
        r.setCodDominio(DOM);
        r.setXmlRpt(null);
        r.setXmlRt(xmlRt());
        r.setCodEsitoPagamento(0);
        r.setImportoTotalePagato(123.45);
        r.setDataMsgRichiesta(v.getDataCreazione().minusHours(1));
        r.setDataMsgRicevuta(v.getDataCreazione());
        r.setVersione("SANP_240");
        r.setStato("RT_ACCETTATA_PA");
        r.setVersamento(v);
        rptRepository.save(r);

        mvc.perform(get("/ricevute/" + DOM + "/IUVSTANDIN1/CCP-ST").with(httpBasic("op-all", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rpt", org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.rt.receiptId", org.hamcrest.Matchers.is("REC-1")));
    }

    @Test
    void ritorna404SeInesistenteSenzaAudit() throws Exception {
        mvc.perform(get("/ricevute/" + DOM + "/IUVZZZ/CCP-Z").with(httpBasic("op-all", PASSWORD)))
                .andExpect(status().isNotFound());
        assertThat(auditPerIuv("IUVZZZ")).isEmpty();
    }


    @Test
    void acl404SuDominioNonVisibileSenzaAudit() throws Exception {
        newRpt(dom, "IUVHIDDEN01", "CCP-H");
        // operatore visibile solo sull'altro dominio
        Utenza u = baseUtenza("op-altro", false);
        utenzaRepository.save(u);
        attachOperatore("op-altro", u);
        UtenzaDominio link = new UtenzaDominio();
        link.setIdUtenza(u.getId());
        link.setIdDominio(domAltro.getId());
        utenzaDominioRepository.save(link);

        mvc.perform(get("/ricevute/" + DOM + "/IUVHIDDEN01/CCP-H").with(httpBasic("op-altro", PASSWORD)))
                .andExpect(status().isNotFound());
        assertThat(auditPerIuv("IUVHIDDEN01")).isEmpty();
    }

    @Test
    void scriveAuditDopo200() throws Exception {
        Rpt rpt = newRpt(dom, "IUVAUDIT001", "CCP-AUD");

        mvc.perform(get("/ricevute/" + DOM + "/IUVAUDIT001/CCP-AUD")
                        .header("X-Forwarded-For", "9.9.9.9")
                        .with(httpBasic("op-all", PASSWORD)))
                .andExpect(status().isOk());

        List<GpAudit> rows = auditRicevuta().stream()
                .filter(a -> a.getOggetto() != null && a.getOggetto().contains("IUVAUDIT001"))
                .toList();
        assertThat(rows).hasSize(1);
        GpAudit row = rows.get(0);
        assertThat(row.getIdOggetto()).isEqualTo(rpt.getId());
        assertThat(row.getIdOperatore()).isEqualTo(operatore.getId());
        assertThat(row.getIpRichiedente()).isEqualTo("9.9.9.9");
        assertThat(row.getOggetto())
                .contains("\"idDominio\":\"" + DOM + "\"")
                .contains("\"iuv\":\"IUVAUDIT001\"")
                .contains("\"idRicevuta\":\"CCP-AUD\"")
                .contains("\"risorsa\":\"ricevuta\"");
    }

    // ----- helpers -----------------------------------------------------------

    private List<GpAudit> auditRicevuta() {
        return gpAuditRepository.findAll().stream()
                .filter(a -> RicevutaService.AZIONE_AUDIT_VISUALIZZA.equals(a.getTipoOggetto()))
                .toList();
    }

    /** Audit RICEVUTA_VISUALIZZA che riferiscono uno specifico iuv (robusto al
     *  commit cross-test degli audit REQUIRES_NEW). */
    private List<GpAudit> auditPerIuv(String iuv) {
        return auditRicevuta().stream()
                .filter(a -> a.getOggetto() != null && a.getOggetto().contains(iuv))
                .toList();
    }

    private Dominio newDominio(String cod, String ragione) {
        Dominio d = new Dominio();
        d.setCodDominio(cod);
        d.setRagioneSociale(ragione);
        d.setAuxDigit(0);
        return dominioRepository.save(d);
    }

    private Rpt newRpt(Dominio dominio, String iuv, String ccp) throws JAXBException {
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
        r.setXmlRpt(xmlRpt());
        r.setXmlRt(xmlRt());
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

    private static byte[] xmlRpt() throws JAXBException {
        CtPaymentPA data = new CtPaymentPA();
        data.setCreditorReferenceId("IUV-RPT");
        PaGetPaymentRes res = new PaGetPaymentRes();
        res.setData(data);
        return marshal(res);
    }

    private static byte[] xmlRt() throws JAXBException {
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

    private Operatore attachOperatore(String nome, Utenza u) {
        Operatore op = new Operatore();
        op.setNome(nome);
        op.setIdUtenza(u.getId());
        return operatoreRepository.save(op);
    }
}
