package it.govpay.console.ricevuta;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
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
import it.govpay.pendenze.entity.Rpt;
import it.govpay.console.entity.TipoVersamento;
import it.govpay.console.entity.TipoVersamentoDominio;
import it.govpay.console.entity.Utenza;
import it.govpay.pendenze.entity.Pendenza;
import it.govpay.pendenze.model.StatoPendenza;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import it.govpay.console.pendenza.PendenzaV3Fixtures;
import it.govpay.console.pendenza.PendenzaV3Fixtures.Dati;
import it.govpay.console.repository.ConsoleApplicazioneRepository;
import it.govpay.console.repository.ConsoleDominioRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.ConsoleRptRepository;
import it.govpay.console.repository.TipoVersamentoDominioRepository;
import it.govpay.console.repository.TipoVersamentoRepository;
import it.govpay.console.repository.UtenzaRepository;
import it.govpay.common.auth.GovpayPasswordEncoder;
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
    @Autowired private ConsoleDominioRepository dominioRepository;
    @Autowired private ConsoleApplicazioneRepository applicazioneRepository;
    @Autowired private TipoVersamentoRepository tipoVersamentoRepository;
    @Autowired private TipoVersamentoDominioRepository tipoVersamentoDominioRepository;
    @PersistenceContext private EntityManager em;
    @Autowired private ConsoleRptRepository rptRepository;

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

    private Pendenza newPendenza(String idPendenza) {
        Pendenza v = PendenzaV3Fixtures.pendenza(em, new Dati(app.getId(), dom.getId(),
                tv.getId(), tvd.getId(), null, idPendenza, "012345678901234567", "RSSMRA80A01H501U", 100.0,
                StatoPendenza.ESEGUITO, 0));
        v.setImportoPagato(100.0);
        v.setDataCreazione(OffsetDateTime.now());
        v.setDataUltimoAggiornamento(OffsetDateTime.now());
        v.getOpzionePagamento().getPdDebitore().getDebitore().setAnagrafica("Mario Rossi");
        v.getOpzionePagamento().getPdDebitore().getDebitore().setIndirizzo("Via Roma");
        v.getOpzionePagamento().getPdDebitore().getDebitore().setCivico("1");
        v.getOpzionePagamento().getPdDebitore().getDebitore().setCap("00100");
        v.getOpzionePagamento().getPdDebitore().getDebitore().setLocalita("Roma");
        v.getOpzionePagamento().getPdDebitore().getDebitore().setProvincia("RM");
        v.setCausaleVersamento("TARI 2026 — saldo");
        v.setIuv("1234567890123");
        return v;
    }

    private Rpt newRpt(Pendenza v, String iuv, String ccp,
                       int esito, OffsetDateTime dataRicevuta, byte[] xmlRt) {
        Rpt r = new Rpt();
        r.setCodStazione("STAZIONE-TEST");
        r.setCodMsgRichiesta("MSG-RICHIESTA");
        r.setDataAggiornamentoStato(java.time.OffsetDateTime.now());
        r.setIuv(iuv);
        r.setIur(ccp);
        r.setCodDominio(DOM);
        r.setXmlRt(xmlRt);
        r.setCodEsitoPagamento(esito);
        r.setImportoTotalePagato(100.0);
        r.setDataMsgRichiesta(dataRicevuta.minusHours(1));
        r.setDataMsgRicevuta(dataRicevuta);
        r.setIdVersamento(v.getId());
        r.setVersione("SANP_240");
        r.setStato("RT_ACCETTATA_PA");
        r.setDescrizioneStato("Ricevuta accettata dalla PA");
        r.setDenominazioneAttestante("Banca Test S.p.A.");
        r.setCodPsp("BNCITEST01");
        r.setCodTransazioneRt("TX-001");
        return rptRepository.save(r);
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-1/ricevute"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listReturnsSummariesMetadataOnly() throws Exception {
        Pendenza v = newPendenza("PEND-1");
        newRpt(v, "1234567890123", "CCP-001", 0, OffsetDateTime.now(), XML_RT);

        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-1/ricevute")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].idDominio", is(DOM)))
                .andExpect(jsonPath("$[0].iuv", is("1234567890123")))
                .andExpect(jsonPath("$[0].idRicevuta", is("CCP-001")))
                .andExpect(jsonPath("$[0].importo", is(100.0)))
                .andExpect(jsonPath("$[0].codPsp", is("BNCITEST01")))
                .andExpect(jsonPath("$[0].versione", is("1.0")))
                .andExpect(jsonPath("$[0].stato", is("RT_ACCETTATA_PA")))
                .andExpect(jsonPath("$[0].descrizioneStato", is("Ricevuta accettata dalla PA")))
                // metadata-only: nessun dato personale ne' XML nel summary; campi V1 rinominati assenti.
                .andExpect(jsonPath("$[0].ccp").doesNotExist())
                .andExpect(jsonPath("$[0].importoTotalePagato").doesNotExist())
                .andExpect(jsonPath("$[0].esito").doesNotExist())
                .andExpect(jsonPath("$[0].idPsp").doesNotExist())
                .andExpect(jsonPath("$[0].causale").doesNotExist())
                .andExpect(jsonPath("$[0].xmlRt").doesNotExist());
    }

    @Test
    void listOrdersByDataPagamentoDesc() throws Exception {
        Pendenza v = newPendenza("PEND-MULTI");
        newRpt(v, "OLD", "CCP-OLD", 0, OffsetDateTime.now().minusDays(2), XML_RT);
        newRpt(v, "NEW", "CCP-NEW", 0, OffsetDateTime.now(), XML_RT);

        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-MULTI/ricevute")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].iuv", contains("NEW", "OLD")));
    }

    @Test
    void listIncludesAllRtRegardlessOfEsito() throws Exception {
        Pendenza v = newPendenza("PEND-ALL");
        newRpt(v, "OK", "CCP-OK", 0, OffsetDateTime.now(), XML_RT);
        newRpt(v, "KO", "CCP-KO", 1 /* Non eseguito */, OffsetDateTime.now().minusHours(1), XML_RT);

        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-ALL/ricevute")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void listReturnsEmptyArrayWhenNoRt() throws Exception {
        newPendenza("PEND-NO-RT");
        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-NO-RT/ricevute")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void returns404WhenPendenzaDoesNotExist() throws Exception {
        mvc.perform(get("/pendenze/" + APP_COD + "/UNKNOWN/ricevute")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType("application/problem+json"));
    }
}
