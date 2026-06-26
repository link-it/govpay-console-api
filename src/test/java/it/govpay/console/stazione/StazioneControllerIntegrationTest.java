package it.govpay.console.stazione;

import static org.hamcrest.Matchers.contains;
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
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.Intermediario;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Stazione;
import it.govpay.console.entity.Utenza;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.repository.GpAuditRepository;
import it.govpay.console.repository.IntermediarioRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.StazioneRepository;
import it.govpay.console.repository.UtenzaRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StazioneControllerIntegrationTest {

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
    private IntermediarioRepository intermediarioRepository;
    @Autowired
    private StazioneRepository stazioneRepository;
    @Autowired
    private DominioRepository dominioRepository;
    @Autowired
    private GpAuditRepository gpAuditRepository;

    @BeforeEach
    void setup() {
        Utenza utenza = new Utenza();
        utenza.setPrincipal(PRINCIPAL);
        utenza.setPrincipalOriginale(PRINCIPAL);
        utenza.setAbilitato(true);
        utenza.setAutorizzazioneDominiStar(true);
        utenza.setAutorizzazioneTipiVersStar(true);
        utenza.setRuoli("OPERATORE");
        utenza.setPassword(encoder.encode(PASSWORD));
        utenzaRepository.save(utenza);

        Operatore op = new Operatore();
        op.setNome("Operatore Uno");
        op.setIdUtenza(utenza.getId());
        operatoreRepository.save(op);

        Intermediario int1 = newIntermediario("INT-001");
        Intermediario int2 = newIntermediario("INT-002");

        Stazione s1 = newStazione("INT-001_01", 1, "V2", true, int1);
        newStazione("INT-001_02", 2, "V1", false, int1);
        newStazione("INT-001_03", 3, "V2", true, int1);
        newStazione("INT-002_01", 1, "V2", true, int2);

        Dominio dom = new Dominio();
        dom.setCodDominio("11111111111");
        dom.setRagioneSociale("Comune di Test");
        dom.setAuxDigit(0);
        dom.setStazione(s1);
        dominioRepository.save(dom);
    }

    private Intermediario newIntermediario(String cod) {
        Intermediario i = new Intermediario();
        i.setCodIntermediario(cod);
        i.setDenominazione("Denom " + cod);
        i.setPrincipal("p-" + cod);
        i.setPrincipalOriginale("p-" + cod);
        i.setCodConnettorePdd("CONN-" + cod);
        i.setAbilitato(true);
        return intermediarioRepository.save(i);
    }

    private Stazione newStazione(String cod, int appCode, String versione, boolean abilitato, Intermediario parent) {
        Stazione s = new Stazione();
        s.setCodStazione(cod);
        s.setApplicationCode(appCode);
        s.setVersione(versione);
        s.setAbilitato(abilitato);
        s.setPassword("");
        s.setIntermediario(parent);
        return stazioneRepository.save(s);
    }

    // --- List ---

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/intermediari/INT-001/stazioni"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void listDefaultIsSliceWithoutTotals() throws Exception {
        mvc.perform(get("/intermediari/INT-001/stazioni").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(3)))
                .andExpect(jsonPath("$.pagination.hasNextPage", is(false)))
                .andExpect(jsonPath("$.pagination.totalResults").doesNotExist());
    }

    @Test
    void listOnlyReturnsStazioniOfThatIntermediario() throws Exception {
        mvc.perform(get("/intermediari/INT-002/stazioni").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].idStazione", is("INT-002_01")));
    }

    @Test
    void listTotalTrueIncludesCount() throws Exception {
        mvc.perform(get("/intermediari/INT-001/stazioni").param("total", "true")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalResults", is(3)))
                .andExpect(jsonPath("$.pagination.totalPages", is(1)));
    }

    @Test
    void listSliceSignalsHasNextPage() throws Exception {
        mvc.perform(get("/intermediari/INT-001/stazioni").param("limit", "2")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(2)))
                .andExpect(jsonPath("$.pagination.hasNextPage", is(true)));
    }

    @Test
    void filterByCodStazionePartial() throws Exception {
        mvc.perform(get("/intermediari/INT-001/stazioni").param("codStazione", "_02")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].idStazione", is("INT-001_02")));
    }

    @Test
    void filterByAbilitato() throws Exception {
        mvc.perform(get("/intermediari/INT-001/stazioni").param("abilitato", "false")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].idStazione", is("INT-001_02")));
    }

    @Test
    void defaultSortByCodStazioneAsc() throws Exception {
        mvc.perform(get("/intermediari/INT-001/stazioni").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idStazione",
                        contains("INT-001_01", "INT-001_02", "INT-001_03")));
    }

    @Test
    void customSortDesc() throws Exception {
        mvc.perform(get("/intermediari/INT-001/stazioni").param("sort", "-codStazione")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idStazione",
                        contains("INT-001_03", "INT-001_02", "INT-001_01")));
    }

    @Test
    void unknownSortFieldReturns400() throws Exception {
        mvc.perform(get("/intermediari/INT-001/stazioni").param("sort", "-bogus")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("bogus")));
    }

    @Test
    void listUnknownIntermediarioReturns404() throws Exception {
        mvc.perform(get("/intermediari/INT-NOPE/stazioni").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType("application/problem+json"));
    }

    // --- Get detail ---

    @Test
    void getExistingReturnsDetailWithEtagAndDomini() throws Exception {
        mvc.perform(get("/intermediari/INT-001/stazioni/INT-001_01").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.idStazione", is("INT-001_01")))
                .andExpect(jsonPath("$.versione", is("V2")))
                .andExpect(jsonPath("$.abilitato", is(true)))
                .andExpect(jsonPath("$.domini", hasSize(1)))
                .andExpect(jsonPath("$.domini[0].idDominio", is("11111111111")))
                .andExpect(jsonPath("$.domini[0].ragioneSociale", is("Comune di Test")))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void getUnknownReturns404() throws Exception {
        mvc.perform(get("/intermediari/INT-001/stazioni/INT-001_99").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getStazioneUnderWrongParentReturns404() throws Exception {
        mvc.perform(get("/intermediari/INT-002/stazioni/INT-001_01").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    // --- Create ---

    @Test
    void createReturns201WithLocationAndEtag() throws Exception {
        String body = """
                {"idStazione":"INT-001_07","versione":"V2","abilitato":true}""";
        mvc.perform(post("/intermediari/INT-001/stazioni").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/stazioni/INT-001_07")))
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.idStazione", is("INT-001_07")))
                .andExpect(jsonPath("$.versione", is("V2")));
    }

    @Test
    void createDuplicateReturns409() throws Exception {
        String body = """
                {"idStazione":"INT-001_01","versione":"V2","abilitato":true}""";
        mvc.perform(post("/intermediari/INT-001/stazioni").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void createWithPrefixMismatchReturns422() throws Exception {
        String body = """
                {"idStazione":"INT-002_05","versione":"V2","abilitato":true}""";
        mvc.perform(post("/intermediari/INT-001/stazioni").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void createWithApplicationCodeOutOfRangeReturns422() throws Exception {
        String body = """
                {"idStazione":"INT-001_00","versione":"V2","abilitato":true}""";
        mvc.perform(post("/intermediari/INT-001/stazioni").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void createWithMissingFieldReturns400() throws Exception {
        String body = """
                {"idStazione":"INT-001_08","abilitato":true}""";
        mvc.perform(post("/intermediari/INT-001/stazioni").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createUnderUnknownIntermediarioReturns404() throws Exception {
        String body = """
                {"idStazione":"INT-NOPE_01","versione":"V2","abilitato":true}""";
        mvc.perform(post("/intermediari/INT-NOPE/stazioni").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void createWritesAudit() throws Exception {
        long before = countAudit("STAZIONE_CREATE");
        String body = """
                {"idStazione":"INT-001_09","versione":"V1","abilitato":true}""";
        mvc.perform(post("/intermediari/INT-001/stazioni").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        org.assertj.core.api.Assertions.assertThat(countAudit("STAZIONE_CREATE")).isEqualTo(before + 1);
    }

    // --- Replace ---

    @Test
    void replaceWithCorrectIfMatchSucceeds() throws Exception {
        String etag = currentEtag("INT-001", "INT-001_01");
        String body = """
                {"versione":"V1","abilitato":false}""";
        String newEtag = mvc.perform(put("/intermediari/INT-001/stazioni/INT-001_01")
                        .with(httpBasic(PRINCIPAL, PASSWORD)).header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versione", is("V1")))
                .andExpect(jsonPath("$.abilitato", is(false)))
                .andReturn().getResponse().getHeader("ETag");
        org.assertj.core.api.Assertions.assertThat(newEtag).isNotEqualTo(etag);
    }

    @Test
    void replaceWithWrongIfMatchReturns412() throws Exception {
        String body = """
                {"versione":"V1","abilitato":false}""";
        mvc.perform(put("/intermediari/INT-001/stazioni/INT-001_01").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", "\"deadbeef\"")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void replaceWithoutIfMatchReturns428() throws Exception {
        String body = """
                {"versione":"V1","abilitato":false}""";
        mvc.perform(put("/intermediari/INT-001/stazioni/INT-001_01").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPreconditionRequired());
    }

    @Test
    void replaceUnknownReturns404() throws Exception {
        String body = """
                {"versione":"V1","abilitato":false}""";
        mvc.perform(put("/intermediari/INT-001/stazioni/INT-001_99").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", "*")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void replaceWritesAudit() throws Exception {
        long before = countAudit("STAZIONE_MODIFICA");
        String etag = currentEtag("INT-001", "INT-001_01");
        String body = """
                {"versione":"V1","abilitato":true}""";
        mvc.perform(put("/intermediari/INT-001/stazioni/INT-001_01").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(countAudit("STAZIONE_MODIFICA")).isEqualTo(before + 1);
    }

    // --- Patch ---

    @Test
    void patchReplaceVersioneSucceeds() throws Exception {
        String etag = currentEtag("INT-001", "INT-001_01");
        String patchDoc = """
                [{"op":"replace","path":"/versione","value":"V1"}]""";
        mvc.perform(patch("/intermediari/INT-001/stazioni/INT-001_01").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(JSON_PATCH).content(patchDoc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versione", is("V1")))
                .andExpect(jsonPath("$.abilitato", is(true)));
    }

    @Test
    void patchChangingIdStazioneReturns400() throws Exception {
        String etag = currentEtag("INT-001", "INT-001_01");
        String patchDoc = """
                [{"op":"replace","path":"/idStazione","value":"INT-001_77"}]""";
        mvc.perform(patch("/intermediari/INT-001/stazioni/INT-001_01").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(JSON_PATCH).content(patchDoc))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("idStazione")));
    }

    @Test
    void patchModifyingDominiReturns400() throws Exception {
        String etag = currentEtag("INT-001", "INT-001_01");
        String patchDoc = """
                [{"op":"replace","path":"/domini","value":[]}]""";
        mvc.perform(patch("/intermediari/INT-001/stazioni/INT-001_01").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(JSON_PATCH).content(patchDoc))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("domini")));
    }

    @Test
    void patchWithWrongIfMatchReturns412() throws Exception {
        String patchDoc = """
                [{"op":"replace","path":"/versione","value":"V1"}]""";
        mvc.perform(patch("/intermediari/INT-001/stazioni/INT-001_01").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", "\"deadbeef\"")
                        .contentType(JSON_PATCH).content(patchDoc))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void patchUnsupportedPointerReturns400() throws Exception {
        String etag = currentEtag("INT-001", "INT-001_01");
        String patchDoc = """
                [{"op":"replace","path":"/nested/x","value":"V1"}]""";
        mvc.perform(patch("/intermediari/INT-001/stazioni/INT-001_01").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(JSON_PATCH).content(patchDoc))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createStazioneForIntermediarioWithUnderscoreInId() throws Exception {
        newIntermediario("INT_A");
        String body = """
                {"idStazione":"INT_A_01","versione":"V2","abilitato":true}""";
        mvc.perform(post("/intermediari/INT_A/stazioni").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idStazione", is("INT_A_01")));
    }

    @Test
    void etagReflectsDominiChanges() throws Exception {
        String etagBefore = currentEtag("INT-001", "INT-001_01");

        Stazione s1 = stazioneRepository.findByCodStazione("INT-001_01").orElseThrow();
        Dominio extra = new Dominio();
        extra.setCodDominio("22222222222");
        extra.setRagioneSociale("Secondo Comune");
        extra.setAuxDigit(0);
        extra.setStazione(s1);
        dominioRepository.save(extra);

        String etagAfter = currentEtag("INT-001", "INT-001_01");
        org.assertj.core.api.Assertions.assertThat(etagAfter).isNotEqualTo(etagBefore);
    }

    private String currentEtag(String idIntermediario, String idStazione) throws Exception {
        return mvc.perform(get("/intermediari/" + idIntermediario + "/stazioni/" + idStazione)
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("ETag");
    }

    private long countAudit(String azione) {
        return gpAuditRepository.findAll().stream()
                .filter(a -> azione.equals(a.getTipoOggetto()))
                .count();
    }
}
