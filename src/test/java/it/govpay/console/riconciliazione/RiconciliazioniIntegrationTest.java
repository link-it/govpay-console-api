package it.govpay.console.riconciliazione;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import it.govpay.console.entity.Incasso;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Pagamento;
import it.govpay.console.entity.Rpt;
import it.govpay.console.entity.SingoloVersamento;
import it.govpay.console.entity.TipoVersamento;
import it.govpay.console.entity.TipoVersamentoDominio;
import it.govpay.console.entity.Utenza;
import it.govpay.console.entity.UtenzaDominio;
import it.govpay.console.entity.Versamento;
import it.govpay.console.model.AclServizio;
import it.govpay.console.repository.AclRepository;
import it.govpay.console.repository.ApplicazioneRepository;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.repository.IncassoRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.PagamentoRepository;
import it.govpay.console.repository.RptRepository;
import it.govpay.console.repository.SingoloVersamentoRepository;
import it.govpay.console.repository.TipoVersamentoDominioRepository;
import it.govpay.console.repository.TipoVersamentoRepository;
import it.govpay.console.repository.UtenzaDominioRepository;
import it.govpay.console.repository.UtenzaRepository;
import it.govpay.console.repository.VersamentoRepository;

/**
 * Integration test di {@code GET /riconciliazioni} e
 * {@code GET /riconciliazioni/{idDominio}/{id}} (PR 74a, sola consultazione):
 * filtri, sort, paginazione offset/cursor, ACL, dettaglio con riscossioni.
 * Nessuna modifica allo schema: tutto letto da {@code incassi}/{@code pagamenti}
 * come gia' esistenti in V1.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RiconciliazioniIntegrationTest {

    private static final String PASSWORD = "secret";
    private static final String DOM_A = "11111111111";
    private static final String DOM_B = "22222222222";

    @Autowired private MockMvc mvc;
    @Autowired private GovpayPasswordEncoder encoder;
    @Autowired private UtenzaRepository utenzaRepository;
    @Autowired private UtenzaDominioRepository utenzaDominioRepository;
    @Autowired private OperatoreRepository operatoreRepository;
    @Autowired private DominioRepository dominioRepository;
    @Autowired private IncassoRepository incassoRepository;
    @Autowired private PagamentoRepository pagamentoRepository;
    @Autowired private ApplicazioneRepository applicazioneRepository;
    @Autowired private TipoVersamentoRepository tipoVersamentoRepository;
    @Autowired private TipoVersamentoDominioRepository tipoVersamentoDominioRepository;
    @Autowired private VersamentoRepository versamentoRepository;
    @Autowired private SingoloVersamentoRepository singoloVersamentoRepository;
    @Autowired private RptRepository rptRepository;
    @Autowired private AclRepository aclRepository;

    private Dominio domA;
    private Dominio domB;
    private Incasso a2Acquisita;
    private Applicazione app;
    private Rpt rptA21;
    private SingoloVersamento svA22;

    @BeforeEach
    void setup() {
        domA = newDominio(DOM_A, "Dominio A");
        domB = newDominio(DOM_B, "Dominio B");

        app = new Applicazione();
        app.setCodApplicazione("APP-1");
        applicazioneRepository.save(app);
        TipoVersamento tv = new TipoVersamento();
        tv.setCodTipoVersamento("TARI");
        tv.setDescrizione("TARI");
        tipoVersamentoRepository.save(tv);
        TipoVersamentoDominio tvd = new TipoVersamentoDominio();
        tvd.setDominio(domA);
        tvd.setTipoVersamento(tv);
        tipoVersamentoDominioRepository.save(tvd);

        // dominio A: 4 righe, stati/riferimenti diversi.
        newIncasso(domA, "RICA1", "NUOVO", null, "FLUSSO-A1", null, 100.0, date(2026, 6, 18), null);
        a2Acquisita = newIncasso(domA, "RICA2", "ACQUISITO", null, "FLUSSO-A2", "SCT-12345", 50.0,
                date(2026, 6, 20), null);
        newIncasso(domA, "RICA3", "ERRORE", null, "FLUSSO-A3", null, 30.0,
                date(2026, 6, 19), "Importo non corrispondente al totale pagamenti del flusso.");
        newIncasso(domA, "RICA4", "ACQUISITO", "IUV-LEGACY-1", null, null, 10.0, date(2026, 6, 21), null);

        // dominio B: 1 riga.
        newIncasso(domB, "RICB1", "ACQUISITO", null, "FLUSSO-B1", null, 40.0, date(2026, 6, 18), null);

        // riscossioni della riconciliazione A2 (dettaglio): tipi misti, una con ricevuta, una con pendenza.
        rptA21 = newRpt(domA, "IUV-A2-1", "CCP-A2-1", tv, tvd);
        svA22 = newSingoloVersamento(domA, "PEND-A2-2", tv, tvd);
        newPagamento(a2Acquisita, DOM_A, "IUV-A2-1", "IUR-1", 1, "ENTRATA", "PAGATO", 25.0,
                rptA21.getId(), null);
        newPagamento(a2Acquisita, DOM_A, "IUV-A2-2", "IUR-2", 1, "MBT", "INCASSATO", 25.0,
                null, svA22.getId());
    }

    // ----- lista: summary shape -------------------------------------------------

    @Test
    void summaryEspoineICampiAttesi() throws Exception {
        String p = utenteDominiStar("u-shape");
        mvc.perform(get("/riconciliazioni?idFlusso=FLUSSO-A2").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].id", is("RICA2")))
                .andExpect(jsonPath("$.results[0].dominio.idDominio", is(DOM_A)))
                .andExpect(jsonPath("$.results[0].dominio.ragioneSociale", is("Dominio A")))
                .andExpect(jsonPath("$.results[0].importo", is(50.0)))
                .andExpect(jsonPath("$.results[0].data").exists())
                .andExpect(jsonPath("$.results[0].sct", is("SCT-12345")))
                .andExpect(jsonPath("$.results[0].stato", is("ACQUISITA")))
                .andExpect(jsonPath("$.results[0].idFlusso", is("FLUSSO-A2")))
                .andExpect(jsonPath("$.results[0].iuv").doesNotExist());
    }

    @Test
    void recordLegacyEspoineIuvNonIdFlusso() throws Exception {
        String p = utenteDominiStar("u-legacy");
        mvc.perform(get("/riconciliazioni?iuv=IUV-LEGACY-1").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].id", is("RICA4")))
                .andExpect(jsonPath("$.results[0].iuv", is("IUV-LEGACY-1")))
                .andExpect(jsonPath("$.results[0].idFlusso").doesNotExist());
    }

    // ----- filtri ----------------------------------------------------------------

    @Test
    void filtroIdDominio() throws Exception {
        String p = utenteDominiStar("u-dom");
        mvc.perform(get("/riconciliazioni?idDominio=" + DOM_B).with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].id", is("RICB1")));
    }

    @Test
    void filtroDataRangeInclusivo() throws Exception {
        String p = utenteDominiStar("u-data");
        mvc.perform(get("/riconciliazioni?idDominio=" + DOM_A
                        + "&dataDa=2026-06-19T00:00:00Z&dataA=2026-06-20T23:59:59Z")
                        .with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(2)))
                .andExpect(jsonPath("$.results[*].id", contains("RICA2", "RICA3")));
    }

    @Test
    void filtroStato() throws Exception {
        String p = utenteDominiStar("u-stato");
        mvc.perform(get("/riconciliazioni?idDominio=" + DOM_A + "&stato=ERRORE").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].id", is("RICA3")))
                .andExpect(jsonPath("$.results[0].descrizioneStato").exists());
    }

    @Test
    void filtroSctParziale() throws Exception {
        String p = utenteDominiStar("u-sct");
        mvc.perform(get("/riconciliazioni?sct=2345").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].id", is("RICA2")));
    }

    @Test
    void filtroIdFlusso() throws Exception {
        String p = utenteDominiStar("u-flusso");
        mvc.perform(get("/riconciliazioni?idFlusso=FLUSSO-A1").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].id", is("RICA1")));
    }

    @Test
    void filtroNonSupportatoRitorna400() throws Exception {
        String p = utenteDominiStar("u-400f");
        mvc.perform(get("/riconciliazioni?ordinamento=%2Bdata").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    // ----- sort --------------------------------------------------------------------

    @Test
    void sortDefaultDataDesc() throws Exception {
        String p = utenteDominiStar("u-sortd");
        mvc.perform(get("/riconciliazioni?idDominio=" + DOM_A).with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].id",
                        contains("RICA4", "RICA2", "RICA3", "RICA1")));
    }

    @Test
    void sortEsplicitoImportoAscendente() throws Exception {
        String p = utenteDominiStar("u-sorta");
        mvc.perform(get("/riconciliazioni?idDominio=" + DOM_A + "&sort=importo").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].id",
                        contains("RICA4", "RICA3", "RICA2", "RICA1")));
    }

    @Test
    void sortCampoSconosciutoRitorna400() throws Exception {
        String p = utenteDominiStar("u-sortx");
        mvc.perform(get("/riconciliazioni?sort=-causale").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    // ----- paginazione ---------------------------------------------------------------

    @Test
    void offsetConTotale() throws Exception {
        String p = utenteDominiStar("u-off");
        mvc.perform(get("/riconciliazioni?limit=2&total=true").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(2)))
                .andExpect(jsonPath("$.pagination.hasNextPage", is(true)))
                .andExpect(jsonPath("$.pagination.totalResults", is(5)))
                .andExpect(jsonPath("$.pagination.totalPages", is(3)));
    }

    @Test
    void cursorPrimaPaginaEPaginaSuccessiva() throws Exception {
        String p = utenteDominiStar("u-cur");
        String body = mvc.perform(get("/riconciliazioni?cursor=&limit=2").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(2)))
                .andExpect(jsonPath("$.pagination").doesNotExist())
                .andExpect(jsonPath("$.nextCursor").exists())
                .andReturn().getResponse().getContentAsString();

        String next = com.jayway.jsonpath.JsonPath.read(body, "$.nextCursor");
        mvc.perform(get("/riconciliazioni?cursor=" + next + "&limit=2").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(2)))
                .andExpect(jsonPath("$.nextCursor").exists());
    }

    @Test
    void pageECursorMutuamenteEsclusiviRitorna400() throws Exception {
        String p = utenteDominiStar("u-mutex");
        mvc.perform(get("/riconciliazioni?cursor=&page=2").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void limitOltre200Ritorna400() throws Exception {
        String p = utenteDominiStar("u-lim");
        mvc.perform(get("/riconciliazioni?limit=5000").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    // ----- ACL -----------------------------------------------------------------------

    @Test
    void aclLimitaAiDominiVisibili() throws Exception {
        String p = utenteSoloDominio("u-acl", domB);
        mvc.perform(get("/riconciliazioni").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].id", is("RICB1")));
    }

    @Test
    void listaSenzaDirittoServizioRitorna403() throws Exception {
        String p = utenteSenzaDirittoServizio("u-noacl-list");
        mvc.perform(get("/riconciliazioni").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isForbidden());
    }

    // ----- dettaglio -------------------------------------------------------------------

    @Test
    void dettaglioConRiscossioni() throws Exception {
        String p = utenteDominiStar("u-det");
        mvc.perform(get("/riconciliazioni/" + DOM_A + "/RICA2").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.id", is("RICA2")))
                .andExpect(jsonPath("$.riscossioni", hasSize(2)))
                .andExpect(jsonPath("$._links.self.href", is("/riconciliazioni/" + DOM_A + "/RICA2")))
                .andExpect(jsonPath("$._links.dominio").exists())
                .andExpect(jsonPath("$._links.flussoRendicontazione").exists())
                // riscossione ENTRATA: link a ricevuta risolto via Rpt.ccp, niente pendenza
                .andExpect(jsonPath("$.riscossioni[0]._links.ricevuta.href",
                        is("/ricevute/" + DOM_A + "/IUV-A2-1/CCP-A2-1")))
                .andExpect(jsonPath("$.riscossioni[0]._links.pendenza").doesNotExist())
                // riscossione MBT: link a pendenza risolto via SingoloVersamento->Versamento->Applicazione
                .andExpect(jsonPath("$.riscossioni[1]._links.pendenza.href",
                        is("/pendenze/APP-1/PEND-A2-2")))
                .andExpect(jsonPath("$.riscossioni[1]._links.ricevuta").doesNotExist());
    }

    @Test
    void dettaglioFiltraPerTipoRiscossione() throws Exception {
        String p = utenteDominiStar("u-tipo");
        mvc.perform(get("/riconciliazioni/" + DOM_A + "/RICA2?tipoRiscossione=MBT").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riscossioni", hasSize(1)))
                .andExpect(jsonPath("$.riscossioni[0].tipo", is("MBT")))
                .andExpect(jsonPath("$.riscossioni[0]._links.pendenza.href", is("/pendenze/APP-1/PEND-A2-2")))
                .andExpect(jsonPath("$.riscossioni[0]._links.ricevuta").doesNotExist());
    }

    @Test
    void dettaglioNonTrovataRitorna404() throws Exception {
        String p = utenteDominiStar("u-404");
        mvc.perform(get("/riconciliazioni/" + DOM_A + "/RICINESISTENTE").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void dettaglioSenzaDirittoServizioRitorna403() throws Exception {
        String p = utenteSenzaDirittoServizio("u-noacl-det");
        mvc.perform(get("/riconciliazioni/" + DOM_A + "/RICA2").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void dettaglioAclNegataRitorna404() throws Exception {
        String p = utenteSoloDominio("u-detacl", domB);
        mvc.perform(get("/riconciliazioni/" + DOM_A + "/RICA2").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    // ----- fixture helpers -----------------------------------------------------------

    private Dominio newDominio(String cod, String ragione) {
        Dominio d = new Dominio();
        d.setCodDominio(cod);
        d.setRagioneSociale(ragione);
        d.setAuxDigit(0);
        return dominioRepository.save(d);
    }

    private Incasso newIncasso(Dominio dominio, String identificativo, String stato, String iuv,
                               String idFlusso, String sct, Double importo,
                               OffsetDateTime data, String descrizioneStato) {
        Incasso i = new Incasso();
        i.setIdentificativo(identificativo);
        i.setCodDominio(dominio.getCodDominio());
        i.setTrn(idFlusso != null ? idFlusso : iuv);
        i.setImporto(importo);
        i.setDataOraIncasso(data);
        i.setSct(sct);
        i.setIuv(iuv);
        i.setCodFlussoRendicontazione(idFlusso);
        i.setStato(stato);
        i.setDescrizioneStato(descrizioneStato);
        return incassoRepository.save(i);
    }

    private void newPagamento(Incasso incasso, String codDominio, String iuv, String iur, int indice,
                              String tipo, String stato, Double importo, Long idRpt, Long idSingoloVersamento) {
        Pagamento p = new Pagamento();
        p.setCodDominio(codDominio);
        p.setIuv(iuv);
        p.setIur(iur);
        p.setIndiceDati(indice);
        p.setImportoPagato(importo);
        p.setDataPagamento(incasso.getDataOraIncasso());
        p.setTipo(tipo);
        p.setStato(stato);
        p.setIdRpt(idRpt);
        p.setIdSingoloVersamento(idSingoloVersamento);
        p.setIdIncasso(incasso.getId());
        pagamentoRepository.save(p);
    }

    private Versamento newVersamento(Dominio dominio, String codVersamentoEnte,
                                     TipoVersamento tv, TipoVersamentoDominio tvd) {
        OffsetDateTime data = date(2026, 6, 20);
        Versamento v = new Versamento();
        v.setCodVersamentoEnte(codVersamentoEnte);
        v.setImportoTotale(25.0);
        v.setImportoPagato(25.0);
        v.setStatoVersamento("ESEGUITO");
        v.setDataCreazione(data);
        v.setDataOraUltimoAggiornamento(data);
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
        return versamentoRepository.save(v);
    }

    private Rpt newRpt(Dominio dominio, String iuv, String ccp, TipoVersamento tv, TipoVersamentoDominio tvd) {
        Versamento v = newVersamento(dominio, "PEND-" + ccp, tv, tvd);
        Rpt r = new Rpt();
        r.setIuv(iuv);
        r.setCcp(ccp);
        r.setCodDominio(dominio.getCodDominio());
        r.setDataMsgRichiesta(v.getDataCreazione());
        r.setVersione("SANP_240");
        r.setStato("RT_ACCETTATA_PA");
        r.setVersamento(v);
        return rptRepository.save(r);
    }

    private SingoloVersamento newSingoloVersamento(Dominio dominio, String codVersamentoEnte,
                                                    TipoVersamento tv, TipoVersamentoDominio tvd) {
        Versamento v = newVersamento(dominio, codVersamentoEnte, tv, tvd);
        SingoloVersamento sv = new SingoloVersamento();
        sv.setCodSingoloVersamentoEnte("SING-" + codVersamentoEnte);
        sv.setStatoSingoloVersamento("ESEGUITO");
        sv.setImportoSingoloVersamento(25.0);
        sv.setIndiceDati(1);
        sv.setVersamento(v);
        return singoloVersamentoRepository.save(sv);
    }

    private static OffsetDateTime date(int y, int m, int d) {
        return OffsetDateTime.of(y, m, d, 12, 0, 0, 0, ZoneOffset.UTC);
    }

    private String utenteDominiStar(String principal) {
        Utenza u = baseUtenza(principal, true, true);
        utenzaRepository.save(u);
        attachOperatore(principal, u);
        grantLettura(u);
        return principal;
    }

    private String utenteSoloDominio(String principal, Dominio dominio) {
        Utenza u = baseUtenza(principal, false, true);
        utenzaRepository.save(u);
        attachOperatore(principal, u);
        grantLettura(u);
        UtenzaDominio link = new UtenzaDominio();
        link.setIdUtenza(u.getId());
        link.setIdDominio(dominio.getId());
        utenzaDominioRepository.save(link);
        return principal;
    }

    /** Come {@link #utenteDominiStar} ma senza il diritto di lettura sul servizio: per i test 403. */
    private String utenteSenzaDirittoServizio(String principal) {
        Utenza u = baseUtenza(principal, true, true);
        utenzaRepository.save(u);
        attachOperatore(principal, u);
        return principal;
    }

    private void grantLettura(Utenza utenza) {
        Acl acl = new Acl();
        acl.setIdUtenza(utenza.getId());
        acl.setServizio(AclServizio.RENDICONTAZIONI_E_INCASSI.getValue());
        acl.setDiritti("R");
        aclRepository.save(acl);
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
