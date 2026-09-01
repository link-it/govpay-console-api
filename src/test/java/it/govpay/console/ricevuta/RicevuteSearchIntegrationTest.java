package it.govpay.console.ricevuta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import it.govpay.console.entity.UnitaOperativa;
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
import it.govpay.console.repository.UnitaOperativaRepository;
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
    @Autowired private UnitaOperativaRepository unitaOperativaRepository;
    @Autowired private GpAuditRepository gpAuditRepository;

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
                .andExpect(jsonPath("$.results[0].dataRicevuta").exists())
                .andExpect(jsonPath("$.results[0].importo", is(10.0)))
                .andExpect(jsonPath("$.results[0].codPsp", is("PSP-X")))
                .andExpect(jsonPath("$.results[0].versione", is("2.0")))
                .andExpect(jsonPath("$.results[0].stato", is("RT_ACCETTATA_PA")))
                .andExpect(jsonPath("$.results[0].descrizioneStato", is("ok")))
                // esattamente i 9 campi previsti, nessuno in piu'
                .andExpect(jsonPath("$.results[0].*", hasSize(9)))
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
        mvc.perform(get("/ricevute?dataRicevutaDa=2026-06-19&dataRicevutaA=2026-06-20").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(2)))
                .andExpect(jsonPath("$.results[*].iuv", contains("AAAAAAAAAA3", "AAAAAAAAAA2")));
    }

    /**
     * Issue #68 §B: {@code dataRichiestaDa/A} e {@code dataRicevutaDa/A} sono
     * intervalli indipendenti su colonne diverse ({@code data_msg_richiesta} vs
     * {@code data_msg_ricevuta}), combinabili in AND. Fixture con richiesta a
     * gennaio e ricevuta a marzo: deve comparire filtrando su gennaio per la
     * richiesta *e* su marzo per la ricevuta, e non deve comparire invertendo i
     * due intervalli.
     */
    @Test
    void dataRichiestaEDataRicevutaSonoIndipendenti() throws Exception {
        String p = utenteDominiStar("u-datesplit");
        newRpt(domA, tvA, tvdA, "CCCCCCCCCC1", "CCP-SPLIT",
                date(2026, 1, 10), date(2026, 3, 10), 99.0, "SANP_240");

        mvc.perform(get("/ricevute?idDominio=" + DOM_A
                        + "&dataRichiestaDa=2026-01-01&dataRichiestaA=2026-01-31"
                        + "&dataRicevutaDa=2026-03-01&dataRicevutaA=2026-03-31")
                        .with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].iuv", hasSize(1)))
                .andExpect(jsonPath("$.results[0].iuv", is("CCCCCCCCCC1")));

        // Invertiti: la richiesta e' a gennaio, non a marzo; la ricevuta e' a
        // marzo, non a gennaio. Nessuna riga puo' soddisfare entrambi.
        mvc.perform(get("/ricevute?idDominio=" + DOM_A
                        + "&dataRichiestaDa=2026-03-01&dataRichiestaA=2026-03-31"
                        + "&dataRicevutaDa=2026-01-01&dataRicevutaA=2026-01-31")
                        .with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(0)));
    }

    @Test
    void filtriCombinatiAnd() throws Exception {
        String p = utenteDominiStar("u-and");
        mvc.perform(get("/ricevute?idDominio=" + DOM_A + "&dataRicevutaDa=2026-06-20")
                        .with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].iuv", is("AAAAAAAAAA3")));
    }

    // ----- filtri via versamento (issue #68 §A) -------------------------------

    @Test
    void filterByIdA2A() throws Exception {
        String p = utenteDominiStar("u-a2a");
        mvc.perform(get("/ricevute?idDominio=" + DOM_A + "&idA2A=" + app.getCodApplicazione())
                        .with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(3)));

        mvc.perform(get("/ricevute?idA2A=APP-BOGUS").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(0)));
    }

    /** {@code idPendenza} e' a match esatto (issue #68), non parziale come su {@code /pendenze}. */
    @Test
    void filterByIdPendenzaEsatto() throws Exception {
        String p = utenteDominiStar("u-idpend");
        mvc.perform(get("/ricevute?idPendenza=PEND-AAAAAAAAAA1").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].iuv", is("AAAAAAAAAA1")));

        // prefisso, non l'identificativo esatto: nessun match (a differenza di /pendenze, partial)
        mvc.perform(get("/ricevute?idPendenza=PEND-AAAAAAAAAA").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(0)));
    }

    /** Riusa il predicato e l'azione di audit di {@code /pendenze} (issue #68 §A): non un audit distinto. */
    @Test
    void filterByIdentificativoDebitoreWritesAudit() throws Exception {
        String p = utenteDominiStar("u-debaudit");
        long auditBefore = gpAuditRepository.count();

        mvc.perform(get("/ricevute?identificativoDebitore=RSSMRA80A01H501U")
                        .with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(5)));

        assertThat(gpAuditRepository.count()).isEqualTo(auditBefore + 1);
    }

    @Test
    void filtersWithoutDebitoreDoNotWriteAudit() throws Exception {
        String p = utenteDominiStar("u-nodebaudit");
        long auditBefore = gpAuditRepository.count();

        mvc.perform(get("/ricevute?idDominio=" + DOM_A).with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk());

        assertThat(gpAuditRepository.count()).isEqualTo(auditBefore);
    }

    @Test
    void filterByIdUnitaOperativa() throws Exception {
        String p = utenteDominiStar("u-uo");
        UnitaOperativa uo = new UnitaOperativa();
        uo.setCodUo("UO-RIC-1");
        uo.setUoDenominazione("Ufficio Ricevute");
        uo.setDominio(domA);
        unitaOperativaRepository.save(uo);

        Versamento v = versamentoRepository.findDetail(app.getCodApplicazione(), "PEND-AAAAAAAAAA1").orElseThrow();
        v.setUnitaOperativa(uo);
        versamentoRepository.save(v);

        mvc.perform(get("/ricevute?idUnitaOperativa=UO-RIC-1").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].iuv", is("AAAAAAAAAA1")));
    }

    @Test
    void filterByIdTipoPendenzaMultiploUnisceIRisultati() throws Exception {
        String p = utenteDominiStar("u-tipopend");
        // dominio A: tvA "TARI" (3 RT), dominio B: tvB "IMU" (2 RT).
        mvc.perform(get("/ricevute?idTipoPendenza=TARI").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(3)));

        mvc.perform(get("/ricevute?idTipoPendenza=TARI,IMU").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(5)));
    }

    @Test
    void idTipoPendenzaVuotoOSoloSeparatoriReturns400() throws Exception {
        String p = utenteDominiStar("u-tipopend400");
        mvc.perform(get("/ricevute?idTipoPendenza=").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("idTipoPendenza")));

        mvc.perform(get("/ricevute?idTipoPendenza=,,").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("idTipoPendenza")));
    }

    /** {@code direzione}/{@code divisione}: stessa cardinalita' lista/OR di {@code /pendenze} dopo il retrofit. */
    @Test
    void filterByDirezioneEDivisioneMultiplo() throws Exception {
        String p = utenteDominiStar("u-dirdiv");
        Versamento v1 = versamentoRepository.findDetail(app.getCodApplicazione(), "PEND-AAAAAAAAAA1").orElseThrow();
        v1.setDirezione("DIR-1");
        v1.setDivisione("DIV-1");
        versamentoRepository.save(v1);
        Versamento v2 = versamentoRepository.findDetail(app.getCodApplicazione(), "PEND-AAAAAAAAAA2").orElseThrow();
        v2.setDirezione("DIR-2");
        v2.setDivisione("DIV-2");
        versamentoRepository.save(v2);

        mvc.perform(get("/ricevute?direzione=DIR-1,DIR-2").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].iuv", containsInAnyOrder("AAAAAAAAAA1", "AAAAAAAAAA2")));

        mvc.perform(get("/ricevute?divisione=DIV-1,DIV-2").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].iuv", containsInAnyOrder("AAAAAAAAAA1", "AAAAAAAAAA2")));
    }

    @Test
    void filterByTassonomia() throws Exception {
        String p = utenteDominiStar("u-tassonomia");
        Versamento v = versamentoRepository.findDetail(app.getCodApplicazione(), "PEND-AAAAAAAAAA1").orElseThrow();
        v.setTassonomia("TAX-001");
        versamentoRepository.save(v);

        mvc.perform(get("/ricevute?tassonomia=TAX-001").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].iuv", is("AAAAAAAAAA1")));

        mvc.perform(get("/ricevute?tassonomia=TAX-BOGUS").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(0)));
    }

    /** Combinazione realistica: piu' filtri via {@code versamento} insieme, tutti in AND. */
    @Test
    void combinazioneRealisticaFiltriViaVersamento() throws Exception {
        String p = utenteDominiStar("u-combo");
        mvc.perform(get("/ricevute?idDominio=" + DOM_A
                        + "&idA2A=" + app.getCodApplicazione()
                        + "&idTipoPendenza=TARI"
                        + "&dataRicevutaDa=2026-06-19")
                        .with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].iuv",
                        containsInAnyOrder("AAAAAAAAAA2", "AAAAAAAAAA3")));
    }

    /** ACL sempre in AND (issue #68): un match esplicito su un'altra pendenza non amplia la visibilita'. */
    @Test
    void filtroIdPendenzaNonAmpliaVisibilitaAcl() throws Exception {
        String p = utenteSoloDominio("u-aclpend", domA);
        // BBBBBBBBBB1 e' sul dominio B, non visibile a questo operatore.
        mvc.perform(get("/ricevute?idPendenza=PEND-BBBBBBBBBB1").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(0)));
    }

    @Test
    void escludeRptConSolaRichiestaSenzaRt() throws Exception {
        String p = utenteDominiStar("u-nort");
        // riga rpt con sola richiesta (xml_rt null, pagamento non concluso) sul dominio A
        newRptSenzaRt(domA, tvA, tvdA, "NORTIUV0001", "CCP-NORT");
        // dominio A ha 3 RT reali dal setup: la riga senza RT non deve comparire.
        mvc.perform(get("/ricevute?idDominio=" + DOM_A).with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(3)))
                .andExpect(jsonPath("$.results[*].iuv",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("NORTIUV0001"))));
    }

    @Test
    void escludeRtSenzaDataPagamento() throws Exception {
        String p = utenteDominiStar("u-nodata");
        // RT presente (xml_rt) ma senza data pagamento: non ordinabile/cursorabile → esclusa.
        Versamento v = new Versamento();
        v.setCodVersamentoEnte("PEND-NODATA");
        v.setImportoTotale(10.0);
        v.setImportoPagato(10.0);
        v.setStatoVersamento("ESEGUITO");
        v.setDataCreazione(date(2026, 6, 19));
        v.setDataOraUltimoAggiornamento(date(2026, 6, 19));
        v.setDebitoreIdentificativo("RSSMRA80A01H501U");
        v.setDebitoreAnagrafica("Mario Rossi");
        v.setSrcDebitoreIdentificativo("RSSMRA80A01H501U");
        v.setAnomalo(false);
        v.setAck(true);
        v.setTipo("DOVUTO");
        v.setDominio(domA);
        v.setApplicazione(app);
        v.setTipoVersamento(tvA);
        v.setTipoVersamentoDominio(tvdA);
        versamentoRepository.save(v);
        Rpt r = new Rpt();
        r.setIuv("NODATAIUV01");
        r.setCcp("CCP-ND");
        r.setCodDominio(DOM_A);
        r.setXmlRt("<RT/>".getBytes());
        r.setImportoTotalePagato(10.0);
        r.setDataMsgRichiesta(date(2026, 6, 19));
        r.setDataMsgRicevuta(null);
        r.setVersione("SANP_240");
        r.setStato("RT_ACCETTATA_PA");
        r.setVersamento(v);
        rptRepository.save(r);

        // cursor mode: deve restare stabile (no NPE su encode) ed escludere la riga senza data
        mvc.perform(get("/ricevute?cursor=&idDominio=" + DOM_A).with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(3)))
                .andExpect(jsonPath("$.results[*].iuv",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("NODATAIUV01"))));
    }

    @Test
    void filtroNonSupportatoRitorna400() throws Exception {
        String p = utenteDominiStar("u-400f");
        mvc.perform(get("/ricevute?esito=0").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    // ----- sort --------------------------------------------------------------

    @Test
    void sortDefaultDataRicevutaDesc() throws Exception {
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
        mvc.perform(get("/ricevute?idDominio=" + DOM_A + "&sort=dataRicevuta")
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
    void cursorConTotalFalseEAmmesso() throws Exception {
        String p = utenteDominiStar("u-totfalse");
        // total=false è il default: non confligge col cursor (un client generato puo' inviarlo)
        mvc.perform(get("/ricevute?cursor=&total=false&limit=2").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(2)))
                .andExpect(jsonPath("$.pagination").doesNotExist())
                .andExpect(jsonPath("$.nextCursor").exists());
    }

    @Test
    void cursorConTotalTrueRitorna400() throws Exception {
        String p = utenteDominiStar("u-tottrue");
        mvc.perform(get("/ricevute?cursor=&total=true").with(httpBasic(p, PASSWORD)))
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
        newRpt(dominio, tv, tvd, iuv, ccp, dataPagamento.minusHours(1), dataPagamento, importo, versione);
    }

    /** Variante con richiesta e ricevuta su date indipendenti (issue #68 §B). */
    private void newRpt(Dominio dominio, TipoVersamento tv, TipoVersamentoDominio tvd,
                        String iuv, String ccp, OffsetDateTime dataRichiesta, OffsetDateTime dataRicevuta,
                        double importo, String versione) {
        Versamento v = new Versamento();
        v.setCodVersamentoEnte("PEND-" + iuv);
        v.setImportoTotale(importo);
        v.setImportoPagato(importo);
        v.setStatoVersamento("ESEGUITO");
        v.setDataCreazione(dataRicevuta);
        v.setDataOraUltimoAggiornamento(dataRicevuta);
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
        r.setDataMsgRichiesta(dataRichiesta);
        r.setDataMsgRicevuta(dataRicevuta);
        r.setVersione(versione);
        r.setStato("RT_ACCETTATA_PA");
        r.setDescrizioneStato("ok");
        r.setCodPsp("PSP-X");
        r.setVersamento(v);
        rptRepository.save(r);
    }

    /** Riga {@code rpt} con sola richiesta: RT non ancora presente (xml_rt e data RT null). */
    private void newRptSenzaRt(Dominio dominio, TipoVersamento tipo, TipoVersamentoDominio tipoDom,
                               String iuv, String ccp) {
        OffsetDateTime data = date(2026, 6, 19);
        Versamento v = new Versamento();
        v.setCodVersamentoEnte("PEND-" + ccp);
        v.setImportoTotale(10.0);
        v.setImportoPagato(0.0);
        v.setStatoVersamento("NON_ESEGUITO");
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
        v.setTipoVersamento(tipo);
        v.setTipoVersamentoDominio(tipoDom);
        versamentoRepository.save(v);

        Rpt r = new Rpt();
        r.setIuv(iuv);
        r.setCcp(ccp);
        r.setCodDominio(dominio.getCodDominio());
        r.setXmlRpt("<RPT/>".getBytes());
        r.setXmlRt(null);
        r.setDataMsgRichiesta(data);
        r.setVersione("SANP_240");
        r.setStato("RPT_ATTIVATA");
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
