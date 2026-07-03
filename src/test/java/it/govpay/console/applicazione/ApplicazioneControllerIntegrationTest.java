package it.govpay.console.applicazione;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.common.auth.GovpayPasswordEncoder;
import it.govpay.console.entity.Acl;
import it.govpay.console.entity.Applicazione;
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.TipoVersamento;
import it.govpay.console.entity.Utenza;
import it.govpay.console.entity.UtenzaTipoVersamento;
import it.govpay.console.repository.AclRepository;
import it.govpay.console.repository.ApplicazioneRepository;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.repository.GpAuditRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.TipoVersamentoRepository;
import it.govpay.console.repository.UtenzaRepository;
import it.govpay.console.repository.UtenzaTipoVersamentoRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApplicazioneControllerIntegrationTest {

    private static final String PRINCIPAL = "operatore1";
    private static final String PASSWORD = "secret";
    private static final MediaType JSON_PATCH = MediaType.valueOf("application/json-patch+json");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private GovpayPasswordEncoder encoder;
    @Autowired
    private UtenzaRepository utenzaRepository;
    @Autowired
    private OperatoreRepository operatoreRepository;
    @Autowired
    private ApplicazioneRepository applicazioneRepository;
    @Autowired
    private DominioRepository dominioRepository;
    @Autowired
    private TipoVersamentoRepository tipoVersamentoRepository;
    @Autowired
    private AclRepository aclRepository;
    @Autowired
    private UtenzaTipoVersamentoRepository utenzaTipoVersamentoRepository;
    @Autowired
    private GpAuditRepository gpAuditRepository;

    @BeforeEach
    void setup() {
        Utenza operatore = new Utenza();
        operatore.setPrincipal(PRINCIPAL);
        operatore.setPrincipalOriginale(PRINCIPAL);
        operatore.setAbilitato(true);
        operatore.setAutorizzazioneDominiStar(true);
        operatore.setAutorizzazioneTipiVersStar(true);
        operatore.setRuoli("OPERATORE");
        operatore.setPassword(encoder.encode(PASSWORD));
        utenzaRepository.save(operatore);

        Operatore op = new Operatore();
        op.setNome("Operatore Uno");
        op.setIdUtenza(operatore.getId());
        operatoreRepository.save(op);

        newDominio("12345678901", "Comune Alfa");
        newTipoVersamento("TARI", "Tassa Rifiuti");
        newRuoloCatalogo("OPERATORE");
        newRuoloCatalogo("AMMINISTRATORE");

        newApplicazione("APP-001", "p-app1", true);
        newApplicazione("APP-002", "p-app2", false);
        newApplicazione("APP-003", "p-app3", true);
    }

    // --- helpers di seed ---

    private Dominio newDominio(String cod, String ragione) {
        Dominio d = new Dominio();
        d.setCodDominio(cod);
        d.setRagioneSociale(ragione);
        d.setAuxDigit(0);
        return dominioRepository.save(d);
    }

    private void newRuoloCatalogo(String ruolo) {
        Acl acl = new Acl();
        acl.setRuolo(ruolo);
        acl.setServizio("Pendenze");
        acl.setDiritti("R");
        acl.setIdUtenza(null);
        aclRepository.save(acl);
    }

    private TipoVersamento newTipoVersamento(String cod, String descrizione) {
        TipoVersamento t = new TipoVersamento();
        t.setCodTipoVersamento(cod);
        t.setDescrizione(descrizione);
        return tipoVersamentoRepository.save(t);
    }

    private Utenza newApplicazione(String cod, String principal, boolean abilitato) {
        Utenza u = new Utenza();
        u.setPrincipal(principal);
        u.setPrincipalOriginale(principal);
        u.setAbilitato(abilitato);
        u.setAutorizzazioneDominiStar(false);
        u.setAutorizzazioneTipiVersStar(false);
        utenzaRepository.save(u);

        Applicazione app = new Applicazione();
        app.setCodApplicazione(cod);
        app.setUtenza(u);
        applicazioneRepository.save(app);
        return u;
    }

    // --- List ---

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/applicazioni"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void listDefaultIsSliceWithoutTotals() throws Exception {
        mvc.perform(get("/applicazioni").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(3)))
                .andExpect(jsonPath("$.pagination.hasNextPage", is(false)))
                .andExpect(jsonPath("$.pagination.totalResults").doesNotExist());
    }

    @Test
    void listTotalTrueIncludesCount() throws Exception {
        mvc.perform(get("/applicazioni").param("total", "true").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalResults", is(3)))
                .andExpect(jsonPath("$.pagination.totalPages", is(1)));
    }

    @Test
    void filterByIdA2APartial() throws Exception {
        mvc.perform(get("/applicazioni").param("idA2A", "001").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].idA2A", is("APP-001")));
    }

    @Test
    void filterByPrincipalPartial() throws Exception {
        mvc.perform(get("/applicazioni").param("principal", "app2").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].idA2A", is("APP-002")));
    }

    @Test
    void filterByAbilitato() throws Exception {
        mvc.perform(get("/applicazioni").param("abilitato", "false").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].idA2A", is("APP-002")));
    }

    @Test
    void defaultSortByIdA2AAsc() throws Exception {
        mvc.perform(get("/applicazioni").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idA2A", contains("APP-001", "APP-002", "APP-003")));
    }

    @Test
    void sortByPrincipalDesc() throws Exception {
        mvc.perform(get("/applicazioni").param("sort", "-principal").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idA2A", contains("APP-003", "APP-002", "APP-001")));
    }

    @Test
    void unknownSortFieldReturns400() throws Exception {
        mvc.perform(get("/applicazioni").param("sort", "-bogus").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("bogus")));
    }

    // --- Get detail ---

    @Test
    void getExistingReturnsDetailWithEtag() throws Exception {
        mvc.perform(get("/applicazioni/APP-001").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.idA2A", is("APP-001")))
                .andExpect(jsonPath("$.principal", is("p-app1")))
                .andExpect(jsonPath("$.abilitato", is(true)))
                .andExpect(jsonPath("$._links.connettoreIntegrazione.href",
                        is("/applicazioni/APP-001/connettore-integrazione")));
    }

    @Test
    void getUnknownReturns404() throws Exception {
        mvc.perform(get("/applicazioni/APP-999").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getRichApplicazioneMapsStarAndAutodeterminazioneAndAcl() throws Exception {
        Utenza u = newApplicazione("APP-RICH", "p-rich", true);
        u.setAutorizzazioneDominiStar(true);
        u.setRuoli("AMMINISTRATORE,OPERATORE");
        utenzaRepository.save(u);
        Applicazione app = applicazioneRepository.findByCodApplicazione("APP-RICH").orElseThrow();
        app.setTrusted(true);
        applicazioneRepository.save(app);
        UtenzaTipoVersamento utv = new UtenzaTipoVersamento();
        utv.setIdUtenza(u.getId());
        utv.setIdTipoVersamento(tipoVersamentoRepository.findByCodTipoVersamento("TARI").orElseThrow().getId());
        utenzaTipoVersamentoRepository.save(utv);
        Acl acl = new Acl();
        acl.setServizio("Pendenze");
        acl.setDiritti("R,W");
        acl.setIdUtenza(u.getId());
        aclRepository.save(acl);

        mvc.perform(get("/applicazioni/APP-RICH").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.domini", hasSize(1)))
                .andExpect(jsonPath("$.domini[0].idDominio", is("*")))
                .andExpect(jsonPath("$.tipiPendenza[*].idTipoPendenza",
                        containsInAnyOrder("TARI", "autodeterminazione")))
                .andExpect(jsonPath("$.ruoli[*].id", containsInAnyOrder("AMMINISTRATORE", "OPERATORE")))
                .andExpect(jsonPath("$.acl", hasSize(1)))
                .andExpect(jsonPath("$.acl[0].servizio", is("Pendenze")))
                .andExpect(jsonPath("$.acl[0].autorizzazioni", containsInAnyOrder("R", "W")));
    }

    // --- Create ---

    @Test
    void createReturns201WithLocationAndEtag() throws Exception {
        String body = """
                {"idA2A":"APP-NEW","principal":"p-new","abilitato":true,
                 "domini":[{"idDominio":"12345678901"}],
                 "tipiPendenza":[{"idTipoPendenza":"TARI"}],
                 "ruoli":[{"id":"OPERATORE"}],
                 "acl":[{"servizio":"Pendenze","autorizzazioni":["R","W"]}]}""";
        mvc.perform(post("/applicazioni").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/applicazioni/APP-NEW")))
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.idA2A", is("APP-NEW")))
                .andExpect(jsonPath("$.domini[0].idDominio", is("12345678901")))
                .andExpect(jsonPath("$.tipiPendenza[0].idTipoPendenza", is("TARI")))
                .andExpect(jsonPath("$.ruoli[0].id", is("OPERATORE")))
                .andExpect(jsonPath("$.acl[0].servizio", is("Pendenze")));
    }

    @Test
    void createWithStarSetsAllFlags() throws Exception {
        String body = """
                {"idA2A":"APP-STAR","principal":"p-star","abilitato":true,
                 "domini":[{"idDominio":"*"}],
                 "tipiPendenza":[{"idTipoPendenza":"*"},{"idTipoPendenza":"autodeterminazione"}]}""";
        mvc.perform(post("/applicazioni").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.domini[0].idDominio", is("*")))
                .andExpect(jsonPath("$.tipiPendenza[*].idTipoPendenza",
                        containsInAnyOrder("*", "autodeterminazione")));
    }

    @Test
    void createDuplicateIdA2AReturns409() throws Exception {
        String body = """
                {"idA2A":"APP-001","principal":"p-x","abilitato":true}""";
        mvc.perform(post("/applicazioni").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void createDuplicatePrincipalReturns409() throws Exception {
        String body = """
                {"idA2A":"APP-DUPP","principal":"p-app1","abilitato":true}""";
        mvc.perform(post("/applicazioni").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void createWithMissingPrincipalReturns400() throws Exception {
        String body = """
                {"idA2A":"APP-NP","abilitato":true}""";
        mvc.perform(post("/applicazioni").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWithUnknownDominioRefReturns404() throws Exception {
        String body = """
                {"idA2A":"APP-BADD","principal":"p-badd","abilitato":true,
                 "domini":[{"idDominio":"99999999999"}]}""";
        mvc.perform(post("/applicazioni").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail", containsString("99999999999")));
    }

    @Test
    void createWithUnknownTipoPendenzaRefReturns404() throws Exception {
        String body = """
                {"idA2A":"APP-BADT","principal":"p-badt","abilitato":true,
                 "tipiPendenza":[{"idTipoPendenza":"BOGUS"}]}""";
        mvc.perform(post("/applicazioni").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail", containsString("BOGUS")));
    }

    @Test
    void createWithUnknownRuoloRefReturns404() throws Exception {
        String body = """
                {"idA2A":"APP-BADR","principal":"p-badr","abilitato":true,
                 "ruoli":[{"id":"BOGUS-ROLE"}]}""";
        mvc.perform(post("/applicazioni").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail", containsString("BOGUS-ROLE")));
    }

    @Test
    void createWithInvalidRegExpIuvReturns422() throws Exception {
        String body = """
                {"idA2A":"APP-RE","principal":"p-re","abilitato":true,
                 "codificaAvvisi":{"regExpIuv":"["}}""";
        mvc.perform(post("/applicazioni").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void createWithCodificaAvvisiRoundTrips() throws Exception {
        String body = """
                {"idA2A":"APP-CA","principal":"p-ca","abilitato":true,
                 "codificaAvvisi":{"codificaIuv":"34","regExpIuv":".*","generazioneIuvInterna":true}}""";
        mvc.perform(post("/applicazioni").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codificaAvvisi.codificaIuv", is("34")))
                .andExpect(jsonPath("$.codificaAvvisi.regExpIuv", is(".*")))
                .andExpect(jsonPath("$.codificaAvvisi.generazioneIuvInterna", is(true)));
    }

    @Test
    void createWritesAudit() throws Exception {
        long before = countAudit("APPLICAZIONE_CREATE");
        String body = """
                {"idA2A":"APP-AUD","principal":"p-aud","abilitato":true}""";
        mvc.perform(post("/applicazioni").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        org.assertj.core.api.Assertions.assertThat(countAudit("APPLICAZIONE_CREATE")).isEqualTo(before + 1);
    }

    // --- Replace (PUT) ---

    @Test
    void replaceWithCorrectIfMatchSucceeds() throws Exception {
        String etag = currentEtag("APP-001");
        String body = """
                {"principal":"p-app1b","abilitato":false,
                 "domini":[{"idDominio":"12345678901"}]}""";
        String newEtag = mvc.perform(put("/applicazioni/APP-001").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.principal", is("p-app1b")))
                .andExpect(jsonPath("$.abilitato", is(false)))
                .andExpect(jsonPath("$.domini[0].idDominio", is("12345678901")))
                .andReturn().getResponse().getHeader("ETag");
        org.assertj.core.api.Assertions.assertThat(newEtag).isNotEqualTo(etag);
    }

    @Test
    void replaceWithWrongIfMatchReturns412() throws Exception {
        String body = """
                {"principal":"p","abilitato":true}""";
        mvc.perform(put("/applicazioni/APP-001").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", "\"deadbeef\"")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void replaceWithoutIfMatchReturns428() throws Exception {
        String body = """
                {"principal":"p","abilitato":true}""";
        mvc.perform(put("/applicazioni/APP-001").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPreconditionRequired());
    }

    @Test
    void replaceUnknownReturns404() throws Exception {
        String body = """
                {"principal":"p","abilitato":true}""";
        mvc.perform(put("/applicazioni/APP-999").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", "*")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    // --- Patch (JSON Patch) ---

    @Test
    void patchReplaceAbilitatoSucceeds() throws Exception {
        String etag = currentEtag("APP-001");
        String patch = """
                [{"op":"replace","path":"/abilitato","value":false}]""";
        mvc.perform(patch("/applicazioni/APP-001").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(JSON_PATCH).content(patch))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abilitato", is(false)))
                .andExpect(jsonPath("$.principal", is("p-app1")));
    }

    @Test
    void patchChangingIdA2AReturns400() throws Exception {
        String etag = currentEtag("APP-001");
        String patch = """
                [{"op":"replace","path":"/idA2A","value":"APP-XXX"}]""";
        mvc.perform(patch("/applicazioni/APP-001").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(JSON_PATCH).content(patch))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("idA2A")));
    }

    @Test
    void patchWithWrongIfMatchReturns412() throws Exception {
        String patch = """
                [{"op":"replace","path":"/abilitato","value":false}]""";
        mvc.perform(patch("/applicazioni/APP-001").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", "\"deadbeef\"")
                        .contentType(JSON_PATCH).content(patch))
                .andExpect(status().isPreconditionFailed());
    }

    private String currentEtag(String cod) throws Exception {
        return mvc.perform(get("/applicazioni/" + cod).with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("ETag");
    }

    private long countAudit(String azione) {
        return gpAuditRepository.findAll().stream()
                .filter(a -> azione.equals(a.getTipoOggetto()))
                .count();
    }
}
