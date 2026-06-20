package it.govpay.console.intermediario;

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
import it.govpay.console.entity.Intermediario;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Utenza;
import it.govpay.console.repository.GpAuditRepository;
import it.govpay.console.repository.IntermediarioRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.UtenzaRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class IntermediarioControllerIntegrationTest {

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

        newIntermediario("INT-001", "Alfa SPA", "p-alfa", "CONN-PDD-1", true);
        newIntermediario("INT-002", "Beta SRL", "p-beta", "CONN-PDD-2", false);
        newIntermediario("INT-003", "Gamma SCARL", "p-gamma", "CONN-PDD-3", true);
    }

    private void newIntermediario(String cod, String denom, String principal, String pdd, boolean abilitato) {
        Intermediario i = new Intermediario();
        i.setCodIntermediario(cod);
        i.setDenominazione(denom);
        i.setPrincipal(principal);
        i.setPrincipalOriginale(principal);
        i.setCodConnettorePdd(pdd);
        i.setAbilitato(abilitato);
        intermediarioRepository.save(i);
    }

    // --- List ---

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/intermediari"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void listDefaultIsSliceWithoutTotals() throws Exception {
        mvc.perform(get("/intermediari").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(3)))
                .andExpect(jsonPath("$.pagination.page", is(1)))
                .andExpect(jsonPath("$.pagination.limit", is(25)))
                .andExpect(jsonPath("$.pagination.hasNextPage", is(false)))
                .andExpect(jsonPath("$.pagination.totalResults").doesNotExist())
                .andExpect(jsonPath("$.pagination.totalPages").doesNotExist());
    }

    @Test
    void listTotalTrueIncludesCount() throws Exception {
        mvc.perform(get("/intermediari").param("total", "true").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalResults", is(3)))
                .andExpect(jsonPath("$.pagination.totalPages", is(1)));
    }

    @Test
    void listSliceSignalsHasNextPage() throws Exception {
        mvc.perform(get("/intermediari").param("limit", "2").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(2)))
                .andExpect(jsonPath("$.pagination.hasNextPage", is(true)));
    }

    @Test
    void filterByCodIntermediarioPartial() throws Exception {
        mvc.perform(get("/intermediari").param("codIntermediario", "001").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].idIntermediario", is("INT-001")));
    }

    @Test
    void filterByDenominazionePartialCaseInsensitive() throws Exception {
        mvc.perform(get("/intermediari").param("denominazione", "alf").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].idIntermediario", is("INT-001")));
    }

    @Test
    void filterByAbilitato() throws Exception {
        mvc.perform(get("/intermediari").param("abilitato", "false").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].idIntermediario", is("INT-002")));
    }

    @Test
    void defaultSortByCodIntermediarioAsc() throws Exception {
        mvc.perform(get("/intermediari").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idIntermediario",
                        contains("INT-001", "INT-002", "INT-003")));
    }

    @Test
    void customSortDesc() throws Exception {
        mvc.perform(get("/intermediari").param("sort", "-codIntermediario").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idIntermediario",
                        contains("INT-003", "INT-002", "INT-001")));
    }

    @Test
    void unknownSortFieldReturns400() throws Exception {
        mvc.perform(get("/intermediari").param("sort", "-bogus").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.detail", containsString("bogus")));
    }

    // --- Get detail + ETag ---

    @Test
    void getExistingReturnsDetailWithEtag() throws Exception {
        mvc.perform(get("/intermediari/INT-001").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.idIntermediario", is("INT-001")))
                .andExpect(jsonPath("$.denominazione", is("Alfa SPA")))
                .andExpect(jsonPath("$.principalPagoPa", is("p-alfa")))
                .andExpect(jsonPath("$.abilitato", is(true)))
                .andExpect(jsonPath("$.codConnettorePagoPa").doesNotExist());
    }

    @Test
    void getUnknownReturns404() throws Exception {
        mvc.perform(get("/intermediari/INT-999").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType("application/problem+json"));
    }

    // --- Create ---

    @Test
    void createReturns201WithLocationAndEtag() throws Exception {
        String body = """
                {"idIntermediario":"INT-NEW","denominazione":"Delta","principalPagoPa":"p-delta","abilitato":true}""";
        mvc.perform(post("/intermediari").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/intermediari/INT-NEW")))
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.idIntermediario", is("INT-NEW")))
                .andExpect(jsonPath("$.denominazione", is("Delta")));
    }

    @Test
    void createDuplicateReturns409() throws Exception {
        String body = """
                {"idIntermediario":"INT-001","denominazione":"Dup","principalPagoPa":"p","abilitato":true}""";
        mvc.perform(post("/intermediari").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void createWithMissingFieldReturns400() throws Exception {
        String body = """
                {"idIntermediario":"INT-NEW","principalPagoPa":"p","abilitato":true}""";
        mvc.perform(post("/intermediari").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void createWritesAudit() throws Exception {
        long before = countAudit("INTERMEDIARIO_CREATE");
        String body = """
                {"idIntermediario":"INT-AUD","denominazione":"Aud","principalPagoPa":"p","abilitato":true}""";
        mvc.perform(post("/intermediari").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        org.assertj.core.api.Assertions.assertThat(countAudit("INTERMEDIARIO_CREATE")).isEqualTo(before + 1);
    }

    // --- Replace (PUT) ---

    @Test
    void replaceWithCorrectIfMatchSucceeds() throws Exception {
        String etag = currentEtag("INT-001");
        String body = """
                {"denominazione":"Alfa Aggiornata","principalPagoPa":"p-alfa2","abilitato":false}""";
        String newEtag = mvc.perform(put("/intermediari/INT-001").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.denominazione", is("Alfa Aggiornata")))
                .andExpect(jsonPath("$.abilitato", is(false)))
                .andReturn().getResponse().getHeader("ETag");
        org.assertj.core.api.Assertions.assertThat(newEtag).isNotEqualTo(etag);
    }

    @Test
    void replaceWithWrongIfMatchReturns412() throws Exception {
        String body = """
                {"denominazione":"X","principalPagoPa":"p","abilitato":true}""";
        mvc.perform(put("/intermediari/INT-001").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", "\"deadbeef\"")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPreconditionFailed())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void replaceWithoutIfMatchReturns428() throws Exception {
        String body = """
                {"denominazione":"X","principalPagoPa":"p","abilitato":true}""";
        mvc.perform(put("/intermediari/INT-001").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPreconditionRequired())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void replaceUnknownReturns404() throws Exception {
        String body = """
                {"denominazione":"X","principalPagoPa":"p","abilitato":true}""";
        mvc.perform(put("/intermediari/INT-999").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", "*")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void replaceWritesAudit() throws Exception {
        long before = countAudit("INTERMEDIARIO_MODIFICA");
        String etag = currentEtag("INT-001");
        String body = """
                {"denominazione":"Alfa Mod","principalPagoPa":"p","abilitato":true}""";
        mvc.perform(put("/intermediari/INT-001").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(countAudit("INTERMEDIARIO_MODIFICA")).isEqualTo(before + 1);
    }

    // --- Patch (JSON Patch) ---

    @Test
    void patchReplaceFieldSucceeds() throws Exception {
        String etag = currentEtag("INT-001");
        String patch = """
                [{"op":"replace","path":"/denominazione","value":"Alfa Patchata"}]""";
        mvc.perform(patch("/intermediari/INT-001").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(JSON_PATCH).content(patch))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.denominazione", is("Alfa Patchata")))
                .andExpect(jsonPath("$.principalPagoPa", is("p-alfa")));
    }

    @Test
    void patchChangingIdIntermediarioReturns400() throws Exception {
        String etag = currentEtag("INT-001");
        String patch = """
                [{"op":"replace","path":"/idIntermediario","value":"INT-XXX"}]""";
        mvc.perform(patch("/intermediari/INT-001").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(JSON_PATCH).content(patch))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("idIntermediario")));
    }

    @Test
    void patchRemovingRequiredFieldReturns400() throws Exception {
        String etag = currentEtag("INT-001");
        String patch = """
                [{"op":"remove","path":"/principalPagoPa"}]""";
        mvc.perform(patch("/intermediari/INT-001").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(JSON_PATCH).content(patch))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patchWithWrongIfMatchReturns412() throws Exception {
        String patch = """
                [{"op":"replace","path":"/denominazione","value":"X"}]""";
        mvc.perform(patch("/intermediari/INT-001").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", "\"deadbeef\"")
                        .contentType(JSON_PATCH).content(patch))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void patchUnsupportedPointerReturns400() throws Exception {
        String etag = currentEtag("INT-001");
        String patch = """
                [{"op":"replace","path":"/nested/field","value":"X"}]""";
        mvc.perform(patch("/intermediari/INT-001").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(JSON_PATCH).content(patch))
                .andExpect(status().isBadRequest());
    }

    private String currentEtag(String cod) throws Exception {
        return mvc.perform(get("/intermediari/" + cod).with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("ETag");
    }

    private long countAudit(String azione) {
        return gpAuditRepository.findAll().stream()
                .filter(a -> azione.equals(a.getTipoOggetto()))
                .count();
    }
}
