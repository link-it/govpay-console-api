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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.function.Consumer;


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
import it.govpay.console.repository.ConsoleApplicazioneRepository;
import it.govpay.console.repository.ConsoleDominioRepository;
import it.govpay.console.repository.GpAuditRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.TipoVersamentoDominioRepository;
import it.govpay.console.repository.TipoVersamentoRepository;
import it.govpay.console.repository.UnitaOperativaRepository;
import it.govpay.console.repository.UtenzaDominioRepository;
import it.govpay.console.repository.UtenzaRepository;
import it.govpay.common.auth.GovpayPasswordEncoder;
import it.govpay.pendenze.entity.Pendenza;
import it.govpay.pendenze.model.StatoPendenza;
import it.govpay.console.pendenza.PendenzaV3Fixtures.Dati;
import jakarta.persistence.EntityManager;

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
    private ConsoleDominioRepository dominioRepository;
    @Autowired
    private ConsoleApplicazioneRepository applicazioneRepository;
    @Autowired
    private TipoVersamentoRepository tipoVersamentoRepository;
    @Autowired
    private UnitaOperativaRepository unitaOperativaRepository;
    @Autowired
    private EntityManager em;
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

        // Pendenza non eseguita ma con data scadenza nel PASSATO -> stato derivato a SCADUTO.
        creaPendenza("PEND-SCADUTA", domA, app, tv, tvdA, null, "RSSMRA80A01H501U", null, 42.0,
                "NON_ESEGUITO", 10, OffsetDateTime.now().minusDays(7), null);

        // Pendenza non eseguita con data scadenza nel FUTURO -> stato = NON_ESEGUITO.
        creaPendenza("PEND-FUTURA", domA, app, tv, tvdA, null, "RSSMRA80A01H501U", null, 15.0,
                "NON_ESEGUITO", 11, OffsetDateTime.now().plusDays(7), null);
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
                             String numAvviso, double importo, String stato, int orderOffsetHours) {
        creaPendenza(idPendenza, dom, app, tv, tvd, uo, debitore, numAvviso, importo, stato, orderOffsetHours,
                null, p -> p.setDataValidita(OffsetDateTime.parse("2026-03-15T10:30:00Z")));
    }

    /** Crea una pendenza del modello v3 (con posizione, debitore e opzione), con data di creazione di {@code oreFa} ore fa. */
    private Pendenza creaPendenza(String idPendenza, Dominio dom, Applicazione app, TipoVersamento tv,
                                  TipoVersamentoDominio tvd, UnitaOperativa uo, String debitore,
                                  String numAvviso, double importo, String stato, int oreFa,
                                  OffsetDateTime dataScadenza, Consumer<Pendenza> personalizza) {
        Pendenza p = PendenzaV3Fixtures.pendenza(em, new PendenzaV3Fixtures.Dati(app.getId(), dom.getId(),
                tv.getId(), tvd.getId(), uo != null ? uo.getId() : null, idPendenza, numAvviso, debitore,
                importo, stato(stato), oreFa).conScadenza(dataScadenza));
        if (personalizza != null) {
            personalizza.accept(p);
        }
        return p;
    }

    private static StatoPendenza stato(String valore) {
        return switch (valore) {
            case "ESEGUITA" -> StatoPendenza.ESEGUITO;
            case "ANNULLATA" -> StatoPendenza.ANNULLATO;
            default -> StatoPendenza.valueOf(valore);
        };
    }

    private Pendenza pendenza(String idPendenza) {
        return em.createQuery("select p from Pendenza p where p.idPendenza = :id", Pendenza.class)
                .setParameter("id", idPendenza).getSingleResult();
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
            creaPendenza("PEND-ORDINE-" + i, dom, app, tv, tvd, null, "RSSMRA80A01H501U", null, 10.0,
                    "NON_ESEGUITO", i * 24, null, p -> p.setDataUltimoAggiornamento(stessoAggiornamento));
        }

        mvc.perform(get("/pendenze").param("idPendenza", "PEND-ORDINE").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idPendenza",
                        contains("PEND-ORDINE-1", "PEND-ORDINE-2", "PEND-ORDINE-3")));
    }

    @Test
    void filterByStatoNonEseguitoEsludeScaduto() throws Exception {
        mvc.perform(get("/pendenze").param("idDominio", "11111111111").param("stato", "NON_ESEGUITO")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idPendenza",
                        containsInAnyOrder("PEND-A-001", "PEND-A-003", "PEND-FUTURA")));
    }

    @Test
    void filterByStatoScadutoSoloNonEseguiteScadute() throws Exception {
        mvc.perform(get("/pendenze").param("stato", "SCADUTO").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idPendenza", contains("PEND-SCADUTA")));
    }

    @Test
    void filterByStatoEseguito() throws Exception {
        mvc.perform(get("/pendenze").param("stato", "ESEGUITO").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idPendenza", contains("PEND-A-002")));
    }

    /** Gli stati ESEGUITO_ALTRO_CANALE e ESEGUITO_SENZA_RPT sono stati a se', non varianti di ESEGUITO. */
    @Test
    void filterByStatoEseguitoAltroCanaleESenzaRptSonoDistinti() throws Exception {
        Pendenza altroCanale = pendenza("PEND-A-001");
        altroCanale.setStato(stato("ESEGUITO_ALTRO_CANALE"));

        Pendenza senzaRpt = pendenza("PEND-A-003");
        senzaRpt.setStato(stato("ESEGUITO_SENZA_RPT"));

        mvc.perform(get("/pendenze").param("idDominio", "11111111111").param("stato", "ESEGUITO")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idPendenza", contains("PEND-A-002")));
        mvc.perform(get("/pendenze").param("idDominio", "11111111111").param("stato", "ESEGUITO_ALTRO_CANALE")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idPendenza", contains("PEND-A-001")));
        mvc.perform(get("/pendenze").param("idDominio", "11111111111").param("stato", "ESEGUITO_SENZA_RPT")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idPendenza", contains("PEND-A-003")));
    }

    /** NON_PUBBLICATA: pubblicazione futura sulla posizione. Prevale su NON_ESEGUITO e su SCADUTO. */
    @Test
    void filterByStatoNonPubblicata() throws Exception {
        pendenza("PEND-A-001").getOpzionePagamento().getPosizioneDebitoria()
                .setDataPubblicazione(LocalDate.now().plusDays(3));
        pendenza("PEND-SCADUTA").getOpzionePagamento().getPosizioneDebitoria()
                .setDataPubblicazione(LocalDate.now().plusDays(3));

        mvc.perform(get("/pendenze").param("stato", "NON_PUBBLICATA").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idPendenza", containsInAnyOrder("PEND-A-001", "PEND-SCADUTA")))
                .andExpect(jsonPath("$.results[0].stato", is("NON_PUBBLICATA")));
        mvc.perform(get("/pendenze").param("idDominio", "11111111111").param("stato", "NON_ESEGUITO")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idPendenza", containsInAnyOrder("PEND-A-003", "PEND-FUTURA")));
        mvc.perform(get("/pendenze").param("stato", "SCADUTO").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", empty()));
    }

    /** Una pubblicazione nel giorno corrente vale come pubblicata. */
    @Test
    void pubblicazioneOggiNonEnonPubblicata() throws Exception {
        pendenza("PEND-A-001").getOpzionePagamento().getPosizioneDebitoria().setDataPubblicazione(LocalDate.now());

        mvc.perform(get("/pendenze").param("stato", "NON_PUBBLICATA").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", empty()));
    }

    /** Acceptance criteria issue #66: valore V1 non valido per 'stato' elenca i valori ammessi. */
    @Test
    void statoValoreInvalidoElencaValoriAmmessi() throws Exception {
        mvc.perform(get("/pendenze").param("stato", "BOGUS").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("Valori ammessi")))
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("NON_ESEGUITO")))
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("ESEGUITO")));
    }

    @Test
    void filterByStatoAnnullato() throws Exception {
        mvc.perform(get("/pendenze").param("stato", "ANNULLATO").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idPendenza", contains("PEND-B-002")));
    }

    @Test
    void filterByStatoParzialmenteEseguito() throws Exception {
        Pendenza v = pendenza("PEND-A-001");
        v.setStato(stato("PARZIALMENTE_ESEGUITO"));

        mvc.perform(get("/pendenze").param("stato", "PARZIALMENTE_ESEGUITO").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idPendenza", contains("PEND-A-001")));
    }

    @Test
    void filterByStatoAnomalo() throws Exception {
        Pendenza v = pendenza("PEND-A-001");
        v.setStato(StatoPendenza.ANOMALO);

        mvc.perform(get("/pendenze").param("stato", "ANOMALO").with(httpBasic(PRINCIPAL, PASSWORD)))
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
        Pendenza withIuv = pendenza("PEND-A-001");
        withIuv.setIuv("IUV-TEST-001");

        mvc.perform(get("/pendenze").param("iuv", "IUV-TEST-001").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idPendenza", contains("PEND-A-001")));
    }

    @Test
    void filterByDirezione() throws Exception {
        Pendenza v = pendenza("PEND-A-001");
        v.setDirezione("DIR-1");

        mvc.perform(get("/pendenze").param("direzione", "DIR-1").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idPendenza", contains("PEND-A-001")));
    }

    @Test
    void filterByDivisione() throws Exception {
        Pendenza v = pendenza("PEND-A-001");
        v.setDivisione("DIV-1");

        mvc.perform(get("/pendenze").param("divisione", "DIV-1").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idPendenza", contains("PEND-A-001")));
    }

    /** direzione/divisione: cardinalita' allineata a /ricevute (issue #68), semantica OR come idTipoPendenza. */
    @Test
    void filterByDirezioneMultiploUnisceIRisultati() throws Exception {
        Pendenza v1 = pendenza("PEND-A-001");
        v1.setDirezione("DIR-1");
        Pendenza v2 = pendenza("PEND-A-002");
        v2.setDirezione("DIR-2");

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
        Pendenza v1 = pendenza("PEND-A-001");
        v1.setDivisione("DIV-1");
        Pendenza v2 = pendenza("PEND-A-002");
        v2.setDivisione("DIV-2");

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

        // Dentro il range richiesto, NON_ESEGUITO: deve comparire.
        salvaVersamentoCombinazione("PEND-COMBO-DENTRO", dom, app, tv, tvd, "NON_ESEGUITO", 1, null);
        // Dentro il range richiesto, ma PAGATA: non deve comparire (stato non combacia).
        salvaVersamentoCombinazione("PEND-COMBO-PAGATA", dom, app, tv, tvd, "ESEGUITO", 1, null);
        // Fuori dal range richiesto (10 giorni fa), NON_ESEGUITO: non deve comparire (data non combacia).
        salvaVersamentoCombinazione("PEND-COMBO-FUORI-RANGE", dom, app, tv, tvd, "NON_ESEGUITO", 10, null);

        java.time.LocalDate oggi = java.time.LocalDate.now();
        mvc.perform(get("/pendenze").param("idPendenza", "PEND-COMBO")
                        .param("stato", "NON_ESEGUITO")
                        .param("dataDa", oggi.minusDays(2).toString()).param("dataA", oggi.toString())
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idPendenza", contains("PEND-COMBO-DENTRO")));
    }

    private void salvaVersamentoCombinazione(String idPendenza, Dominio dom, Applicazione app, TipoVersamento tv,
                                             TipoVersamentoDominio tvd, String stato, int giorniFa,
                                             OffsetDateTime dataScadenza) {
        creaPendenza(idPendenza, dom, app, tv, tvd, null, "RSSMRA80A01H501U", null, 10.0, stato,
                giorniFa * 24, dataScadenza, null);
    }

    /** ACL prevale anche sui nuovi filtri: dominio non visibile -> lista vuota, non 403. */
    @Test
    void aclPrevaleSuNuoviFiltri() throws Exception {
        mvc.perform(get("/pendenze").param("idDominio", "33333333333").param("stato", "NON_ESEGUITO")
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
                .andExpect(jsonPath("$.results[0].stato", is("NON_ESEGUITO")))
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
    void pendenzaSummaryHasExactlyTwentyOneFields() throws Exception {
        // Pendenza dedicata con TUTTI i 21 campi del summary popolati (creata nel
        // test per non alterare i conteggi degli altri): la proiezione deve esporne
        // esattamente 21, cosi' l'aggiunta accidentale di un 22esimo campo rompe il test.
        Pendenza base = pendenza("PEND-A-001");
        UnitaOperativa uo = unitaOperativaRepository.findById(
                base.getOpzionePagamento().getPosizioneDebitoria().getIdUnitaOperativa()).orElseThrow();
        creaPendenza("PEND-15", dominioRepository.findById(base.getIdDominio()).orElseThrow(),
                applicazioneRepository.findById(base.getIdApplicazione()).orElseThrow(),
                tipoVersamentoRepository.findById(base.getIdTipoVersamento()).orElseThrow(),
                tipoVersamentoDominioRepository.findById(base.getIdTipoPendenza()).orElseThrow(),
                uo, "RSSMRA80A01H501U", "009999999999999999", 123.0, "NON_ESEGUITO", 12,
                OffsetDateTime.now().plusDays(10), full -> {
                    full.setDataValidita(OffsetDateTime.parse("2026-04-01T08:00:00Z"));
                    full.setDataUltimaModificaAca(OffsetDateTime.now().minusDays(1));
                    full.setDataUltimaComunicazioneAca(OffsetDateTime.now().minusDays(2));
                    full.setCausaleVersamento("Causale completa");
                    full.setIuv("9999999999999");
                });

        mvc.perform(get("/pendenze").param("idPendenza", "PEND-15")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].*", hasSize(21)))
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
                .andExpect(jsonPath("$.results[0].dataUltimaComunicazioneAca").exists())
                .andExpect(jsonPath("$.results[0].debitore").exists())
                .andExpect(jsonPath("$.results[0].opzionePagamento").exists())
                .andExpect(jsonPath("$.results[0].posizioneDebitoria").exists())
                .andExpect(jsonPath("$.results[0].dataCreazione").exists())
                .andExpect(jsonPath("$.results[0].direzione", is("Dir-X")))
                .andExpect(jsonPath("$.results[0].divisione", is("Div-Y")));
    }

    /** Ogni riga porta debitore (solo tipo e identificativo), opzione e posizione della pendenza. */
    @Test
    void summaryRiportaDebitoreOpzioneEPosizione() throws Exception {
        mvc.perform(get("/pendenze").param("idPendenza", "PEND-A-001").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].debitore.tipo", is("F")))
                .andExpect(jsonPath("$.results[0].debitore.identificativo", is("RSSMRA80A01H501U")))
                .andExpect(jsonPath("$.results[0].debitore.anagrafica").doesNotExist())
                .andExpect(jsonPath("$.results[0].opzionePagamento.tipologia", is("SOLUZIONE_UNICA")))
                .andExpect(jsonPath("$.results[0].opzionePagamento.stato", is("DISPONIBILE")))
                .andExpect(jsonPath("$.results[0].opzionePagamento.numeroRata").doesNotExist())
                .andExpect(jsonPath("$.results[0].posizioneDebitoria.idPosizioneDebitoria",
                        is(APP_COD + "11111111111" + "DOC-PEND-A-001")));
    }

    /** Di un piano rateale la lista indica la rata della pendenza. */
    @Test
    void summaryDiUnPianoRatealeRiportaNumeroRata() throws Exception {
        PendenzaV3Fixtures.pendenza(em, datiPendenza("PEND-RATA", "RSSMRA80A01H501U", StatoPendenza.NON_ESEGUITO)
                .conTipologia(it.govpay.pendenze.model.TipologiaOpzionePagamento.PIANO_RATEALE));

        mvc.perform(get("/pendenze").param("idPendenza", "PEND-RATA").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].opzionePagamento.tipologia", is("PIANO_RATEALE")))
                .andExpect(jsonPath("$.results[0].opzionePagamento.numeroRata", is(1)));
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
                .andExpect(jsonPath("$.results[0].stato", is("SCADUTO")));
    }

    @Test
    void nonEseguitoNotYetExpiredIsMappedToNonPagata() throws Exception {
        mvc.perform(get("/pendenze").param("idPendenza", "PEND-FUTURA")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].stato", is("NON_ESEGUITO")));
    }

    // ---- Issue #66 scope B: idA2A, idTipoPendenza ----

    @Test
    void filterByIdA2AExact() throws Exception {
        Applicazione appB = new Applicazione();
        appB.setCodApplicazione("APP-B");
        applicazioneRepository.save(appB);

        Pendenza v = pendenza("PEND-A-002");
        v.setIdApplicazione(appB.getId());

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

        Pendenza v = pendenza("PEND-A-001");
        v.setIdApplicazione(appB.getId());

        mvc.perform(get("/pendenze").param("idDominio", "11111111111")
                        .param("idA2A", "APP-B").param("stato", "NON_ESEGUITO")
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
                        .param("idTipoPendenza", "TARI,IMU").param("stato", "NON_ESEGUITO")
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

    private Pendenza salvaVersamentoTipo(String idPendenza, Dominio dom, Applicazione app,
                                         TipoVersamento tv, TipoVersamentoDominio tvd, String stato) {
        return creaPendenza(idPendenza, dom, app, tv, tvd, null, "RSSMRA80A01H501U", null, 10.0, stato, 0,
                null, null);
    }

    // ---- Modello v3: debitore dell'opzione, opzione obbligatoria, numero di query ----

    /** Il filtro per debitore trova le pendenze di quel debitore, non quelle degli altri debitori della posizione. */
    @Test
    void filterByIdentificativoDebitoreDistingueIDebitoriDellaPosizione() throws Exception {
        Dati dati = datiPendenza("PEND-SECONDO", "01234567890", StatoPendenza.NON_ESEGUITO);
        PendenzaV3Fixtures.pendenzaDelSecondoDebitore(em, dati, "VRDLGI90B02H501T", false);

        mvc.perform(get("/pendenze").param("identificativoDebitore", "01234567890")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idPendenza", contains("PEND-SECONDO")));
        // Il primo debitore della posizione non e' il debitore di questa pendenza.
        mvc.perform(get("/pendenze").param("identificativoDebitore", "VRDLGI90B02H501T")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idPendenza", contains("PEND-A-002")));
    }

    @Test
    void filterByIdentificativoDebitoreSenzaDistinzioneMaiuscole() throws Exception {
        mvc.perform(get("/pendenze").param("identificativoDebitore", "vrdlgi90b02h501t")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idPendenza", contains("PEND-A-002")));
    }

    /** Un debitore rimosso ha le pendenze annullate: restano in lista, filtrabili come ANNULLATO. */
    @Test
    void pendenzeDiUnDebitoreRimossoRestanoInListaComeAnnullate() throws Exception {
        Dati dati = datiPendenza("PEND-RIMOSSO", "01234567890", StatoPendenza.ANNULLATO);
        PendenzaV3Fixtures.pendenzaDelSecondoDebitore(em, dati, "VRDLGI90B02H501T", true);

        mvc.perform(get("/pendenze").param("idPendenza", "PEND-RIMOSSO").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idPendenza", contains("PEND-RIMOSSO")))
                .andExpect(jsonPath("$.results[0].stato", is("ANNULLATO")));
    }

    /** Come il dettaglio, la lista non ammette pendenze senza opzione di pagamento: ogni riga si puo' aprire. */
    @Test
    void pendenzaSenzaOpzioneNonCompareInLista() throws Exception {
        PendenzaV3Fixtures.pendenzaSenzaOpzione(em, datiPendenza("PEND-SENZA-OPZIONE", "RSSMRA80A01H501U",
                StatoPendenza.NON_ESEGUITO));

        mvc.perform(get("/pendenze").param("idPendenza", "PEND-SENZA-OPZIONE")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", empty()));
        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-SENZA-OPZIONE").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    private Dati datiPendenza(String idPendenza, String debitore, StatoPendenza stato) {
        Dominio dom = dominioRepository.findByCodDominio("11111111111").orElseThrow();
        Applicazione app = applicazioneRepository.findByCodApplicazione(APP_COD).orElseThrow();
        TipoVersamento tv = tipoVersamentoRepository.findByCodTipoVersamento("TARI").orElseThrow();
        TipoVersamentoDominio tvd = tipoVersamentoDominioRepository
                .findByDominio_IdAndTipoVersamento_CodTipoVersamento(dom.getId(), "TARI").orElseThrow();
        return new Dati(app.getId(), dom.getId(), tv.getId(), tvd.getId(), null, idPendenza, null, debitore,
                10.0, stato, 0);
    }
}
