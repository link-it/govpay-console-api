package it.govpay.console.ricevuta.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import it.govpay.common.auth.GovpayPasswordEncoder;
import it.gov.pagopa.pagopa_api.pa.pafornode.CtFaultBean;
import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Response;
import it.gov.pagopa.pagopa_api.pa.pafornode.StOutcome;
import it.govpay.console.entity.Acl;
import it.govpay.console.entity.Applicazione;
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.Intermediario;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Rpt;
import it.govpay.console.entity.Stazione;
import it.govpay.console.entity.TipoVersamento;
import it.govpay.console.entity.TipoVersamentoDominio;
import it.govpay.console.entity.Utenza;
import it.govpay.console.entity.Versamento;
import it.govpay.console.model.AclServizio;
import it.govpay.console.repository.AclRepository;
import it.govpay.console.repository.ApplicazioneRepository;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.repository.GpAuditRepository;
import it.govpay.console.repository.IntermediarioRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.RptRepository;
import it.govpay.console.repository.StazioneRepository;
import it.govpay.console.repository.TipoVersamentoDominioRepository;
import it.govpay.console.repository.TipoVersamentoRepository;
import it.govpay.console.repository.UtenzaRepository;
import it.govpay.console.repository.VersamentoRepository;

/**
 * {@code POST /ricevute} (issue #59 par. A-G): {@link PaForNodeClient}
 * mockato, {@code govpay-core} non esiste in questo repo. La "acquisizione"
 * lato core viene simulata scrivendo direttamente su {@code Rpt}
 * (xmlRt/dataMsgRicevuta) dentro lo stub del client, cosi' come
 * {@code RecuperoRicevutaEsitoIntegrationTest} simula il batch — la rilettura
 * di {@link it.govpay.console.ricevuta.RicevutaService#getDetail} trova
 * quindi uno stato coerente con un'acquisizione reale.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RicevutaUploadIntegrationTest {

    private static final String PASSWORD = "secret";
    // Allineato ai fixture di src/test/resources/rt-*.{xml,json} (issue #59 par. J).
    private static final String DOM = "12345678901";

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
    @Autowired private AclRepository aclRepository;
    @Autowired private StazioneRepository stazioneRepository;
    @Autowired private IntermediarioRepository intermediarioRepository;
    @Autowired private GpAuditRepository gpAuditRepository;

    @MockitoBean private PaForNodeClient paForNodeClient;

    private Dominio dominio;
    private Applicazione app;
    private TipoVersamento tv;
    private TipoVersamentoDominio tvd;
    private Intermediario intermediario;
    private Stazione stazione;

    @BeforeEach
    void setup() {
        dominio = new Dominio();
        dominio.setCodDominio(DOM);
        dominio.setRagioneSociale("Dominio Upload");
        dominio.setAuxDigit(0);
        dominioRepository.save(dominio);

        intermediario = new Intermediario();
        intermediario.setCodIntermediario("INT-UPLOAD");
        intermediario.setDenominazione("Intermediario Upload");
        intermediario.setPrincipal("p-int-upload");
        intermediario.setPrincipalOriginale("p-int-upload");
        intermediario.setCodConnettorePdd("CONN-UPLOAD");
        intermediario.setAbilitato(true);
        intermediarioRepository.save(intermediario);

        stazione = new Stazione();
        stazione.setCodStazione("STAZ-UPLOAD");
        stazione.setApplicationCode(1);
        stazione.setVersione("2.0");
        stazione.setAbilitato(true);
        stazione.setPassword("");
        stazione.setIntermediario(intermediario);
        stazioneRepository.save(stazione);

        dominio.setStazione(stazione);
        dominioRepository.save(dominio);

        app = new Applicazione();
        app.setCodApplicazione("APP-UPLOAD");
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
        rptRepository.findAll().stream()
                .filter(r -> DOM.equals(r.getCodDominio()))
                .forEach(rptRepository::delete);
        versamentoRepository.findAll().stream()
                .filter(v -> dominio.getId().equals(v.getDominio().getId()))
                .forEach(versamentoRepository::delete);
        tipoVersamentoDominioRepository.delete(tvd);
        tipoVersamentoRepository.delete(tv);
        applicazioneRepository.delete(app);
        dominio.setStazione(null);
        dominioRepository.save(dominio);
        dominioRepository.delete(dominio);
        stazioneRepository.delete(stazione);
        intermediarioRepository.delete(intermediario);
    }

    // Contenuto completo (tutti i campi obbligatori dello schema paForNode.xsd):
    // dopo la reintroduzione della validazione XSD (RicevutaXmlValidator) un
    // fixture minimo come nelle versioni precedenti di questo file non basta
    // piu', verrebbe respinto con 422 prima ancora dell'invio.
    private static String receiptBody(String iuv, String idRicevuta) {
        return """
                <receiptId>%s</receiptId>
                <noticeNumber>311000000000000000</noticeNumber>
                <fiscalCode>%s</fiscalCode>
                <outcome>OK</outcome>
                <creditorReferenceId>%s</creditorReferenceId>
                <paymentAmount>10.00</paymentAmount>
                <description>Pagamento TARI</description>
                <companyName>Comune di Prova</companyName>
                <debtor>
                  <uniqueIdentifier>
                    <entityUniqueIdentifierType>F</entityUniqueIdentifierType>
                    <entityUniqueIdentifierValue>RSSMRA80A01H501U</entityUniqueIdentifierValue>
                  </uniqueIdentifier>
                  <fullName>Mario Rossi</fullName>
                </debtor>
                <transferList>
                  <transfer>
                    <idTransfer>1</idTransfer>
                    <transferAmount>10.00</transferAmount>
                    <fiscalCodePA>%s</fiscalCodePA>
                    <IBAN>IT60X0542811101000000123456</IBAN>
                    <remittanceInformation>TARI saldo</remittanceInformation>
                    <transferCategory>9/0101108TS/</transferCategory>
                  </transfer>
                </transferList>
                <idPSP>PSP_FIXTURE</idPSP>
                <PSPCompanyName>PSP di Prova SpA</PSPCompanyName>
                <idChannel>CHANNEL_FIXTURE</idChannel>
                <channelDescription>App IO</channelDescription>
                """.formatted(idRicevuta, DOM, iuv, DOM);
    }

    // Radice qualificata col prefisso tns, MAI xmlns="..." come default: lo schema
    // non dichiara elementFormDefault, quindi vale il default "unqualified" per gli
    // elementi locali (idPA, receipt, ...) - solo gli elementi globali (la radice)
    // vivono nel target namespace. Un xmlns="..." di default sulla radice
    // qualificherebbe per errore anche i figli, e la validazione XSD li rifiuta.
    private static final String TNS = "http://pagopa-api.pagopa.gov.it/pa/paForNode.xsd";

    private static String xmlV22(String iuv, String idRicevuta) {
        return """
                <tns:paSendRTV2Request xmlns:tns="%s">
                  <idPA>%s</idPA>
                  <idBrokerPA>INTERMEDIARIO_FIXTURE</idBrokerPA>
                  <idStation>STAZIONE_FIXTURE</idStation>
                  <receipt>
                %s
                  </receipt>
                </tns:paSendRTV2Request>
                """.formatted(TNS, DOM, receiptBody(iuv, idRicevuta));
    }

    private static String xmlV2(String iuv, String idRicevuta) {
        return """
                <tns:paSendRTReq xmlns:tns="%s">
                  <idPA>%s</idPA>
                  <idBrokerPA>INTERMEDIARIO_FIXTURE</idBrokerPA>
                  <idStation>STAZIONE_FIXTURE</idStation>
                  <receipt>
                %s
                  </receipt>
                </tns:paSendRTReq>
                """.formatted(TNS, DOM, receiptBody(iuv, idRicevuta));
    }

    private static String xmlSanp230() {
        return "<RT><foo/></RT>";
    }

    private static String jsonRicevuta(String iuv, String idRicevuta) {
        return """
                {
                  "receiptId": "%s",
                  "noticeNumber": "311000000000000000",
                  "fiscalCode": "%s",
                  "outcome": "OK",
                  "creditorReferenceId": "%s",
                  "paymentAmount": 10.00,
                  "description": "Pagamento TARI",
                  "companyName": "Comune di Prova",
                  "debtor": {
                    "fullName": "Mario Rossi",
                    "entityUniqueIdentifierType": "F",
                    "entityUniqueIdentifierValue": "RSSMRA80A01H501U"
                  },
                  "transferList": [
                    {
                      "idTransfer": "1",
                      "fiscalCodePA": "%s",
                      "iban": "IT60X0542811101000000123456",
                      "remittanceInformation": "causale",
                      "transferAmount": 10.00,
                      "transferCategory": "cat"
                    }
                  ],
                  "idPSP": "psp-1",
                  "pspCompanyName": "PSP di Prova",
                  "idChannel": "channel-1",
                  "paymentDateTimeFormatted": "2026-09-01T10:00:00.000+02:00"
                }
                """.formatted(idRicevuta, DOM, iuv, DOM);
    }

    private void mockAcquisizioneOk(String iuv, String idRicevuta) {
        PaSendRTV2Response ok = new PaSendRTV2Response();
        ok.setOutcome(StOutcome.OK);
        when(paForNodeClient.inviaRicevutaV2(any())).thenAnswer(inv -> {
            acquisisci(iuv, idRicevuta);
            return ok;
        });
        when(paForNodeClient.inviaRicevutaXml(any(), any())).thenAnswer(inv -> {
            acquisisci(iuv, idRicevuta);
            return ok;
        });
    }

    private void acquisisci(String iuv, String idRicevuta) {
        rptRepository.findByDominioAndIuv(DOM, iuv).ifPresent(rpt -> {
            rpt.setCcp(idRicevuta);
            rpt.setXmlRt("<RT/>".getBytes(StandardCharsets.UTF_8));
            rpt.setDataMsgRicevuta(OffsetDateTime.now());
            rptRepository.save(rpt);
        });
    }

    @Test
    void xmlV2_2VieneInoltratoConPaSendRTV2ERitorna201() throws Exception {
        Versamento v = newVersamento("PEND-V22");
        newRptSenzaRt(v, "IUV-V22", "CCP-V22");
        mockAcquisizioneOk("IUV-V22", "IUR-V22");
        String p = utenteScrittura("u-v22");

        mvc.perform(uploadXml(xmlV22("IUV-V22", "IUR-V22"), p))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", Matchers.endsWith("/ricevute/" + DOM + "/IUV-V22/IUR-V22")))
                .andExpect(jsonPath("$.iuv", Matchers.is("IUV-V22")));

        verify(paForNodeClient).inviaRicevutaXml(any(), eq(RicevutaFormato.V2_2));
        verify(paForNodeClient, never()).inviaRicevutaV2(any());
    }

    @Test
    void xmlV2VieneInoltratoConPaSendRTNonPaSendRTV2() throws Exception {
        Versamento v = newVersamento("PEND-V2");
        newRptSenzaRt(v, "IUV-V2", "CCP-V2");
        mockAcquisizioneOk("IUV-V2", "IUR-V2");
        String p = utenteScrittura("u-v2");

        mvc.perform(uploadXml(xmlV2("IUV-V2", "IUR-V2"), p))
                .andExpect(status().isCreated());

        verify(paForNodeClient).inviaRicevutaXml(any(), eq(RicevutaFormato.V2));
        verify(paForNodeClient, never()).inviaRicevutaV2(any());
    }

    @Test
    void jsonVieneConvertitoEInviatoConPaSendRTV2Tipizzato() throws Exception {
        Versamento v = newVersamento("PEND-JSON");
        newRptSenzaRt(v, "IUV-JSON", "CCP-JSON");
        mockAcquisizioneOk("IUV-JSON", "IUR-JSON");
        String p = utenteScrittura("u-json");

        mvc.perform(uploadJson(jsonRicevuta("IUV-JSON", "IUR-JSON"), p))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.iuv", Matchers.is("IUV-JSON")));

        verify(paForNodeClient).inviaRicevutaV2(any());
        verify(paForNodeClient, never()).inviaRicevutaXml(any(), any());
    }

    @Test
    void multipartConCampoFileVieneRiconosciutoDalContenuto() throws Exception {
        Versamento v = newVersamento("PEND-MP");
        newRptSenzaRt(v, "IUV-MP", "CCP-MP");
        mockAcquisizioneOk("IUV-MP", "IUR-MP");
        String p = utenteScrittura("u-mp");

        MockMultipartFile file = new MockMultipartFile("file", "ricevuta.xml", "application/octet-stream",
                xmlV22("IUV-MP", "IUR-MP").getBytes(StandardCharsets.UTF_8));
        MockMultipartHttpServletRequestBuilder builder = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart("/ricevute").file(file).with(httpBasic(p, PASSWORD));

        mvc.perform(builder).andExpect(status().isCreated());
    }

    @Test
    void ricevutaGiaAcquisitaRitorna409SenzaChiamareIlClient() throws Exception {
        Versamento v = newVersamento("PEND-DUP");
        Rpt rpt = newRptSenzaRt(v, "IUV-DUP", "CCP-DUP");
        rpt.setCcp("IUR-DUP");
        rpt.setXmlRt("<RT/>".getBytes(StandardCharsets.UTF_8));
        rpt.setDataMsgRicevuta(OffsetDateTime.now());
        rptRepository.save(rpt);
        String p = utenteScrittura("u-dup");

        mvc.perform(uploadXml(xmlV22("IUV-DUP", "IUR-DUP"), p))
                .andExpect(status().isConflict());

        verify(paForNodeClient, never()).inviaRicevutaV2(any());
        verify(paForNodeClient, never()).inviaRicevutaXml(any(), any());
    }

    @Test
    void dominioNonVisibileRitorna403SenzaChiamareIlClient() throws Exception {
        Versamento v = newVersamento("PEND-403");
        newRptSenzaRt(v, "IUV-403", "CCP-403");
        String p = utenteScritturaSenzaAccessoDominio("u-403");

        mvc.perform(uploadXml(xmlV22("IUV-403", "IUR-403"), p))
                .andExpect(status().isForbidden());

        verify(paForNodeClient, never()).inviaRicevutaV2(any());
        verify(paForNodeClient, never()).inviaRicevutaXml(any(), any());
    }

    @Test
    void contentTypeNonSupportatoRitorna415() throws Exception {
        String p = utenteScrittura("u-415");
        mvc.perform(post("/ricevute").with(httpBasic(p, PASSWORD))
                        .contentType(MediaType.APPLICATION_PDF)
                        .content(new byte[] { 1, 2, 3 }))
                .andExpect(status().isUnsupportedMediaType());
    }

    /**
     * Review: un Content-Type fuori dal {@code consumes} del controller e'
     * respinto da Spring stesso (mapping), prima che
     * {@link RicevutaUploadService#upload} sia mai invocato — il suo blocco
     * try/audit non entra mai in gioco. {@link ProblemExceptionHandler}
     * scrive quindi l'audit direttamente in questo caso (unico modo per
     * coprirlo: verificato che allargare il {@code consumes} generato non e'
     * praticabile).
     */
    @Test
    void contentTypeNonSupportatoRitorna415EScriveAuditNonostantePreControllore() throws Exception {
        long before = countAudit("RICEVUTA_CARICA");
        String p = utenteScrittura("u-415-audit");

        mvc.perform(post("/ricevute").with(httpBasic(p, PASSWORD))
                        .contentType(MediaType.APPLICATION_PDF)
                        .content(new byte[] { 1, 2, 3 }))
                .andExpect(status().isUnsupportedMediaType());

        assertThat(countAudit("RICEVUTA_CARICA")).isEqualTo(before + 1);
        assertThat(ultimiEsitiAudit(1)).containsExactly("415");
    }

    /**
     * Review: {@code auditRicevutaCaricaSeApplicabile} confrontava
     * {@code getRequestURI()} (include l'eventuale context path del
     * deployment) con la costante letterale {@code "/ricevute"} — in
     * produzione, con un context path configurato, il confronto avrebbe
     * fallito silenziosamente e l'audit non sarebbe mai scattato. Nessun
     * context path e' configurato oggi in questo progetto: questo test lo
     * simula esplicitamente per non dipendere da quell'assenza.
     */
    @Test
    void contentTypeNonSupportatoScriveAuditAncheConContextPath() throws Exception {
        long before = countAudit("RICEVUTA_CARICA");
        String p = utenteScrittura("u-415-ctxpath");

        mvc.perform(post("/govpay-console-api/ricevute")
                        .contextPath("/govpay-console-api")
                        .with(httpBasic(p, PASSWORD))
                        .contentType(MediaType.APPLICATION_PDF)
                        .content(new byte[] { 1, 2, 3 }))
                .andExpect(status().isUnsupportedMediaType());

        assertThat(countAudit("RICEVUTA_CARICA")).isEqualTo(before + 1);
        assertThat(ultimiEsitiAudit(1)).containsExactly("415");
    }

    @Test
    void payloadOltreSogliaRitorna413() throws Exception {
        String p = utenteScrittura("u-413");
        byte[] tooBig = new byte[2 * 1024 * 1024];
        mvc.perform(post("/ricevute").with(httpBasic(p, PASSWORD))
                        .contentType(MediaType.APPLICATION_XML)
                        .content(tooBig))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void formatoSanp230Ritorna422() throws Exception {
        String p = utenteScrittura("u-sanp230");
        mvc.perform(uploadXml(xmlSanp230(), p))
                .andExpect(status().isUnprocessableEntity());
        verify(paForNodeClient, never()).inviaRicevutaXml(any(), any());
    }

    @Test
    void xmlMalformatoRitorna400() throws Exception {
        String p = utenteScrittura("u-malformato");
        mvc.perform(uploadXml("<paSendRTV2Request><receipt>", p))
                .andExpect(status().isBadRequest());
    }

    @Test
    void timeoutConRtAcquisitaNelFrattempoRitorna201() throws Exception {
        Versamento v = newVersamento("PEND-TMO201");
        newRptSenzaRt(v, "IUV-TMO201", "CCP-TMO201");
        when(paForNodeClient.inviaRicevutaXml(any(), any())).thenAnswer(inv -> {
            acquisisci("IUV-TMO201", "IUR-TMO201");
            throw new PaForNodeTransportException("timeout simulato", new java.io.IOException("timeout"), true);
        });
        String p = utenteScrittura("u-tmo201");

        mvc.perform(uploadXml(xmlV22("IUV-TMO201", "IUR-TMO201"), p))
                .andExpect(status().isCreated());
    }

    @Test
    void timeoutConRtAssenteRitorna504() throws Exception {
        Versamento v = newVersamento("PEND-TMO504");
        newRptSenzaRt(v, "IUV-TMO504", "CCP-TMO504");
        when(paForNodeClient.inviaRicevutaXml(any(), any()))
                .thenThrow(new PaForNodeTransportException("timeout simulato", new java.io.IOException("timeout"), true));
        String p = utenteScrittura("u-tmo504");

        mvc.perform(uploadXml(xmlV22("IUV-TMO504", "IUR-TMO504"), p))
                .andExpect(status().isGatewayTimeout());
    }

    @Test
    void erroreTrasportoConRtAssenteRitorna502() throws Exception {
        Versamento v = newVersamento("PEND-502");
        newRptSenzaRt(v, "IUV-502", "CCP-502");
        when(paForNodeClient.inviaRicevutaXml(any(), any()))
                .thenThrow(new PaForNodeTransportException("errore simulato", new java.io.IOException("connessione rifiutata"), false));
        String p = utenteScrittura("u-502");

        mvc.perform(uploadXml(xmlV22("IUV-502", "IUR-502"), p))
                .andExpect(status().isBadGateway());
    }

    @Test
    void faultInBandRitorna422SenzaAcquisizione() throws Exception {
        Versamento v = newVersamento("PEND-FAULT");
        newRptSenzaRt(v, "IUV-FAULT", "CCP-FAULT");
        PaSendRTV2Response ko = new PaSendRTV2Response();
        ko.setOutcome(StOutcome.KO);
        CtFaultBean fault = new CtFaultBean();
        fault.setFaultCode("PPT_SEMANTICA");
        fault.setFaultString("RT non valida");
        ko.setFault(fault);
        when(paForNodeClient.inviaRicevutaXml(any(), any())).thenThrow(
                new it.govpay.console.web.UnprocessableEntityException("RT rifiutata da api-pagopa: PPT_SEMANTICA - RT non valida"));
        String p = utenteScrittura("u-fault");

        mvc.perform(uploadXml(xmlV22("IUV-FAULT", "IUR-FAULT"), p))
                .andExpect(status().isUnprocessableEntity());

        assertThat(rptRepository.findByKey(DOM, "IUV-FAULT", "IUR-FAULT")).isEmpty();
    }

    @Test
    void auditRegistratoSuSuccessoESuFallimento() throws Exception {
        long before = countAudit("RICEVUTA_CARICA");

        Versamento v = newVersamento("PEND-AUDIT");
        newRptSenzaRt(v, "IUV-AUDIT", "CCP-AUDIT");
        mockAcquisizioneOk("IUV-AUDIT", "IUR-AUDIT");
        String p = utenteScrittura("u-audit");
        mvc.perform(uploadXml(xmlV22("IUV-AUDIT", "IUR-AUDIT"), p)).andExpect(status().isCreated());

        mvc.perform(uploadXml(xmlSanp230(), p)).andExpect(status().isUnprocessableEntity());

        assertThat(countAudit("RICEVUTA_CARICA")).isEqualTo(before + 2);
    }

    /**
     * Review: {@code esitoDa()} non copriva 413/415, l'esito scritto in
     * {@code gp_audit} era "500" anche se la risposta HTTP era corretta.
     * Questo test copre il 413 sollevato dentro
     * {@link RicevutaUploadService} stesso (body XML oltre soglia); il 415 —
     * e il 413 sollevato invece da Spring prima del controller — sono
     * coperti separatamente, vedi
     * {@link #contentTypeNonSupportatoRitorna415EScriveAuditNonostantePreControllore}.
     */
    @Test
    void auditRegistratoConEsitoCorrettoSu413() throws Exception {
        long before = countAudit("RICEVUTA_CARICA");
        String p = utenteScrittura("u-audit-413");

        mvc.perform(post("/ricevute").with(httpBasic(p, PASSWORD))
                        .contentType(MediaType.APPLICATION_XML)
                        .content(new byte[2 * 1024 * 1024]))
                .andExpect(status().isPayloadTooLarge());

        assertThat(countAudit("RICEVUTA_CARICA")).isEqualTo(before + 1);
        assertThat(ultimiEsitiAudit(1)).containsExactly("413");
    }

    /**
     * Review: un multipart oltre la soglia applicativa (ma sotto il tetto
     * Spring, ancorato al limite ben piu' ampio di /pendenze/tracciati)
     * raggiunge {@link RicevutaUploadService}, che risponde 413 e registra
     * l'audit — a differenza di un multipart che eccede anche il tetto
     * Spring, respinto prima ancora di raggiungere il controller (nessun
     * audit possibile in quel caso, gap noto e accettato).
     */
    @Test
    void multipartOltreSogliaApplicativaRitorna413EScriveAudit() throws Exception {
        long before = countAudit("RICEVUTA_CARICA");
        String p = utenteScrittura("u-mp-413");

        // 12MB: sopra il default Spring Boot di spring.servlet.multipart.max-request-size
        // (10MB) — se quella property non fosse allineata al tetto piu' ampio di
        // max-file-size, la richiesta verrebbe respinta da Spring stesso, prima di
        // raggiungere il controller, senza audit (stesso bug gia' corretto per
        // max-file-size, riemerso su max-request-size).
        MockMultipartFile file = new MockMultipartFile("file", "ricevuta.xml", "application/octet-stream",
                new byte[12 * 1024 * 1024]);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/ricevute").file(file).with(httpBasic(p, PASSWORD)))
                .andExpect(status().isPayloadTooLarge());

        assertThat(countAudit("RICEVUTA_CARICA")).isEqualTo(before + 1);
        assertThat(ultimiEsitiAudit(1)).containsExactly("413");
    }

    private long countAudit(String azione) {
        return gpAuditRepository.findAll().stream().filter(a -> azione.equals(a.getTipoOggetto())).count();
    }

    @SuppressWarnings("unchecked")
    private List<String> ultimiEsitiAudit(int quanti) {
        return gpAuditRepository.findAll().stream()
                .filter(a -> "RICEVUTA_CARICA".equals(a.getTipoOggetto()))
                .sorted(java.util.Comparator.comparing(it.govpay.console.entity.GpAudit::getId).reversed())
                .limit(quanti)
                .map(a -> (String) ((java.util.Map<String, Object>) readOggetto(a)).get("esito"))
                .toList();
    }

    private Object readOggetto(it.govpay.console.entity.GpAudit audit) {
        try {
            return new tools.jackson.databind.ObjectMapper().readValue(audit.getOggetto(), java.util.Map.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ----- test con le fixture di src/test/resources (issue #59 par. I/J) ---------------

    @Test
    void textXmlAliasVieneAccettatoERitorna201() throws Exception {
        Versamento v = newVersamento("PEND-TEXTXML");
        newRptSenzaRt(v, "IUV900000001", "CCP-TEXTXML");
        mockAcquisizioneOk("IUV900000001", "RT900000001");
        String p = utenteScrittura("u-textxml");

        mvc.perform(post("/ricevute").with(httpBasic(p, PASSWORD))
                        .contentType(MediaType.valueOf("text/xml"))
                        .content(fixture("rt-v2_2-ok.xml")))
                .andExpect(status().isCreated());
    }

    @Test
    void base64VieneDecodificatoERitorna201() throws Exception {
        Versamento v = newVersamento("PEND-B64");
        newRptSenzaRt(v, "IUV900000001", "CCP-B64");
        mockAcquisizioneOk("IUV900000001", "RT900000001");
        String p = utenteScrittura("u-b64");

        mvc.perform(post("/ricevute").with(httpBasic(p, PASSWORD))
                        .contentType(MediaType.APPLICATION_XML)
                        .content(fixture("rt-v2_2-base64.txt")))
                .andExpect(status().isCreated());
    }

    @Test
    void bustaSoapVieneSbustataERitorna201() throws Exception {
        Versamento v = newVersamento("PEND-SOAP");
        newRptSenzaRt(v, "IUV900000001", "CCP-SOAP");
        mockAcquisizioneOk("IUV900000001", "RT900000001");
        String p = utenteScrittura("u-soap");

        mvc.perform(post("/ricevute").with(httpBasic(p, PASSWORD))
                        .contentType(MediaType.APPLICATION_XML)
                        .content(fixture("rt-v2_2-soap.xml")))
                .andExpect(status().isCreated());
    }

    @Test
    void xxeConEntitaEsternaRitorna400SenzaRisoluzioneEntita() throws Exception {
        String p = utenteScrittura("u-xxe");
        mvc.perform(post("/ricevute").with(httpBasic(p, PASSWORD))
                        .contentType(MediaType.APPLICATION_XML)
                        .content(fixture("rt-xxe.xml")))
                .andExpect(status().isBadRequest());
        // Se l'entita' fosse stata risolta, il parsing sarebbe comunque proseguito
        // (nessuna verifica diretta sul contenuto di /etc/passwd e' possibile qui,
        // ma un 400 conferma che il parser hardenizzato ha rifiutato il DOCTYPE
        // invece di espanderlo).
    }

    @Test
    void radiceIgnotaRitorna422() throws Exception {
        String p = utenteScrittura("u-radice-ignota");
        mvc.perform(post("/ricevute").with(httpBasic(p, PASSWORD))
                        .contentType(MediaType.APPLICATION_XML)
                        .content(fixture("rt-radice-ignota.xml")))
                .andExpect(status().isUnprocessableEntity());
        verify(paForNodeClient, never()).inviaRicevutaXml(any(), any());
    }

    @Test
    void xmlMalformatoDaFixtureRitorna400() throws Exception {
        String p = utenteScrittura("u-malformato-fixture");
        mvc.perform(post("/ricevute").with(httpBasic(p, PASSWORD))
                        .contentType(MediaType.APPLICATION_XML)
                        .content(fixture("rt-malformato.xml")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void multipartSenzaCampoFileRitorna400() throws Exception {
        String p = utenteScrittura("u-multipart-vuoto");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/ricevute").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void jsonBizeventsCompletoRitorna201EInvocaPaSendRTV2Tipizzato() throws Exception {
        Versamento v = newVersamento("PEND-BIZ");
        newRptSenzaRt(v, "IUV900000005", "CCP-BIZ");
        mockAcquisizioneOk("IUV900000005", "RT900000005");
        String p = utenteScrittura("u-biz");

        mvc.perform(post("/ricevute").with(httpBasic(p, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture("rt-bizevents.json")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.iuv", Matchers.is("IUV900000005")));

        org.mockito.ArgumentCaptor<it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Request> captor =
                org.mockito.ArgumentCaptor.forClass(it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Request.class);
        verify(paForNodeClient).inviaRicevutaV2(captor.capture());
        it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Request inviata = captor.getValue();
        assertThat(inviata.getIdPA()).isEqualTo(DOM);
        assertThat(inviata.getIdStation()).isEqualTo(stazione.getCodStazione());
        assertThat(inviata.getIdBrokerPA()).isEqualTo(intermediario.getCodIntermediario());
        assertThat(inviata.getReceipt().getReceiptId()).isEqualTo("RT900000005");
        assertThat(inviata.getReceipt().getCreditorReferenceId()).isEqualTo("IUV900000005");
        assertThat(inviata.getReceipt().getFiscalCode()).isEqualTo(DOM);
    }

    @Test
    void jsonSenzaPaymentDateTimeFormattedRitorna400SenzaChiamareIlClient() throws Exception {
        String p = utenteScrittura("u-biz-nodata");
        mvc.perform(post("/ricevute").with(httpBasic(p, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture("rt-bizevents-senza-data-formatted.json")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("paymentDateTimeFormatted")));
        verify(paForNodeClient, never()).inviaRicevutaV2(any());
    }

    @Test
    void jsonConCampiMancantiRitorna400ChiElencaICampi() throws Exception {
        String p = utenteScrittura("u-biz-mancanti");
        mvc.perform(post("/ricevute").with(httpBasic(p, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture("rt-bizevents-campi-mancanti.json")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("noticeNumber")))
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("idPSP")));
        verify(paForNodeClient, never()).inviaRicevutaV2(any());
    }

    @Test
    void jsonNonConformeAlModelRitorna422() throws Exception {
        String p = utenteScrittura("u-biz-nonconforme");
        mvc.perform(post("/ricevute").with(httpBasic(p, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture("rt-bizevents-non-conforme.json")))
                .andExpect(status().isUnprocessableEntity());
        verify(paForNodeClient, never()).inviaRicevutaV2(any());
    }

    @Test
    void jsonMalformatoDaFixtureRitorna400() throws Exception {
        String p = utenteScrittura("u-json-malformato");
        mvc.perform(post("/ricevute").with(httpBasic(p, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture("rt-json-malformato.json")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void jsonMbtSenzaAttachmentRitorna422SenzaChiamareIlClient() throws Exception {
        String p = utenteScrittura("u-mbt-senza");
        mvc.perform(post("/ricevute").with(httpBasic(p, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture("rt-bizevents-mbt-senza-attachment.json")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("mbdAttachment")));
        verify(paForNodeClient, never()).inviaRicevutaV2(any());
    }

    @Test
    void jsonMbtConAttachmentValorizzatoRitorna201() throws Exception {
        Versamento v = newVersamento("PEND-MBT-OK");
        newRptSenzaRt(v, "IUV900000010", "CCP-MBT-OK");
        mockAcquisizioneOk("IUV900000010", "RT900000010");
        String p = utenteScrittura("u-mbt-ok");

        mvc.perform(post("/ricevute").with(httpBasic(p, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture("rt-bizevents-mbt-completa.json")))
                .andExpect(status().isCreated());
    }

    @Test
    void jsonConDominioNonVisibileRitorna403SenzaChiamareIlClient() throws Exception {
        String p = utenteScritturaSenzaAccessoDominio("u-biz-403");
        mvc.perform(post("/ricevute").with(httpBasic(p, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture("rt-bizevents.json")))
                .andExpect(status().isForbidden());
        verify(paForNodeClient, never()).inviaRicevutaV2(any());
    }

    private static byte[] fixture(String name) throws java.io.IOException {
        try (java.io.InputStream in = RicevutaUploadIntegrationTest.class.getClassLoader()
                .getResourceAsStream(name)) {
            if (in == null) {
                throw new java.io.FileNotFoundException(name);
            }
            return in.readAllBytes();
        }
    }

    // ----- request builders -------------------------------------------------------------

    private static MockHttpServletRequestBuilder uploadXml(String xml, String principal) {
        return post("/ricevute")
                .with(httpBasic(principal, PASSWORD))
                .contentType(MediaType.APPLICATION_XML)
                .content(xml.getBytes(StandardCharsets.UTF_8));
    }

    private static MockHttpServletRequestBuilder uploadJson(String json, String principal) {
        return post("/ricevute")
                .with(httpBasic(principal, PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.getBytes(StandardCharsets.UTF_8));
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

    private String utenteScritturaSenzaAccessoDominio(String principal) {
        Utenza u = new Utenza();
        u.setPrincipal(principal);
        u.setPrincipalOriginale(principal);
        u.setAbilitato(true);
        u.setAutorizzazioneDominiStar(false);
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
