package it.govpay.console.ricevuta;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.common.auth.GovpayPasswordEncoder;
import it.govpay.console.entity.Applicazione;
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Rpt;
import it.govpay.console.entity.TipoVersamento;
import it.govpay.console.entity.TipoVersamentoDominio;
import it.govpay.console.entity.Utenza;
import it.govpay.console.entity.UtenzaDominio;
import it.govpay.console.entity.Versamento;
import it.govpay.console.repository.ApplicazioneRepository;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.RptRepository;
import it.govpay.console.repository.TipoVersamentoDominioRepository;
import it.govpay.console.repository.TipoVersamentoRepository;
import it.govpay.console.repository.UtenzaDominioRepository;
import it.govpay.console.repository.UtenzaRepository;
import it.govpay.console.repository.VersamentoRepository;

/**
 * Integration test della collection top-level {@code GET /ricevute} (scope B/F
 * issue #12): filtri, sort, paginazione offset/cursor e ACL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RicevuteSearchIntegrationTest {

    private static final String PASSWORD = "secret";
    private static final String DOM_A = "11111111111";
    private static final String DOM_B = "22222222222";

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

    private Dominio domA;
    private Dominio domB;
    private Applicazione app;
    private TipoVersamento tvA;
    private TipoVersamentoDominio tvdA;
    private TipoVersamento tvB;
    private TipoVersamentoDominio tvdB;

    @BeforeEach
    void setup() {
        domA = newDominio(DOM_A, "Dominio A");
        domB = newDominio(DOM_B, "Dominio B");
        app = new Applicazione();
        app.setCodApplicazione("APP-1");
        applicazioneRepository.save(app);
        tvA = newTipoVersamento("TARI");
        tvdA = newTvd(domA, tvA);
        tvB = newTipoVersamento("IMU");
        tvdB = newTvd(domB, tvB);

        // 3 RT su dominio A, date crescenti; 2 RT su dominio B.
        newRpt(domA, tvA, tvdA, "AAAAAAAAAA1", "CCP-A1", date(2026, 6, 18), 10.0, "SANP_321_V2");
        newRpt(domA, tvA, tvdA, "AAAAAAAAAA2", "CCP-A2", date(2026, 6, 19), 20.0, "SANP_240");
        newRpt(domA, tvA, tvdA, "AAAAAAAAAA3", "CCP-A3", date(2026, 6, 20), 30.0, "SANP_230");
        newRpt(domB, tvB, tvdB, "BBBBBBBBBB1", "CCP-B1", date(2026, 6, 18), 40.0, "SANP_240");
        newRpt(domB, tvB, tvdB, "BBBBBBBBBB2", "CCP-B2", date(2026, 6, 21), 50.0, "SANP_321_V2");
    }

    // ----- summary shape -----------------------------------------------------

    @Test
    void summaryEspoineSoloI9CampiTecnici() throws Exception {
        String p = utenteDominiStar("u-shape");
        mvc.perform(get("/ricevute?iuv=AAAAAAAAAA1").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].idDominio", is(DOM_A)))
                .andExpect(jsonPath("$.results[0].iuv", is("AAAAAAAAAA1")))
                .andExpect(jsonPath("$.results[0].idRicevuta", is("CCP-A1")))
                .andExpect(jsonPath("$.results[0].importo", is(10.0)))
                .andExpect(jsonPath("$.results[0].codPsp", is("PSP-X")))
                .andExpect(jsonPath("$.results[0].versione", is("2.0")))
                .andExpect(jsonPath("$.results[0].stato", is("RT_ACCETTATA_PA")))
                .andExpect(jsonPath("$.results[0].descrizioneStato", is("ok")))
                // nessun dato personale ne' campi V1 rinominati
                .andExpect(jsonPath("$.results[0].ccp").doesNotExist())
                .andExpect(jsonPath("$.results[0].esito").doesNotExist())
                .andExpect(jsonPath("$.results[0].idDebitore").doesNotExist())
                .andExpect(jsonPath("$.results[0].rpt").doesNotExist())
                .andExpect(jsonPath("$.results[0].rt").doesNotExist());
    }

    // ----- filtri ------------------------------------------------------------

    @Test
    void filtroIdDominio() throws Exception {
        String p = utenteDominiStar("u-dom");
        mvc.perform(get("/ricevute?idDominio=" + DOM_B).with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(2)));
    }

    @Test
    void filtroIdRicevuta() throws Exception {
        String p = utenteDominiStar("u-ric");
        mvc.perform(get("/ricevute?idRicevuta=CCP-A2").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].iuv", is("AAAAAAAAAA2")));
    }

    @Test
    void filtroDataRangeInclusivo() throws Exception {
        String p = utenteDominiStar("u-data");
        // 19→20 incluso: A2 (19), A3 (20). Esclude A1/B1 (18) e B2 (21).
        mvc.perform(get("/ricevute?dataDa=2026-06-19&dataA=2026-06-20").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(2)))
                .andExpect(jsonPath("$.results[*].iuv", contains("AAAAAAAAAA3", "AAAAAAAAAA2")));
    }

    @Test
    void filtriCombinatiAnd() throws Exception {
        String p = utenteDominiStar("u-and");
        mvc.perform(get("/ricevute?idDominio=" + DOM_A + "&dataDa=2026-06-20")
                        .with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].iuv", is("AAAAAAAAAA3")));
    }

    @Test
    void filtroNonSupportatoRitorna400() throws Exception {
        String p = utenteDominiStar("u-400f");
        mvc.perform(get("/ricevute?esito=0").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    // ----- sort --------------------------------------------------------------

    @Test
    void sortDefaultDataPagamentoDesc() throws Exception {
        String p = utenteDominiStar("u-sortd");
        mvc.perform(get("/ricevute?idDominio=" + DOM_A).with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].iuv",
                        contains("AAAAAAAAAA3", "AAAAAAAAAA2", "AAAAAAAAAA1")));
    }

    @Test
    void sortAscendente() throws Exception {
        String p = utenteDominiStar("u-sorta");
        // direzione di default = ASC (nessun prefisso)
        mvc.perform(get("/ricevute?idDominio=" + DOM_A + "&sort=dataPagamento")
                        .with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].iuv",
                        contains("AAAAAAAAAA1", "AAAAAAAAAA2", "AAAAAAAAAA3")));
    }

    @Test
    void sortCampoSconosciutoRitorna400() throws Exception {
        String p = utenteDominiStar("u-sortx");
        mvc.perform(get("/ricevute?sort=-importo").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    // ----- paginazione -------------------------------------------------------

    @Test
    void offsetConTotale() throws Exception {
        String p = utenteDominiStar("u-off");
        mvc.perform(get("/ricevute?limit=2&total=true").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(2)))
                .andExpect(jsonPath("$.pagination.hasNextPage", is(true)))
                .andExpect(jsonPath("$.pagination.totalResults", is(5)))
                .andExpect(jsonPath("$.pagination.totalPages", is(3)));
    }

    @Test
    void cursorPrimaPaginaEPaginaSuccessiva() throws Exception {
        String p = utenteDominiStar("u-cur");
        String body = mvc.perform(get("/ricevute?cursor=&limit=2").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(2)))
                .andExpect(jsonPath("$.pagination").doesNotExist())
                .andExpect(jsonPath("$.nextCursor").exists())
                .andReturn().getResponse().getContentAsString();

        String next = com.jayway.jsonpath.JsonPath.read(body, "$.nextCursor");
        mvc.perform(get("/ricevute?cursor=" + next + "&limit=2").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(2)))
                .andExpect(jsonPath("$.nextCursor").exists());
    }

    @Test
    void pageECursorMutuamenteEsclusiviRitorna400() throws Exception {
        String p = utenteDominiStar("u-mutex");
        mvc.perform(get("/ricevute?cursor=&page=2").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void limitOltre200Ritorna400() throws Exception {
        String p = utenteDominiStar("u-lim");
        mvc.perform(get("/ricevute?limit=5000").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    // ----- ACL ---------------------------------------------------------------

    @Test
    void aclLimitaAiDominiVisibili() throws Exception {
        String p = utenteSoloDominio("u-acl", domB);
        mvc.perform(get("/ricevute").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(2)))
                .andExpect(jsonPath("$.results[*].idDominio", contains(DOM_B, DOM_B)));
    }

    // ----- fixture helpers ---------------------------------------------------

    private Dominio newDominio(String cod, String ragione) {
        Dominio d = new Dominio();
        d.setCodDominio(cod);
        d.setRagioneSociale(ragione);
        d.setAuxDigit(0);
        return dominioRepository.save(d);
    }

    private TipoVersamento newTipoVersamento(String cod) {
        TipoVersamento tv = new TipoVersamento();
        tv.setCodTipoVersamento(cod);
        tv.setDescrizione(cod);
        return tipoVersamentoRepository.save(tv);
    }

    private TipoVersamentoDominio newTvd(Dominio d, TipoVersamento tv) {
        TipoVersamentoDominio tvd = new TipoVersamentoDominio();
        tvd.setDominio(d);
        tvd.setTipoVersamento(tv);
        return tipoVersamentoDominioRepository.save(tvd);
    }

    private static OffsetDateTime date(int y, int m, int d) {
        return OffsetDateTime.of(y, m, d, 12, 0, 0, 0, ZoneOffset.UTC);
    }

    private void newRpt(Dominio dominio, TipoVersamento tv, TipoVersamentoDominio tvd,
                        String iuv, String ccp, OffsetDateTime dataPagamento,
                        double importo, String versione) {
        Versamento v = new Versamento();
        v.setCodVersamentoEnte("PEND-" + iuv);
        v.setImportoTotale(importo);
        v.setImportoPagato(importo);
        v.setStatoVersamento("ESEGUITO");
        v.setDataCreazione(dataPagamento);
        v.setDataOraUltimoAggiornamento(dataPagamento);
        v.setDebitoreIdentificativo("RSSMRA80A01H501U");
        v.setDebitoreAnagrafica("Mario Rossi");
        v.setSrcDebitoreIdentificativo("RSSMRA80A01H501U");
        v.setAnomalo(false);
        v.setAck(true);
        v.setTipo("DOVUTO");
        v.setDominio(dominio);
        v.setApplicazione(app);
        v.setTipoVersamento(tv);
        v.setTipoVersamentoDominio(tvd);
        versamentoRepository.save(v);

        Rpt r = new Rpt();
        r.setIuv(iuv);
        r.setCcp(ccp);
        r.setCodDominio(dominio.getCodDominio());
        r.setXmlRt("<RT/>".getBytes());
        r.setCodEsitoPagamento(0);
        r.setImportoTotalePagato(importo);
        r.setDataMsgRichiesta(dataPagamento.minusHours(1));
        r.setDataMsgRicevuta(dataPagamento);
        r.setVersione(versione);
        r.setStato("RT_ACCETTATA_PA");
        r.setDescrizioneStato("ok");
        r.setCodPsp("PSP-X");
        r.setVersamento(v);
        rptRepository.save(r);
    }

    private String utenteDominiStar(String principal) {
        Utenza u = baseUtenza(principal, true, true);
        utenzaRepository.save(u);
        attachOperatore(principal, u);
        return principal;
    }

    private String utenteSoloDominio(String principal, Dominio dominio) {
        Utenza u = baseUtenza(principal, false, true);
        utenzaRepository.save(u);
        attachOperatore(principal, u);
        UtenzaDominio link = new UtenzaDominio();
        link.setIdUtenza(u.getId());
        link.setIdDominio(dominio.getId());
        utenzaDominioRepository.save(link);
        return principal;
    }

    private Utenza baseUtenza(String principal, boolean tuttiDomini, boolean tuttiTipi) {
        Utenza u = new Utenza();
        u.setPrincipal(principal);
        u.setPrincipalOriginale(principal);
        u.setAbilitato(true);
        u.setAutorizzazioneDominiStar(tuttiDomini);
        u.setAutorizzazioneTipiVersStar(tuttiTipi);
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
