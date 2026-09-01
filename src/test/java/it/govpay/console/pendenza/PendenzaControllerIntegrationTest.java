package it.govpay.console.pendenza;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.entity.Applicazione;
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.Operatore;
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
import it.govpay.console.repository.TipoVersamentoDominioRepository;
import it.govpay.console.repository.TipoVersamentoRepository;
import it.govpay.console.repository.UnitaOperativaRepository;
import it.govpay.console.repository.UtenzaDominioRepository;
import it.govpay.console.repository.UtenzaRepository;
import it.govpay.console.repository.VersamentoRepository;
import it.govpay.common.auth.GovpayPasswordEncoder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PendenzaControllerIntegrationTest {

    private static final String PRINCIPAL = "operatore1";
    private static final String PASSWORD = "secret";

    private static final String APP_COD = "APP-A";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private GovpayPasswordEncoder encoder;
    @Autowired
    private UtenzaRepository utenzaRepository;
    @Autowired
    private UtenzaDominioRepository utenzaDominioRepository;
    @Autowired
    private OperatoreRepository operatoreRepository;
    @Autowired
    private DominioRepository dominioRepository;
    @Autowired
    private ApplicazioneRepository applicazioneRepository;
    @Autowired
    private TipoVersamentoRepository tipoVersamentoRepository;
    @Autowired
    private UnitaOperativaRepository unitaOperativaRepository;
    @Autowired
    private VersamentoRepository versamentoRepository;
    @Autowired
    private TipoVersamentoDominioRepository tipoVersamentoDominioRepository;
    @Autowired
    private GpAuditRepository gpAuditRepository;


    @BeforeEach
    void setup() {
        Utenza utenza = new Utenza();
        utenza.setPrincipal(PRINCIPAL);
        utenza.setPrincipalOriginale(PRINCIPAL);
        utenza.setAbilitato(true);
        utenza.setAutorizzazioneDominiStar(false);
        utenza.setAutorizzazioneTipiVersStar(true);
        utenza.setRuoli("OPERATORE");
        utenza.setPassword(encoder.encode(PASSWORD));
        utenzaRepository.save(utenza);

        Operatore op = new Operatore();
        op.setNome("Operatore Uno");
        op.setIdUtenza(utenza.getId());
        operatoreRepository.save(op);

        Dominio domA = newDominio("11111111111", "Dominio A");
        Dominio domB = newDominio("22222222222", "Dominio B");
        Dominio domC = newDominio("33333333333", "Dominio Non Visibile");

        link(utenza.getId(), domA.getId());
        link(utenza.getId(), domB.getId());
        // domC NON associato

        Applicazione app = new Applicazione();
        app.setCodApplicazione(APP_COD);
        applicazioneRepository.save(app);

        TipoVersamento tv = new TipoVersamento();
        tv.setCodTipoVersamento("TARI");
        tv.setDescrizione("Tassa rifiuti");
        tipoVersamentoRepository.save(tv);

        TipoVersamentoDominio tvdA = newTvd(domA, tv);
        TipoVersamentoDominio tvdB = newTvd(domB, tv);
        TipoVersamentoDominio tvdC = newTvd(domC, tv);

        UnitaOperativa uo = new UnitaOperativa();
        uo.setCodUo("UO1");
        uo.setUoDenominazione("Ufficio Tributi A");
        uo.setDominio(domA);
        unitaOperativaRepository.save(uo);

        // 3 pendenze su dominio A
        newPendenza("PEND-A-001", domA, app, tv, tvdA, uo, "RSSMRA80A01H501U", "001234567890123456", 100.0,
                "NON_ESEGUITO", 1);
        newPendenza("PEND-A-002", domA, app, tv, tvdA, null, "VRDLGI90B02H501T", "001234567890123457", 50.0,
                "ESEGUITA", 2);
        newPendenza("PEND-A-003", domA, app, tv, tvdA, null, "BNCMRA70C03H501S", null, 75.0,
                "NON_ESEGUITO", 3);

        // 2 pendenze su dominio B
        newPendenza("PEND-B-001", domB, app, tv, tvdB, null, "RSSMRA80A01H501U", "001999999999999999", 30.0,
                "NON_ESEGUITO", 4);
        newPendenza("PEND-B-002", domB, app, tv, tvdB, null, "GLGFNC85D04H501R", null, 200.0,
                "ANNULLATA", 5);

        // 1 pendenza su dominio C (non visibile all'operatore)
        newPendenza("PEND-C-001", domC, app, tv, tvdC, null, "RSSMRA80A01H501U", null, 999.0,
                "NON_ESEGUITO", 6);

        // Pendenza non eseguita ma con data scadenza nel PASSATO -> stato V2 derivato a SCADUTA.
        Versamento scaduta = new Versamento();
        scaduta.setCodVersamentoEnte("PEND-SCADUTA");
        scaduta.setImportoTotale(42.0);
        scaduta.setStatoVersamento("NON_ESEGUITO");
        scaduta.setDataCreazione(OffsetDateTime.now().minusHours(10));
        scaduta.setDataOraUltimoAggiornamento(OffsetDateTime.now().minusHours(10));
        scaduta.setDataScadenza(OffsetDateTime.now().minusDays(7));
        scaduta.setDebitoreIdentificativo("RSSMRA80A01H501U");
        scaduta.setDebitoreAnagrafica("Mario Rossi");
        scaduta.setSrcDebitoreIdentificativo("RSSMRA80A01H501U");
        scaduta.setImportoPagato(0.0);
        scaduta.setAnomalo(false);
        scaduta.setAck(false);
        scaduta.setTipo("DOVUTO");
        scaduta.setDominio(domA);
        scaduta.setApplicazione(app);
        scaduta.setTipoVersamento(tv);
        scaduta.setTipoVersamentoDominio(tvdA);
        versamentoRepository.save(scaduta);

        // Pendenza non eseguita con data scadenza nel FUTURO -> stato V2 = NON_PAGATA.
        Versamento futura = new Versamento();
        futura.setCodVersamentoEnte("PEND-FUTURA");
        futura.setImportoTotale(15.0);
        futura.setStatoVersamento("NON_ESEGUITO");
        futura.setDataCreazione(OffsetDateTime.now().minusHours(11));
        futura.setDataOraUltimoAggiornamento(OffsetDateTime.now().minusHours(11));
        futura.setDataScadenza(OffsetDateTime.now().plusDays(7));
        futura.setDebitoreIdentificativo("RSSMRA80A01H501U");
        futura.setDebitoreAnagrafica("Mario Rossi");
        futura.setSrcDebitoreIdentificativo("RSSMRA80A01H501U");
        futura.setImportoPagato(0.0);
        futura.setAnomalo(false);
        futura.setAck(false);
        futura.setTipo("DOVUTO");
        futura.setDominio(domA);
        futura.setApplicazione(app);
        futura.setTipoVersamento(tv);
        futura.setTipoVersamentoDominio(tvdA);
        versamentoRepository.save(futura);
    }

    private Dominio newDominio(String cod, String rs) {
        Dominio d = new Dominio();
        d.setCodDominio(cod);
        d.setRagioneSociale(rs);
        d.setAuxDigit(0);
        return dominioRepository.save(d);
    }

    private TipoVersamentoDominio newTvd(Dominio d, TipoVersamento tv) {
        TipoVersamentoDominio tvd = new TipoVersamentoDominio();
        tvd.setDominio(d);
        tvd.setTipoVersamento(tv);
        return tipoVersamentoDominioRepository.save(tvd);
    }

    private void link(Long idUtenza, Long idDominio) {
        UtenzaDominio ud = new UtenzaDominio();
        ud.setIdUtenza(idUtenza);
        ud.setIdDominio(idDominio);
        utenzaDominioRepository.save(ud);
    }

    private void newPendenza(String idPendenza, Dominio dom, Applicazione app, TipoVersamento tv,
                             TipoVersamentoDominio tvd, UnitaOperativa uo, String debitore,
                             String numAvviso, double importo, String statoV1, int orderOffsetHours) {
        Versamento v = new Versamento();
        v.setCodVersamentoEnte(idPendenza);
        v.setImportoTotale(importo);
        v.setStatoVersamento(statoV1);
        v.setDataCreazione(OffsetDateTime.now().minusHours(orderOffsetHours));
        v.setDataOraUltimoAggiornamento(OffsetDateTime.now().minusHours(orderOffsetHours));
        v.setDebitoreIdentificativo(debitore);
        v.setDebitoreAnagrafica("Anagrafica " + debitore);
        v.setSrcDebitoreIdentificativo(debitore);
        v.setImportoPagato(0.0);
        v.setAnomalo(false);
        v.setAck(false);
        v.setTipo("DOVUTO");
        v.setCausaleVersamento("Causale " + idPendenza);
        v.setDataValidita(OffsetDateTime.parse("2026-03-15T10:30:00Z"));
        v.setNumeroAvviso(numAvviso);
        v.setDominio(dom);
        v.setApplicazione(app);
        v.setTipoVersamento(tv);
        v.setTipoVersamentoDominio(tvd);
        v.setUnitaOperativa(uo);
        versamentoRepository.save(v);
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/pendenze"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void listFilteredByAcl() throws Exception {
        mvc.perform(get("/pendenze").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(7)))
                .andExpect(jsonPath("$.results[*].idPendenza",
                        containsInAnyOrder("PEND-A-001", "PEND-A-002", "PEND-A-003",
                                "PEND-B-001", "PEND-B-002",
                                "PEND-SCADUTA", "PEND-FUTURA")))
                .andExpect(jsonPath("$.pagination.page", is(1)))
                .andExpect(jsonPath("$.pagination.limit", is(25)))
                .andExpect(jsonPath("$.pagination.hasNextPage", is(false)))
                .andExpect(jsonPath("$.pagination.totalResults").doesNotExist())
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void totalTrueIncludesCount() throws Exception {
        mvc.perform(get("/pendenze").param("total", "true").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalResults", is(7)))
                .andExpect(jsonPath("$.pagination.totalPages", is(1)));
    }

    @Test
    void filterByIdDominio() throws Exception {
        mvc.perform(get("/pendenze").param("idDominio", "11111111111")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(5)))
                .andExpect(jsonPath("$.results[*].dominio.idDominio",
                        containsInAnyOrder("11111111111", "11111111111", "11111111111",
                                "11111111111", "11111111111")));
    }

    @Test
    void filterByNumeroAvviso() throws Exception {
        mvc.perform(get("/pendenze").param("numeroAvviso", "001234567890123456")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].idPendenza", is("PEND-A-001")));
    }

    @Test
    void filterByIdentificativoDebitoreWritesAudit() throws Exception {
        long auditBefore = gpAuditRepository.count();
        mvc.perform(get("/pendenze").param("identificativoDebitore", "RSSMRA80A01H501U")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(4)))
                .andExpect(jsonPath("$.results[*].idPendenza",
                        containsInAnyOrder("PEND-A-001", "PEND-B-001",
                                "PEND-SCADUTA", "PEND-FUTURA")));
        assertThat(gpAuditRepository.count()).isEqualTo(auditBefore + 1);
    }

    @Test
    void filtersWithoutDebitoreDoNotWriteAudit() throws Exception {
        long auditBefore = gpAuditRepository.count();
        mvc.perform(get("/pendenze").param("idDominio", "11111111111")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk());
        assertThat(gpAuditRepository.count()).isEqualTo(auditBefore);
    }

    @Test
    void filterByIdPendenzaPartial() throws Exception {
        mvc.perform(get("/pendenze").param("idPendenza", "PEND-B").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(2)))
                .andExpect(jsonPath("$.results[*].idPendenza",
                        containsInAnyOrder("PEND-B-001", "PEND-B-002")));
    }

    /**
     * `%`/`_` nel termine devono restare caratteri letterali (via
     * {@link it.govpay.console.common.LikePatterns}), non wildcard SQL: senza
     * escaping `_______` (7 underscore, quanti "PEND-B-" e' lungo) matcherebbe
     * qualunque idPendenza di 7+ caratteri invece di cercare letteralmente
     * quella sequenza — nessuna fixture ha underscore nel nome.
     */
    @Test
    void filterByIdPendenzaConWildcardTrattatoLetteralmente() throws Exception {
        mvc.perform(get("/pendenze").param("idPendenza", "_______").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(0)));
    }

    @Test
    void defaultSortByDataCreazioneDesc() throws Exception {
        mvc.perform(get("/pendenze").param("idDominio", "11111111111")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idPendenza",
                        contains("PEND-A-001", "PEND-A-002", "PEND-A-003",
                                "PEND-SCADUTA", "PEND-FUTURA")));
    }

    /**
     * Issue #66 scope E: il default deve ordinare per {@code dataCreazione}, non
     * piu' per {@code dataUltimoAggiornamento}. Fixture dedicata con i due campi
     * deliberatamente in disaccordo (dataCreazione decrescente, dataUltimoAggiornamento
     * costante): se il default tornasse a ordinare sul campo vecchio, l'ordine atteso
     * non reggerebbe.
     */
    @Test
    void defaultSortUsesDataCreazioneNotDataUltimoAggiornamento() throws Exception {
        Dominio dom = dominioRepository.findByCodDominio("11111111111").orElseThrow();
        Applicazione app = applicazioneRepository.findByCodApplicazione(APP_COD).orElseThrow();
        TipoVersamento tv = tipoVersamentoRepository.findByCodTipoVersamento("TARI").orElseThrow();
        TipoVersamentoDominio tvd = tipoVersamentoDominioRepository
                .findByDominio_IdAndTipoVersamento_CodTipoVersamento(dom.getId(), "TARI").orElseThrow();
        OffsetDateTime stessoAggiornamento = OffsetDateTime.now();

        for (int i = 1; i <= 3; i++) {
            Versamento v = new Versamento();
            v.setCodVersamentoEnte("PEND-ORDINE-" + i);
            v.setImportoTotale(10.0);
            v.setStatoVersamento("NON_ESEGUITO");
            v.setDataCreazione(OffsetDateTime.now().minusDays(i));
            v.setDataOraUltimoAggiornamento(stessoAggiornamento);
            v.setDebitoreIdentificativo("RSSMRA80A01H501U");
            v.setDebitoreAnagrafica("Mario Rossi");
            v.setSrcDebitoreIdentificativo("RSSMRA80A01H501U");
            v.setImportoPagato(0.0);
            v.setAnomalo(false);
            v.setAck(false);
            v.setTipo("DOVUTO");
            v.setDominio(dom);
            v.setApplicazione(app);
            v.setTipoVersamento(tv);
            v.setTipoVersamentoDominio(tvd);
            versamentoRepository.save(v);
        }

        mvc.perform(get("/pendenze").param("idPendenza", "PEND-ORDINE").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idPendenza",
                        contains("PEND-ORDINE-1", "PEND-ORDINE-2", "PEND-ORDINE-3")));
    }

    @Test
    void filterByStatoNonPagataEsludeScaduta() throws Exception {
        mvc.perform(get("/pendenze").param("idDominio", "11111111111").param("stato", "NON_PAGATA")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idPendenza",
                        containsInAnyOrder("PEND-A-001", "PEND-A-003", "PEND-FUTURA")));
    }

    @Test
    void filterByStatoScadutaSoloNonEseguiteScadute() throws Exception {
        mvc.perform(get("/pendenze").param("stato", "SCADUTA").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idPendenza", contains("PEND-SCADUTA")));
    }

    @Test
    void filterByStatoPagata() throws Exception {
        mvc.perform(get("/pendenze").param("stato", "PAGATA").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idPendenza", contains("PEND-A-002")));
    }

    /**
     * Stati interni equivalenti a ESEGUITO (bug scoperto lavorando su #66,
     * corretto anche in {@link PendenzaMapper#mapStato}): il filtro deve
     * trovarli, non solo il mapper di output.
     */
    @Test
    void filterByStatoPagataIncludeStatiInterniEquivalenti() throws Exception {
        Versamento altroCanale = versamentoRepository.findDetail(APP_COD, "PEND-A-001").orElseThrow();
        altroCanale.setStatoVersamento("ESEGUITO_ALTRO_CANALE");
        versamentoRepository.save(altroCanale);

        Versamento senzaRpt = versamentoRepository.findDetail(APP_COD, "PEND-A-003").orElseThrow();
        senzaRpt.setStatoVersamento("ESEGUITO_SENZA_RPT");
        versamentoRepository.save(senzaRpt);

        mvc.perform(get("/pendenze").param("idDominio", "11111111111").param("stato", "PAGATA")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idPendenza",
                        containsInAnyOrder("PEND-A-001", "PEND-A-002", "PEND-A-003")));
    }

    /** Acceptance criteria issue #66: valore V1 non valido per 'stato' elenca i valori ammessi. */
    @Test
    void statoValoreInvalidoElencaValoriAmmessi() throws Exception {
        mvc.perform(get("/pendenze").param("stato", "BOGUS").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("Valori ammessi")))
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("NON_PAGATA")))
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("PAGATA")));
    }

    @Test
    void filterByStatoAnnullata() throws Exception {
        mvc.perform(get("/pendenze").param("stato", "ANNULLATA").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idPendenza", contains("PEND-B-002")));
    }

    /**
     * Il mapper mappa lo stato grezzo letterale SCADUTA/SCADUTO direttamente a
     * SCADUTA, indipendentemente da data_scadenza: il filtro deve trovarla,
     * non solo la variante derivata da NON_ESEGUITO + scadenza passata.
     */
    @Test
    void filterByStatoScadutaTrovaAncheIlValoreGrezzoLetterale() throws Exception {
        Versamento v = versamentoRepository.findDetail(APP_COD, "PEND-A-001").orElseThrow();
        v.setStatoVersamento("SCADUTA");
        v.setDataScadenza(null);
        versamentoRepository.save(v);

        mvc.perform(get("/pendenze").param("stato", "SCADUTA").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idPendenza",
                        containsInAnyOrder("PEND-A-001", "PEND-SCADUTA")));
    }

    /**
     * PARZIALMENTE_ESEGUITO e' il nome V1 canonico dello stato (oltre alle
     * varianti ESEGUITA_PARZIALE/ESEGUITO_PARZIALE): il mapper lo mostra come
     * PAGATA_PARZIALE, quindi il filtro deve trovarlo con lo stesso nome.
     */
    @Test
    void filterByStatoPagataParzialeTrovaAncheParzialmenteEseguito() throws Exception {
        Versamento v = versamentoRepository.findDetail(APP_COD, "PEND-A-001").orElseThrow();
        v.setStatoVersamento("PARZIALMENTE_ESEGUITO");
        versamentoRepository.save(v);

        mvc.perform(get("/pendenze").param("stato", "PAGATA_PARZIALE").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idPendenza", contains("PEND-A-001")));
    }

    /**
     * ANOMALA nel mapper e' il catch-all per qualunque valore grezzo non
     * riconosciuto: il filtro deve trovare anche quelle righe, non solo i
     * letterali ANOMALA/ANOMALO.
     */
    @Test
    void filterByStatoAnomalaTrovaAncheValoriGrezziSconosciuti() throws Exception {
        Versamento v = versamentoRepository.findDetail(APP_COD, "PEND-A-001").orElseThrow();
        v.setStatoVersamento("QUALCOSA_DI_INESISTENTE");
        versamentoRepository.save(v);

        mvc.perform(get("/pendenze").param("stato", "ANOMALA").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idPendenza", contains("PEND-A-001")));
    }

    @Test
    void filterByDataRangeSuDataCreazione() throws Exception {
        // Fixture dedicata con offset in giorni: gli offset in ore del setup
        // condiviso (1..11h) sono troppo vicini al confine di mezzanotte per
        // dare un test deterministico indipendente dall'orario di esecuzione.
        Dominio dom = dominioRepository.findByCodDominio("11111111111").orElseThrow();
        Applicazione app = applicazioneRepository.findByCodApplicazione(APP_COD).orElseThrow();
        TipoVersamento tv = tipoVersamentoRepository.findByCodTipoVersamento("TARI").orElseThrow();
        TipoVersamentoDominio tvd = tipoVersamentoDominioRepository
                .findByDominio_IdAndTipoVersamento_CodTipoVersamento(dom.getId(), "TARI").orElseThrow();

        salvaVersamentoCombinazione("PEND-RANGE-DENTRO", dom, app, tv, tvd, "NON_ESEGUITO", 1, null);
        salvaVersamentoCombinazione("PEND-RANGE-FUORI", dom, app, tv, tvd, "NON_ESEGUITO", 10, null);

        java.time.LocalDate oggi = java.time.LocalDate.now();
        mvc.perform(get("/pendenze").param("idPendenza", "PEND-RANGE")
                        .param("dataDa", oggi.minusDays(2).toString()).param("dataA", oggi.toString())
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idPendenza", contains("PEND-RANGE-DENTRO")));
    }

    @Test
    void dataDaSuccessivaADataAReturns400() throws Exception {
        mvc.perform(get("/pendenze")
                        .param("dataDa", "2026-06-15").param("dataA", "2026-06-01")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("dataDa")));
    }

    @Test
    void filterByIuv() throws Exception {
        Versamento withIuv = versamentoRepository.findDetail(APP_COD, "PEND-A-001").orElseThrow();
        withIuv.setIuvVersamento("IUV-TEST-001");
        versamentoRepository.save(withIuv);

        mvc.perform(get("/pendenze").param("iuv", "IUV-TEST-001").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idPendenza", contains("PEND-A-001")));
    }

    @Test
    void filterByDirezione() throws Exception {
        Versamento v = versamentoRepository.findDetail(APP_COD, "PEND-A-001").orElseThrow();
        v.setDirezione("DIR-1");
        versamentoRepository.save(v);

        mvc.perform(get("/pendenze").param("direzione", "DIR-1").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idPendenza", contains("PEND-A-001")));
    }

    @Test
    void filterByDivisione() throws Exception {
        Versamento v = versamentoRepository.findDetail(APP_COD, "PEND-A-001").orElseThrow();
        v.setDivisione("DIV-1");
        versamentoRepository.save(v);

        mvc.perform(get("/pendenze").param("divisione", "DIV-1").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idPendenza", contains("PEND-A-001")));
    }

    /** direzione/divisione: cardinalita' allineata a /ricevute (issue #68), semantica OR come idTipoPendenza. */
    @Test
    void filterByDirezioneMultiploUnisceIRisultati() throws Exception {
        Versamento v1 = versamentoRepository.findDetail(APP_COD, "PEND-A-001").orElseThrow();
        v1.setDirezione("DIR-1");
        versamentoRepository.save(v1);
        Versamento v2 = versamentoRepository.findDetail(APP_COD, "PEND-A-002").orElseThrow();
        v2.setDirezione("DIR-2");
        versamentoRepository.save(v2);

        mvc.perform(get("/pendenze").param("direzione", "DIR-1")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idPendenza", contains("PEND-A-001")));

        mvc.perform(get("/pendenze").param("direzione", "DIR-1,DIR-2")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idPendenza",
                        containsInAnyOrder("PEND-A-001", "PEND-A-002")));
    }

    @Test
    void filterByDivisioneMultiploUnisceIRisultati() throws Exception {
        Versamento v1 = versamentoRepository.findDetail(APP_COD, "PEND-A-001").orElseThrow();
        v1.setDivisione("DIV-1");
        versamentoRepository.save(v1);
        Versamento v2 = versamentoRepository.findDetail(APP_COD, "PEND-A-002").orElseThrow();
        v2.setDivisione("DIV-2");
        versamentoRepository.save(v2);

        mvc.perform(get("/pendenze").param("divisione", "DIV-1,DIV-2")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idPendenza",
                        containsInAnyOrder("PEND-A-001", "PEND-A-002")));
    }

    @Test
    void direzioneEDivisioneVuoteOSoloSeparatoriReturns400() throws Exception {
        mvc.perform(get("/pendenze").param("direzione", ",,").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("direzione")));

        mvc.perform(get("/pendenze").param("divisione", ",,").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("divisione")));
    }

    /**
     * Combinazione richiesta dagli acceptance criteria: stato + range data.
     * Fixture dedicata con offset in giorni (non ore, a differenza del resto
     * del setup): un confine a livello di giorno su offset dell'ordine
     * dell'ora e' intrinsecamente ambiguo rispetto al momento in cui gira il
     * test (dipende da quanto manca a mezzanotte), quindi qui serve una
     * separazione netta.
     */
    @Test
    void combinazioneStatoEDataRange() throws Exception {
        Dominio dom = dominioRepository.findByCodDominio("11111111111").orElseThrow();
        Applicazione app = applicazioneRepository.findByCodApplicazione(APP_COD).orElseThrow();
        TipoVersamento tv = tipoVersamentoRepository.findByCodTipoVersamento("TARI").orElseThrow();
        TipoVersamentoDominio tvd = tipoVersamentoDominioRepository
                .findByDominio_IdAndTipoVersamento_CodTipoVersamento(dom.getId(), "TARI").orElseThrow();

        // Dentro il range richiesto, NON_PAGATA: deve comparire.
        salvaVersamentoCombinazione("PEND-COMBO-DENTRO", dom, app, tv, tvd, "NON_ESEGUITO", 1, null);
        // Dentro il range richiesto, ma PAGATA: non deve comparire (stato non combacia).
        salvaVersamentoCombinazione("PEND-COMBO-PAGATA", dom, app, tv, tvd, "ESEGUITO", 1, null);
        // Fuori dal range richiesto (10 giorni fa), NON_PAGATA: non deve comparire (data non combacia).
        salvaVersamentoCombinazione("PEND-COMBO-FUORI-RANGE", dom, app, tv, tvd, "NON_ESEGUITO", 10, null);

        java.time.LocalDate oggi = java.time.LocalDate.now();
        mvc.perform(get("/pendenze").param("idPendenza", "PEND-COMBO")
                        .param("stato", "NON_PAGATA")
                        .param("dataDa", oggi.minusDays(2).toString()).param("dataA", oggi.toString())
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idPendenza", contains("PEND-COMBO-DENTRO")));
    }

    private void salvaVersamentoCombinazione(String idPendenza, Dominio dom, Applicazione app, TipoVersamento tv,
                                             TipoVersamentoDominio tvd, String statoV1, int giorniFa,
                                             OffsetDateTime dataScadenza) {
        Versamento v = new Versamento();
        v.setCodVersamentoEnte(idPendenza);
        v.setImportoTotale(10.0);
        v.setStatoVersamento(statoV1);
        v.setDataCreazione(OffsetDateTime.now().minusDays(giorniFa));
        v.setDataOraUltimoAggiornamento(OffsetDateTime.now());
        v.setDataScadenza(dataScadenza);
        v.setDebitoreIdentificativo("RSSMRA80A01H501U");
        v.setDebitoreAnagrafica("Mario Rossi");
        v.setSrcDebitoreIdentificativo("RSSMRA80A01H501U");
        v.setImportoPagato(0.0);
        v.setAnomalo(false);
        v.setAck(false);
        v.setTipo("DOVUTO");
        v.setDominio(dom);
        v.setApplicazione(app);
        v.setTipoVersamento(tv);
        v.setTipoVersamentoDominio(tvd);
        versamentoRepository.save(v);
    }

    /** ACL prevale anche sui nuovi filtri: dominio non visibile -> lista vuota, non 403. */
    @Test
    void aclPrevaleSuNuoviFiltri() throws Exception {
        mvc.perform(get("/pendenze").param("idDominio", "33333333333").param("stato", "NON_PAGATA")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", empty()));
    }

    @Test
    void customSortDirectionByDataScadenza() throws Exception {
        // dataScadenza ASC: prima i null (PEND-A-001/002/003), poi le date crescenti
        // (PEND-SCADUTA -7d, PEND-FUTURA +7d).
        mvc.perform(get("/pendenze").param("idDominio", "11111111111").param("sort", "+dataScadenza")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idPendenza",
                        org.hamcrest.Matchers.hasSize(5)));
    }

    /**
     * Issue #9 scope H: il sort deve supportare multi-field
     * ({@code ?sort=-dataScadenza,dataUltimoAggiornamento}).
     */
    @Test
    void multiFieldSortIsAccepted() throws Exception {
        mvc.perform(get("/pendenze").param("idDominio", "11111111111")
                        .param("sort", "-dataScadenza,dataUltimoAggiornamento")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idPendenza",
                        org.hamcrest.Matchers.hasSize(5)));
    }

    @Test
    void unknownSortFieldReturns400() throws Exception {
        mvc.perform(get("/pendenze").param("sort", "-bogusField")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.detail",
                        org.hamcrest.Matchers.containsString("bogusField")));
    }

    /** Issue #66 scope E: la chiave rinominata da dataCaricamento non e' piu' riconosciuta. */
    @Test
    void oldSortKeyDataCaricamentoReturns400() throws Exception {
        mvc.perform(get("/pendenze").param("sort", "dataCaricamento")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.detail",
                        org.hamcrest.Matchers.containsString("dataCaricamento")));
    }

    @Test
    void newSortKeyDataCreazioneIsAccepted() throws Exception {
        mvc.perform(get("/pendenze").param("idDominio", "11111111111").param("sort", "-dataCreazione")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idPendenza",
                        contains("PEND-A-001", "PEND-A-002", "PEND-A-003",
                                "PEND-SCADUTA", "PEND-FUTURA")));
    }

    @Test
    void unsupportedQueryParamReturns400() throws Exception {
        // Issue #66 scope C: esclusione esplicita, non deve mai comparire nell'OpenAPI.
        mvc.perform(get("/pendenze").param("mostraSpontaneiNonPagati", "true")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.detail",
                        org.hamcrest.Matchers.containsString("Filtro non supportato")));
    }

    @Test
    void limitOver200Returns400() throws Exception {
        mvc.perform(get("/pendenze").param("limit", "5000").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    /**
     * Issue #9 scope H: l'audit GDPR {@code PENDENZE_RICERCA_PER_DEBITORE}
     * viene scritto SOLO quando il filtro {@code identificativoDebitore} e'
     * valorizzato. Una ricerca senza quel filtro (anche con altri filtri come
     * {@code idDominio}) non deve generare audit.
     */
    @Test
    void searchWithoutIdentificativoDebitoreScriveNessunAudit() throws Exception {
        long auditBefore = gpAuditRepository.findAll().stream()
                .filter(a -> "PENDENZE_RICERCA_PER_DEBITORE".equals(a.getTipoOggetto()))
                .count();

        mvc.perform(get("/pendenze").param("idDominio", "11111111111")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk());

        long auditAfter = gpAuditRepository.findAll().stream()
                .filter(a -> "PENDENZE_RICERCA_PER_DEBITORE".equals(a.getTipoOggetto()))
                .count();

        org.assertj.core.api.Assertions.assertThat(auditAfter)
                .as("ricerca senza identificativoDebitore: nessun audit GDPR atteso")
                .isEqualTo(auditBefore);
    }

    @Test
    void unknownDomainGivesEmptyList() throws Exception {
        mvc.perform(get("/pendenze").param("idDominio", "33333333333")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", empty()))
                .andExpect(jsonPath("$.pagination.hasNextPage", is(false)));
    }

    @Test
    void pendenzaSummaryShapeContainsExpectedFields() throws Exception {
        mvc.perform(get("/pendenze").param("idPendenza", "PEND-A-001")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].idA2A", is(APP_COD)))
                .andExpect(jsonPath("$.results[0].stato", is("NON_PAGATA")))
                .andExpect(jsonPath("$.results[0].importo", is(100.0)))
                .andExpect(jsonPath("$.results[0].causale", org.hamcrest.Matchers.notNullValue()))
                // dataValidita e' una data pura (format: date): nessun orario nel payload.
                .andExpect(jsonPath("$.results[0].dataValidita", is("2026-03-15")))
                .andExpect(jsonPath("$.results[0].dominio.idDominio", is("11111111111")))
                .andExpect(jsonPath("$.results[0].dominio.ragioneSociale", is("Dominio A")))
                .andExpect(jsonPath("$.results[0].tipoPendenza.idTipoPendenza", is("TARI")))
                .andExpect(jsonPath("$.results[0].unitaOperativa.idUnitaOperativa", is("UO1")))
                .andExpect(jsonPath("$.results[0].dataUltimoAggiornamento", org.hamcrest.Matchers.notNullValue()))
                // Campi rimossi dal refactor #9: non devono comparire nel summary.
                .andExpect(jsonPath("$.results[0].tipo").doesNotExist())
                .andExpect(jsonPath("$.results[0].anomalo").doesNotExist())
                .andExpect(jsonPath("$.results[0].verificato").doesNotExist())
                .andExpect(jsonPath("$.results[0].importoPagato").doesNotExist())
                .andExpect(jsonPath("$.results[0].idDebitore").doesNotExist())
                .andExpect(jsonPath("$.results[0].causaleBreve").doesNotExist());
    }

    @Test
    void pendenzaSummaryHasExactlyFifteenFields() throws Exception {
        // Pendenza dedicata con TUTTI i 15 campi del summary popolati (creata nel
        // test per non alterare i conteggi degli altri): la proiezione deve esporne
        // esattamente 15, cosi' l'aggiunta accidentale di un 16esimo campo rompe il test.
        Versamento base = versamentoRepository.findDetail(APP_COD, "PEND-A-001").orElseThrow();
        Versamento full = new Versamento();
        full.setCodVersamentoEnte("PEND-15");
        full.setImportoTotale(123.0);
        full.setStatoVersamento("NON_ESEGUITO");
        full.setDataCreazione(OffsetDateTime.now().minusHours(12));
        full.setDataOraUltimoAggiornamento(OffsetDateTime.now().minusHours(12));
        full.setDataScadenza(OffsetDateTime.now().plusDays(10));
        full.setDataValidita(OffsetDateTime.parse("2026-04-01T08:00:00Z"));
        full.setDataUltimaModificaAca(OffsetDateTime.now().minusDays(1));
        full.setDataUltimaComunicazioneAca(OffsetDateTime.now().minusDays(2));
        full.setCausaleVersamento("Causale completa");
        full.setNumeroAvviso("009999999999999999");
        full.setIuvVersamento("9999999999999");
        full.setDebitoreIdentificativo("RSSMRA80A01H501U");
        full.setDebitoreAnagrafica("Mario Rossi");
        full.setSrcDebitoreIdentificativo("RSSMRA80A01H501U");
        full.setImportoPagato(0.0);
        full.setAnomalo(false);
        full.setAck(false);
        full.setTipo("DOVUTO");
        full.setDominio(base.getDominio());
        full.setApplicazione(base.getApplicazione());
        full.setTipoVersamento(base.getTipoVersamento());
        full.setTipoVersamentoDominio(base.getTipoVersamentoDominio());
        full.setUnitaOperativa(base.getUnitaOperativa());
        versamentoRepository.save(full);

        mvc.perform(get("/pendenze").param("idPendenza", "PEND-15")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].*", hasSize(15)))
                .andExpect(jsonPath("$.results[0].idA2A").exists())
                .andExpect(jsonPath("$.results[0].idPendenza").exists())
                .andExpect(jsonPath("$.results[0].stato").exists())
                .andExpect(jsonPath("$.results[0].dominio").exists())
                .andExpect(jsonPath("$.results[0].tipoPendenza").exists())
                .andExpect(jsonPath("$.results[0].unitaOperativa").exists())
                .andExpect(jsonPath("$.results[0].importo").exists())
                .andExpect(jsonPath("$.results[0].numeroAvviso").exists())
                .andExpect(jsonPath("$.results[0].iuvAvviso").exists())
                .andExpect(jsonPath("$.results[0].causale").exists())
                .andExpect(jsonPath("$.results[0].dataScadenza").exists())
                .andExpect(jsonPath("$.results[0].dataValidita").exists())
                .andExpect(jsonPath("$.results[0].dataUltimoAggiornamento").exists())
                .andExpect(jsonPath("$.results[0].dataUltimaModificaAca").exists())
                .andExpect(jsonPath("$.results[0].dataUltimaComunicazioneAca").exists());
    }

    @Test
    void unitaOperativaNullWhenAbsent() throws Exception {
        mvc.perform(get("/pendenze").param("idPendenza", "PEND-A-002")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].unitaOperativa", nullValue()));
    }

    @Test
    void nonEseguitoExpiredIsMappedToScaduta() throws Exception {
        mvc.perform(get("/pendenze").param("idPendenza", "PEND-SCADUTA")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].stato", is("SCADUTA")));
    }

    @Test
    void nonEseguitoNotYetExpiredIsMappedToNonPagata() throws Exception {
        mvc.perform(get("/pendenze").param("idPendenza", "PEND-FUTURA")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].stato", is("NON_PAGATA")));
    }

    // ---- Issue #66 scope B: idA2A, idTipoPendenza ----

    @Test
    void filterByIdA2AExact() throws Exception {
        Applicazione appB = new Applicazione();
        appB.setCodApplicazione("APP-B");
        applicazioneRepository.save(appB);

        Versamento v = versamentoRepository.findDetail(APP_COD, "PEND-A-002").orElseThrow();
        v.setApplicazione(appB);
        versamentoRepository.save(v);

        mvc.perform(get("/pendenze").param("idA2A", "APP-B").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idPendenza", contains("PEND-A-002")));
    }

    /** Acceptance criteria issue #66: combinazione idDominio+idA2A+stato. */
    @Test
    void combinazioneIdDominioIdA2AEStato() throws Exception {
        Applicazione appB = new Applicazione();
        appB.setCodApplicazione("APP-B");
        applicazioneRepository.save(appB);

        Versamento v = versamentoRepository.findDetail(APP_COD, "PEND-A-001").orElseThrow();
        v.setApplicazione(appB);
        versamentoRepository.save(v);

        mvc.perform(get("/pendenze").param("idDominio", "11111111111")
                        .param("idA2A", "APP-B").param("stato", "NON_PAGATA")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idPendenza", contains("PEND-A-001")));
    }

    @Test
    void filterByIdTipoPendenzaMultiploUnisceIRisultati() throws Exception {
        Dominio dom = dominioRepository.findByCodDominio("11111111111").orElseThrow();
        Applicazione app = applicazioneRepository.findByCodApplicazione(APP_COD).orElseThrow();
        TipoVersamento imu = creaTipoVersamento("IMU");
        TipoVersamentoDominio tvdImu = newTvd(dom, imu);
        salvaVersamentoTipo("PEND-IMU-001", dom, app, imu, tvdImu, "NON_ESEGUITO");

        // Solo IMU: deve isolare la pendenza IMU, escludendo le 5 pendenze TARI del dominio A.
        mvc.perform(get("/pendenze").param("idDominio", "11111111111").param("idTipoPendenza", "IMU")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idPendenza", contains("PEND-IMU-001")));

        // TARI,IMU: unione, tutte e 6 le pendenze del dominio A.
        mvc.perform(get("/pendenze").param("idDominio", "11111111111").param("idTipoPendenza", "TARI,IMU")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idPendenza",
                        containsInAnyOrder("PEND-A-001", "PEND-A-002", "PEND-A-003",
                                "PEND-SCADUTA", "PEND-FUTURA", "PEND-IMU-001")));
    }

    @Test
    void filterByIdTipoPendenzaConValoreInesistenteRestringeSenzaErrore() throws Exception {
        mvc.perform(get("/pendenze").param("idDominio", "11111111111").param("idTipoPendenza", "TARI,BOGUS")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idPendenza",
                        containsInAnyOrder("PEND-A-001", "PEND-A-002", "PEND-A-003",
                                "PEND-SCADUTA", "PEND-FUTURA")));
    }

    @Test
    void idTipoPendenzaVuotoOSoloSeparatoriReturns400() throws Exception {
        mvc.perform(get("/pendenze").param("idTipoPendenza", "").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("idTipoPendenza")));

        mvc.perform(get("/pendenze").param("idTipoPendenza", ",,").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("idTipoPendenza")));
    }

    @Test
    void idTipoPendenzaOltreLimiteMassimoReturns400() throws Exception {
        String troppi = java.util.stream.IntStream.rangeClosed(1, 51)
                .mapToObj(i -> "T" + i)
                .collect(java.util.stream.Collectors.joining(","));

        mvc.perform(get("/pendenze").param("idTipoPendenza", troppi).with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("idTipoPendenza")));
    }

    /** Acceptance criteria issue #66: idTipoPendenza multiplo (OR) in AND con stato. */
    @Test
    void combinazioneIdTipoPendenzaMultiploEStato() throws Exception {
        Dominio dom = dominioRepository.findByCodDominio("11111111111").orElseThrow();
        Applicazione app = applicazioneRepository.findByCodApplicazione(APP_COD).orElseThrow();
        TipoVersamento imu = creaTipoVersamento("IMU");
        TipoVersamentoDominio tvdImu = newTvd(dom, imu);
        salvaVersamentoTipo("PEND-IMU-NON-PAGATA", dom, app, imu, tvdImu, "NON_ESEGUITO");
        salvaVersamentoTipo("PEND-IMU-PAGATA", dom, app, imu, tvdImu, "ESEGUITO");

        mvc.perform(get("/pendenze").param("idDominio", "11111111111")
                        .param("idTipoPendenza", "TARI,IMU").param("stato", "NON_PAGATA")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idPendenza",
                        containsInAnyOrder("PEND-A-001", "PEND-A-003", "PEND-FUTURA", "PEND-IMU-NON-PAGATA")));
    }

    private TipoVersamento creaTipoVersamento(String cod) {
        TipoVersamento tv = new TipoVersamento();
        tv.setCodTipoVersamento(cod);
        tv.setDescrizione(cod);
        return tipoVersamentoRepository.save(tv);
    }

    private Versamento salvaVersamentoTipo(String idPendenza, Dominio dom, Applicazione app,
                                           TipoVersamento tv, TipoVersamentoDominio tvd, String statoV1) {
        Versamento v = new Versamento();
        v.setCodVersamentoEnte(idPendenza);
        v.setImportoTotale(10.0);
        v.setStatoVersamento(statoV1);
        v.setDataCreazione(OffsetDateTime.now());
        v.setDataOraUltimoAggiornamento(OffsetDateTime.now());
        v.setDebitoreIdentificativo("RSSMRA80A01H501U");
        v.setDebitoreAnagrafica("Mario Rossi");
        v.setSrcDebitoreIdentificativo("RSSMRA80A01H501U");
        v.setImportoPagato(0.0);
        v.setAnomalo(false);
        v.setAck(false);
        v.setTipo("DOVUTO");
        v.setDominio(dom);
        v.setApplicazione(app);
        v.setTipoVersamento(tv);
        v.setTipoVersamentoDominio(tvd);
        return versamentoRepository.save(v);
    }
}
