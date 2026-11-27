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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.ParameterizedTest;
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
import it.govpay.pendenze.entity.Rpt;
import it.govpay.console.entity.TipoVersamento;
import it.govpay.console.entity.TipoVersamentoDominio;
import it.govpay.console.entity.UnitaOperativa;
import it.govpay.console.entity.Utenza;
import it.govpay.console.entity.UtenzaDominio;
import it.govpay.pendenze.entity.Pendenza;
import it.govpay.pendenze.model.StatoPendenza;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import it.govpay.console.pendenza.PendenzaV3Fixtures;
import it.govpay.console.pendenza.PendenzaV3Fixtures.Dati;
import it.govpay.console.repository.ConsoleApplicazioneRepository;
import it.govpay.console.repository.ConsoleDominioRepository;
import it.govpay.console.repository.GpAuditRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.ConsoleRptRepository;
import it.govpay.console.repository.TipoVersamentoDominioRepository;
import it.govpay.console.repository.TipoVersamentoRepository;
import it.govpay.console.repository.UnitaOperativaRepository;
import it.govpay.console.repository.UtenzaDominioRepository;
import it.govpay.console.repository.UtenzaRepository;

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
    @Autowired private ConsoleDominioRepository dominioRepository;
    @Autowired private ConsoleApplicazioneRepository applicazioneRepository;
    @Autowired private TipoVersamentoRepository tipoVersamentoRepository;
    @Autowired private TipoVersamentoDominioRepository tipoVersamentoDominioRepository;
    @PersistenceContext private EntityManager em;
    @Autowired private ConsoleRptRepository rptRepository;
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

    @ParameterizedTest
    @ValueSource(strings = { "2025-01-15", "2025-07-15" })
    void filtriDataRicevutaERichiestaSulGiornoInOraDiRoma(LocalDate giorno) throws Exception {
        String p = utenteDominiStar("u-giorno");
        Map<String, OffsetDateTime> istanti = Map.of(
                "GIORNOPRIMA", aRoma(giorno.minusDays(1), 23, 30),
                "GIORNOINIZI", aRoma(giorno, 0, 30),
                "GIORNOFINE0", aRoma(giorno, 23, 30),
                "GIORNODOPO0", aRoma(giorno.plusDays(1), 0, 30));
        istanti.forEach((iuv, istante) -> newRpt(domA, tvA, tvdA, iuv, "CCP-" + iuv, istante, istante, 10.0,
                "SANP_240"));

        for (String filtro : List.of("dataRicevuta", "dataRichiesta")) {
            mvc.perform(get("/ricevute").param(filtro + "Da", giorno.toString())
                            .param(filtro + "A", giorno.toString()).with(httpBasic(p, PASSWORD)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results[*].iuv", containsInAnyOrder("GIORNOINIZI", "GIORNOFINE0")));
        }
    }

    private static OffsetDateTime aRoma(LocalDate giorno, int ora, int minuti) {
        return giorno.atTime(ora, minuti).atZone(ZoneId.of("Europe/Rome")).toOffsetDateTime();
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

        Pendenza v = pendenza("PEND-AAAAAAAAAA1");
        v.getOpzionePagamento().getPosizioneDebitoria().setIdUnitaOperativa(uo.getId());

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
        Pendenza v1 = pendenza("PEND-AAAAAAAAAA1");
        v1.setDirezione("DIR-1");
        v1.setDivisione("DIV-1");
        Pendenza v2 = pendenza("PEND-AAAAAAAAAA2");
        v2.setDirezione("DIR-2");
        v2.setDivisione("DIV-2");

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
        Pendenza v = pendenza("PEND-AAAAAAAAAA1");
        v.setTassonomia("TAX-001");

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

    // ----- anagraficaDebitore (issue #68 §C) ----------------------------------

    @Test
    void filterByAnagraficaDebitorePartialCaseInsensitive() throws Exception {
        String p = utenteDominiStar("u-anagrafica");
        Pendenza v = pendenza("PEND-AAAAAAAAAA1");
        v.getOpzionePagamento().getPdDebitore().getDebitore().setAnagrafica("Anna Verdi");

        // le altre 4 righe fixture hanno "Mario Rossi": contains su "ver", case-insensitive.
        mvc.perform(get("/ricevute?anagraficaDebitore=VER").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].iuv", is("AAAAAAAAAA1")));
    }

    /**
     * `%` e `_` nel termine cercato devono restare caratteri letterali, non
     * wildcard SQL: senza escaping, `%%%` o `___` (3 caratteri, superano la
     * soglia minima) matcherebbero quasi tutto l'archivio invece di cercare
     * letteralmente quella sequenza. Nessuna fixture ha quei caratteri nel
     * nome: il match letterale corretto e' zero risultati.
     */
    @Test
    void anagraficaDebitoreConWildcardPercentTrattatoLetteralmente() throws Exception {
        String p = utenteDominiStar("u-anagraficawild1");
        mvc.perform(get("/ricevute").param("anagraficaDebitore", "%%%").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(0)));
    }

    @Test
    void anagraficaDebitoreConWildcardUnderscoreTrattatoLetteralmente() throws Exception {
        String p = utenteDominiStar("u-anagraficawild2");
        mvc.perform(get("/ricevute").param("anagraficaDebitore", "___").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(0)));
    }

    /** Azione di audit dedicata (issue #68 §C), distinta da quella di {@code identificativoDebitore}. */
    @Test
    void filterByAnagraficaDebitoreWritesDedicatedAudit() throws Exception {
        String p = utenteDominiStar("u-anagraficaaudit");
        long auditBefore = gpAuditRepository.count();

        mvc.perform(get("/ricevute?anagraficaDebitore=ros").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(5)));

        assertThat(gpAuditRepository.count()).isEqualTo(auditBefore + 1);
        assertThat(gpAuditRepository.findAll().stream()
                .anyMatch(a -> "RICEVUTE_RICERCA_PER_ANAGRAFICA_DEBITORE".equals(a.getTipoOggetto())))
                .as("azione di audit dedicata attesa")
                .isTrue();
    }

    @Test
    void anagraficaDebitoreSottoLunghezzaMinimaReturns400() throws Exception {
        String p = utenteDominiStar("u-anagraficamin");
        mvc.perform(get("/ricevute?anagraficaDebitore=ro").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("anagraficaDebitore")));
    }

    @Test
    void anagraficaDebitoreConLimitOltreSogliaReturns400() throws Exception {
        String p = utenteDominiStar("u-anagraficalimit");
        mvc.perform(get("/ricevute?anagraficaDebitore=ros&limit=51").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("limit")));

        // al limite consentito, nessun 400.
        mvc.perform(get("/ricevute?anagraficaDebitore=ros&limit=50").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk());
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
        Pendenza v = PendenzaV3Fixtures.pendenza(em, new Dati(app.getId(), domA.getId(),
                tvA.getId(), tvdA.getId(), null, "PEND-NODATA", null, "RSSMRA80A01H501U", 10.0,
                StatoPendenza.ESEGUITO, 0));
        v.setImportoPagato(10.0);
        v.setDataCreazione(date(2026, 6, 19));
        v.setDataUltimoAggiornamento(date(2026, 6, 19));
        v.getOpzionePagamento().getPdDebitore().getDebitore().setAnagrafica("Mario Rossi");
        Rpt r = new Rpt();
        r.setCodStazione("STAZIONE-TEST");
        r.setCodMsgRichiesta("MSG-RICHIESTA");
        r.setDataAggiornamentoStato(java.time.OffsetDateTime.now());
        r.setIuv("NODATAIUV01");
        r.setIur("CCP-ND");
        r.setCodDominio(DOM_A);
        r.setXmlRt("<RT/>".getBytes());
        r.setImportoTotalePagato(10.0);
        r.setDataMsgRichiesta(date(2026, 6, 19));
        r.setDataMsgRicevuta(null);
        r.setVersione("SANP_240");
        r.setStato("RT_ACCETTATA_PA");
        r.setIdVersamento(v.getId());
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

    /**
     * Issue #68: la paginazione a cursore va verificata anche con filtri attivi,
     * non solo senza (acceptance criterion esplicito). Fixture: 3 RT sul dominio
     * A (piu' del limit=2, cosi' servono due pagine) e 2 RT sul dominio B (il
     * "risultato non corrispondente" che il filtro deve escludere su entrambe
     * le pagine). Verifica: nessun elemento del dominio B, nessun duplicato fra
     * le pagine, nessun elemento perso — l'unione delle due pagine e' esattamente
     * l'insieme filtrato.
     */
    @Test
    void cursorConFiltroAttivoNonPerdeNonDuplicaNonAmpliaIRisultati() throws Exception {
        String p = utenteDominiStar("u-curfiltro");

        String firstBody = mvc.perform(get("/ricevute?cursor=&idDominio=" + DOM_A + "&limit=2")
                        .with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(2)))
                .andExpect(jsonPath("$.results[*].idDominio", contains(DOM_A, DOM_A)))
                .andExpect(jsonPath("$.results[*].iuv", contains("AAAAAAAAAA3", "AAAAAAAAAA2")))
                .andExpect(jsonPath("$.nextCursor").exists())
                .andReturn().getResponse().getContentAsString();
        String next = com.jayway.jsonpath.JsonPath.read(firstBody, "$.nextCursor");

        mvc.perform(get("/ricevute?cursor=" + next + "&idDominio=" + DOM_A + "&limit=2")
                        .with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].idDominio", is(DOM_A)))
                .andExpect(jsonPath("$.results[0].iuv", is("AAAAAAAAAA1")))
                // ultima pagina: nessun elemento del dominio B "recuperato" per errore, nessun nextCursor.
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
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

    private Pendenza pendenza(String idPendenza) {
        return em.createQuery("select p from Pendenza p where p.idPendenza = :id", Pendenza.class)
                .setParameter("id", idPendenza).getSingleResult();
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
        Pendenza v = PendenzaV3Fixtures.pendenza(em, new Dati(app.getId(), dominio.getId(),
                tv.getId(), tvd.getId(), null, "PEND-" + iuv, null, "RSSMRA80A01H501U", importo,
                StatoPendenza.ESEGUITO, 0));
        v.setImportoPagato(importo);
        v.setDataCreazione(dataRicevuta);
        v.setDataUltimoAggiornamento(dataRicevuta);
        v.getOpzionePagamento().getPdDebitore().getDebitore().setAnagrafica("Mario Rossi");

        Rpt r = new Rpt();
        r.setCodStazione("STAZIONE-TEST");
        r.setCodMsgRichiesta("MSG-RICHIESTA");
        r.setDataAggiornamentoStato(java.time.OffsetDateTime.now());
        r.setIuv(iuv);
        r.setIur(ccp);
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
        r.setIdVersamento(v.getId());
        rptRepository.save(r);
    }

    /** Riga {@code rpt} con sola richiesta: RT non ancora presente (xml_rt e data RT null). */
    private void newRptSenzaRt(Dominio dominio, TipoVersamento tipo, TipoVersamentoDominio tipoDom,
                               String iuv, String ccp) {
        OffsetDateTime data = date(2026, 6, 19);
        Pendenza v = PendenzaV3Fixtures.pendenza(em, new Dati(app.getId(), dominio.getId(),
                tipo.getId(), tipoDom.getId(), null, "PEND-" + ccp, null, "RSSMRA80A01H501U", 10.0,
                StatoPendenza.NON_ESEGUITO, 0));
        v.setImportoPagato(0.0);
        v.setDataCreazione(data);
        v.setDataUltimoAggiornamento(data);
        v.getOpzionePagamento().getPdDebitore().getDebitore().setAnagrafica("Mario Rossi");

        Rpt r = new Rpt();
        r.setCodStazione("STAZIONE-TEST");
        r.setCodMsgRichiesta("MSG-RICHIESTA");
        r.setDataAggiornamentoStato(java.time.OffsetDateTime.now());
        r.setIuv(iuv);
        r.setIur(ccp);
        r.setCodDominio(dominio.getCodDominio());
        r.setXmlRpt("<RPT/>".getBytes());
        r.setXmlRt(null);
        r.setDataMsgRichiesta(data);
        r.setVersione("SANP_240");
        r.setStato("RPT_ATTIVATA");
        r.setIdVersamento(v.getId());
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
