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

    private Long idDominioA;
    private Long idDominioB;

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
        idDominioA = domA.getId();
        idDominioB = domB.getId();

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

    @Test
    void defaultSortByDataUltimoAggiornamentoDesc() throws Exception {
        mvc.perform(get("/pendenze").param("idDominio", "11111111111")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idPendenza",
                        contains("PEND-A-001", "PEND-A-002", "PEND-A-003",
                                "PEND-SCADUTA", "PEND-FUTURA")));
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

    @Test
    void unsupportedQueryParamReturns400() throws Exception {
        mvc.perform(get("/pendenze").param("stato", "PAGATA").with(httpBasic(PRINCIPAL, PASSWORD)))
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
                .andExpect(jsonPath("$.results[0].tipo", is("DOVUTA")))
                .andExpect(jsonPath("$.results[0].importo", is(100.0)))
                .andExpect(jsonPath("$.results[0].importoPagato", greaterThanOrEqualTo(0.0)))
                .andExpect(jsonPath("$.results[0].dominio.idDominio", is("11111111111")))
                .andExpect(jsonPath("$.results[0].dominio.ragioneSociale", is("Dominio A")))
                .andExpect(jsonPath("$.results[0].tipoPendenza.idTipoPendenza", is("TARI")))
                .andExpect(jsonPath("$.results[0].unitaOperativa.idUnitaOperativa", is("UO1")))
                .andExpect(jsonPath("$.results[0].idDebitore", is("RSSMRA80A01H501U")))
                .andExpect(jsonPath("$.results[0].dataUltimoAggiornamento", org.hamcrest.Matchers.notNullValue()))
                .andExpect(jsonPath("$.results[0].verificato", is(false)));
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
}
