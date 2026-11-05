package it.govpay.console.ricevuta;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.common.auth.GovpayPasswordEncoder;
import it.govpay.console.entity.Acl;
import it.govpay.console.entity.Applicazione;
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.Fr;
import it.govpay.console.entity.Incasso;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Pagamento;
import it.govpay.console.entity.Rendicontazione;
import it.govpay.console.entity.Rpt;
import it.govpay.console.entity.TipoVersamento;
import it.govpay.console.entity.TipoVersamentoDominio;
import it.govpay.console.entity.Utenza;
import it.govpay.console.entity.Versamento;
import it.govpay.console.model.AclServizio;
import it.govpay.console.repository.AclRepository;
import it.govpay.console.repository.ApplicazioneRepository;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.repository.FrRepository;
import it.govpay.console.repository.IncassoRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.PagamentoRepository;
import it.govpay.console.repository.RendicontazioneRepository;
import it.govpay.console.repository.RptRepository;
import it.govpay.console.repository.TipoVersamentoDominioRepository;
import it.govpay.console.repository.TipoVersamentoRepository;
import it.govpay.console.repository.UtenzaRepository;
import it.govpay.console.repository.VersamentoRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Integration test di {@code GET /ricevute/.../} per {@code voci[]}: ACL non
 * bloccante (omissione del campo, non 403/valore di default), le 3
 * precedenze di {@code statoRendicontazione}, esclusione delle righe di
 * flusso obsoleto, aggregazione di {@code anomalia}/{@code anomalie} su piu'
 * righe correnti.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RicevutaVociIntegrationTest {

    private static final String PASSWORD = "secret";
    private static final String DOM = "55566677788";
    private static final String APP_COD = "APP-VOCI";
    private static final byte[] XML_RT = "<RT><id>fake</id></RT>".getBytes(StandardCharsets.UTF_8);

    @Autowired private MockMvc mvc;
    @Autowired private GovpayPasswordEncoder encoder;
    @Autowired private UtenzaRepository utenzaRepository;
    @Autowired private OperatoreRepository operatoreRepository;
    @Autowired private AclRepository aclRepository;
    @Autowired private DominioRepository dominioRepository;
    @Autowired private ApplicazioneRepository applicazioneRepository;
    @Autowired private TipoVersamentoRepository tipoVersamentoRepository;
    @Autowired private TipoVersamentoDominioRepository tipoVersamentoDominioRepository;
    @Autowired private VersamentoRepository versamentoRepository;
    @Autowired private RptRepository rptRepository;
    @Autowired private PagamentoRepository pagamentoRepository;
    @Autowired private RendicontazioneRepository rendicontazioneRepository;
    @Autowired private FrRepository frRepository;
    @Autowired private IncassoRepository incassoRepository;
    @PersistenceContext private EntityManager entityManager;

    private Dominio dom;
    private Applicazione app;
    private TipoVersamento tv;
    private TipoVersamentoDominio tvd;

    @BeforeEach
    void setup() {
        dom = new Dominio();
        dom.setCodDominio(DOM);
        dom.setRagioneSociale("Comune di Test Voci");
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

    @Test
    void senzaDirittoRendicontazioniEIncassiVociEAssente() throws Exception {
        String principal = utenteSenzaDirittoServizio("op-no-rnd");
        Rpt rpt = newRpt("1000000000001", "CCP-ND");

        flushAndClear();
        mvc.perform(get("/ricevute/" + DOM + "/" + rpt.getIuv() + "/" + rpt.getCcp())
                        .with(httpBasic(principal, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voci").doesNotExist());
    }

    @Test
    void senzaPagamentiVociEUnArrayVuoto() throws Exception {
        String principal = utenteConDiritto("op-no-pag");
        Rpt rpt = newRpt("1000000000002", "CCP-EMPTY");

        flushAndClear();
        mvc.perform(get("/ricevute/" + DOM + "/" + rpt.getIuv() + "/" + rpt.getCcp())
                        .with(httpBasic(principal, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voci", hasSize(0)));
    }

    @Test
    void nessunaRendicontazioneNessunIncassoDaRendicontare() throws Exception {
        String principal = utenteConDiritto("op-da-rnd");
        Rpt rpt = newRpt("1000000000003", "CCP-DARND");
        newPagamento(rpt, 1, null, null);

        flushAndClear();
        mvc.perform(get("/ricevute/" + DOM + "/" + rpt.getIuv() + "/" + rpt.getCcp())
                        .with(httpBasic(principal, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voci", hasSize(1)))
                .andExpect(jsonPath("$.voci[0].indice", is(1)))
                .andExpect(jsonPath("$.voci[0].statoRendicontazione", is("DA_RENDICONTARE")))
                .andExpect(jsonPath("$.voci[0].anomalia", is(false)))
                .andExpect(jsonPath("$.voci[0].rendicontazioni", hasSize(0)))
                .andExpect(jsonPath("$.voci[0].riconciliazione").value((Object) null));
    }

    @Test
    void unaRigaCorrenteOkRendicontata() throws Exception {
        String principal = utenteConDiritto("op-rendicontata");
        Rpt rpt = newRpt("1000000000004", "CCP-RND");
        Pagamento p = newPagamento(rpt, 1, null, null);
        Fr fr = newFr("FLUSSO-1", 1L, false);
        newRendicontazione(fr, p, "007#", 0, "OK", null);
        String linkFlusso = "/flussi-rendicontazione/" + DOM + "/FLUSSO-1/PSP-TEST/1";

        flushAndClear();
        mvc.perform(get("/ricevute/" + DOM + "/" + rpt.getIuv() + "/" + rpt.getCcp())
                        .with(httpBasic(principal, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voci[0].statoRendicontazione", is("RENDICONTATA")))
                .andExpect(jsonPath("$.voci[0].anomalia", is(false)))
                .andExpect(jsonPath("$.voci[0].rendicontazioni", hasSize(1)))
                .andExpect(jsonPath("$.voci[0].rendicontazioni[0].stato", is("OK")))
                .andExpect(jsonPath("$.voci[0].rendicontazioni[0].esito", is("ESEGUITO")))
                .andExpect(jsonPath("$.voci[0].rendicontazioni[0].idFlusso", is("FLUSSO-1")))
                .andExpect(jsonPath("$.voci[0].rendicontazioni[0].idPsp", is("PSP-TEST")))
                .andExpect(jsonPath("$.voci[0].rendicontazioni[0].revisione", is(1)))
                .andExpect(jsonPath("$.voci[0].rendicontazioni[0]._links.flussoRendicontazione.href",
                        is(linkFlusso)));

        mvc.perform(get(linkFlusso).with(httpBasic(principal, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idFlusso", is("FLUSSO-1")))
                .andExpect(jsonPath("$.idPsp", is("PSP-TEST")))
                .andExpect(jsonPath("$.revisione", is(1)));
    }

    @Test
    void incassoCollegatoRiconciliataAncheSenzaRigheCorrenti() throws Exception {
        String principal = utenteConDiritto("op-riconciliata");
        Rpt rpt = newRpt("1000000000005", "CCP-RIC");
        Incasso incasso = newIncasso("INC-001", "TRN-001", "SCT-001");
        newPagamento(rpt, 1, null, incasso.getId());

        flushAndClear();
        mvc.perform(get("/ricevute/" + DOM + "/" + rpt.getIuv() + "/" + rpt.getCcp())
                        .with(httpBasic(principal, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voci[0].statoRendicontazione", is("RICONCILIATA")))
                .andExpect(jsonPath("$.voci[0].rendicontazioni", hasSize(0)))
                .andExpect(jsonPath("$.voci[0].riconciliazione.id", is("INC-001")))
                .andExpect(jsonPath("$.voci[0].riconciliazione.trn", is("TRN-001")))
                .andExpect(jsonPath("$.voci[0].riconciliazione.sct", is("SCT-001")));
    }

    @Test
    void rigaDiFlussoObsoletoEsclusaDalBlocco() throws Exception {
        String principal = utenteConDiritto("op-obsoleto");
        Rpt rpt = newRpt("1000000000006", "CCP-OBS");
        Pagamento p = newPagamento(rpt, 1, null, null);
        Fr frObsoleto = newFr("FLUSSO-OBS", 1L, true);
        newRendicontazione(frObsoleto, p, null, 0, "OK", null);

        flushAndClear();
        mvc.perform(get("/ricevute/" + DOM + "/" + rpt.getIuv() + "/" + rpt.getCcp())
                        .with(httpBasic(principal, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voci[0].statoRendicontazione", is("DA_RENDICONTARE")))
                .andExpect(jsonPath("$.voci[0].rendicontazioni", hasSize(0)));
    }

    @Test
    void pluriRigheCorrentiAnomaliaAggregataSuTutte() throws Exception {
        String principal = utenteConDiritto("op-anomala");
        Rpt rpt = newRpt("1000000000007", "CCP-ANM");
        Pagamento p = newPagamento(rpt, 1, null, null);
        Fr fr = newFr("FLUSSO-DUP", 1L, false);
        newRendicontazione(fr, p, null, 0, "OK", null);
        newRendicontazione(fr, p, "007115#Rendicontazione duplicata", 0, "ANOMALA", null);

        flushAndClear();
        mvc.perform(get("/ricevute/" + DOM + "/" + rpt.getIuv() + "/" + rpt.getCcp())
                        .with(httpBasic(principal, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voci[0].statoRendicontazione", is("RENDICONTATA")))
                .andExpect(jsonPath("$.voci[0].anomalia", is(true)))
                .andExpect(jsonPath("$.voci[0].anomalie", hasSize(1)))
                .andExpect(jsonPath("$.voci[0].anomalie[0].codice", is("007115")))
                .andExpect(jsonPath("$.voci[0].rendicontazioni", hasSize(2)));
    }

    /** Forza la richiesta HTTP successiva a ri-leggere le fixture da DB. */
    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    // ----- fixture helpers -----------------------------------------------------------

    private Versamento newVersamento(String codVersamentoEnte) {
        OffsetDateTime data = date(2026, 6, 20);
        Versamento v = new Versamento();
        v.setCodVersamentoEnte(codVersamentoEnte);
        v.setImportoTotale(100.0);
        v.setImportoPagato(100.0);
        v.setStatoVersamento("ESEGUITO");
        v.setDataCreazione(data);
        v.setDataOraUltimoAggiornamento(data);
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
        return versamentoRepository.save(v);
    }

    private Rpt newRpt(String iuv, String ccp) {
        Versamento v = newVersamento("PEND-" + ccp);
        Rpt r = new Rpt();
        r.setIuv(iuv);
        r.setCcp(ccp);
        r.setCodDominio(DOM);
        r.setXmlRt(XML_RT);
        r.setImportoTotalePagato(100.0);
        r.setDataMsgRichiesta(v.getDataCreazione());
        r.setDataMsgRicevuta(v.getDataCreazione());
        r.setVersione("SANP_240");
        r.setStato("RT_ACCETTATA_PA");
        r.setVersamento(v);
        return rptRepository.save(r);
    }

    private Pagamento newPagamento(Rpt rpt, int indice, Long idSingoloVersamento, Long idIncasso) {
        Pagamento p = new Pagamento();
        p.setCodDominio(DOM);
        p.setIuv(rpt.getIuv());
        p.setIur("IUR-" + rpt.getCcp());
        p.setIndiceDati(indice);
        p.setImportoPagato(100.0);
        p.setDataPagamento(rpt.getDataMsgRicevuta());
        p.setTipo("ENTRATA");
        p.setStato("PAGATO");
        p.setIdRpt(rpt.getId());
        p.setIdSingoloVersamento(idSingoloVersamento);
        p.setIdIncasso(idIncasso);
        return pagamentoRepository.save(p);
    }

    private Fr newFr(String codFlusso, Long revisione, boolean obsoleto) {
        OffsetDateTime data = date(2026, 6, 21);
        Fr fr = new Fr();
        fr.setIdDominio(dom.getId());
        fr.setCodDominio(DOM);
        fr.setCodFlusso(codFlusso);
        fr.setCodPsp("PSP-TEST");
        fr.setRevisione(revisione);
        fr.setStato("ACCETTATA");
        fr.setIur("TRN-" + codFlusso);
        fr.setDataOraFlusso(data);
        fr.setDataAcquisizione(data);
        fr.setObsoleto(obsoleto);
        return frRepository.save(fr);
    }

    private Rendicontazione newRendicontazione(Fr fr, Pagamento pagamento, String anomalie, int esito,
                                               String stato, Long idSingoloVersamento) {
        Rendicontazione r = new Rendicontazione();
        r.setIdFr(fr.getId());
        r.setIuv(pagamento.getIuv());
        r.setIur(pagamento.getIur());
        r.setIndiceDati(pagamento.getIndiceDati());
        r.setImportoPagato(pagamento.getImportoPagato());
        r.setEsito(esito);
        r.setData(pagamento.getDataPagamento());
        r.setStato(stato);
        r.setAnomalie(anomalie);
        r.setIdPagamento(pagamento.getId());
        r.setIdSingoloVersamento(idSingoloVersamento);
        return rendicontazioneRepository.save(r);
    }

    private Incasso newIncasso(String identificativo, String trn, String sct) {
        Incasso i = new Incasso();
        i.setIdentificativo(identificativo);
        i.setCodDominio(DOM);
        i.setTrn(trn);
        i.setSct(sct);
        i.setImporto(100.0);
        i.setDataOraIncasso(date(2026, 6, 22));
        i.setStato("ACQUISITO");
        return incassoRepository.save(i);
    }

    private static OffsetDateTime date(int y, int m, int d) {
        return OffsetDateTime.of(y, m, d, 12, 0, 0, 0, ZoneOffset.UTC);
    }

    private String utenteConDiritto(String principal) {
        Utenza u = baseUtenza(principal);
        utenzaRepository.save(u);
        attachOperatore(principal, u);
        Acl acl = new Acl();
        acl.setIdUtenza(u.getId());
        acl.setServizio(AclServizio.RENDICONTAZIONI_E_INCASSI.getValue());
        acl.setDiritti("R");
        aclRepository.save(acl);
        return principal;
    }

    private String utenteSenzaDirittoServizio(String principal) {
        Utenza u = baseUtenza(principal);
        utenzaRepository.save(u);
        attachOperatore(principal, u);
        return principal;
    }

    private Utenza baseUtenza(String principal) {
        Utenza u = new Utenza();
        u.setPrincipal(principal);
        u.setPrincipalOriginale(principal);
        u.setAbilitato(true);
        u.setAutorizzazioneDominiStar(true);
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
