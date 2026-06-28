package it.govpay.console.entrata;

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
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.TipoTributo;
import it.govpay.console.entity.Utenza;
import it.govpay.console.repository.GpAuditRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.TipoTributoRepository;
import it.govpay.console.repository.UtenzaRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EntrataControllerIntegrationTest {

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
    private TipoTributoRepository tipoTributoRepository;
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

        newEntrata("IMU", "Imposta municipale", "2", "3321");
        newEntrata("TARI", "Tassa rifiuti", "0", "1234");
        newEntrata("TOSAP", "Occupazione suolo", "9", "9999");
    }

    private void newEntrata(String cod, String descr, String tipoContabilita, String codContabilita) {
        TipoTributo t = new TipoTributo();
        t.setCodTributo(cod);
        t.setDescrizione(descr);
        t.setTipoContabilita(tipoContabilita);
        t.setCodContabilita(codContabilita);
        tipoTributoRepository.save(t);
    }

    // --- List ---

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/entrate"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void listDefaultIsSliceWithoutTotals() throws Exception {
        mvc.perform(get("/entrate").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(3)))
                .andExpect(jsonPath("$.pagination.hasNextPage", is(false)))
                .andExpect(jsonPath("$.pagination.totalResults").doesNotExist());
    }

    @Test
    void listTotalTrueIncludesCount() throws Exception {
        mvc.perform(get("/entrate").param("total", "true").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalResults", is(3)))
                .andExpect(jsonPath("$.pagination.totalPages", is(1)));
    }

    @Test
    void listSliceSignalsHasNextPage() throws Exception {
        mvc.perform(get("/entrate").param("limit", "2").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(2)))
                .andExpect(jsonPath("$.pagination.hasNextPage", is(true)));
    }

    @Test
    void filterByIdEntrataPartial() throws Exception {
        mvc.perform(get("/entrate").param("idEntrata", "ta").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].idEntrata", is("TARI")));
    }

    @Test
    void filterByDescrizionePartialCaseInsensitive() throws Exception {
        mvc.perform(get("/entrate").param("descrizione", "imposta").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].idEntrata", is("IMU")));
    }

    @Test
    void defaultSortByIdEntrataAsc() throws Exception {
        mvc.perform(get("/entrate").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idEntrata", contains("IMU", "TARI", "TOSAP")));
    }

    @Test
    void customSortDesc() throws Exception {
        mvc.perform(get("/entrate").param("sort", "-idEntrata").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idEntrata", contains("TOSAP", "TARI", "IMU")));
    }

    @Test
    void unknownSortFieldReturns400() throws Exception {
        mvc.perform(get("/entrate").param("sort", "-bogus").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.detail", containsString("bogus")));
    }

    // --- Get detail + ETag ---

    @Test
    void getExistingReturnsDetailWithEtag() throws Exception {
        mvc.perform(get("/entrate/IMU").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.idEntrata", is("IMU")))
                .andExpect(jsonPath("$.descrizione", is("Imposta municipale")))
                .andExpect(jsonPath("$.tipoContabilita", is("SIOPE")))
                .andExpect(jsonPath("$.codiceContabilita", is("3321")));
    }

    @Test
    void getUnknownReturns404() throws Exception {
        mvc.perform(get("/entrate/NOPE").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType("application/problem+json"));
    }

    // --- Create ---

    @Test
    void createReturns201WithLocationAndEtag() throws Exception {
        String body = """
                {"idEntrata":"COSAP","descrizione":"Canone","tipoContabilita":"CAPITOLO","codiceContabilita":"5000"}""";
        mvc.perform(post("/entrate").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/entrate/COSAP")))
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.idEntrata", is("COSAP")))
                .andExpect(jsonPath("$.tipoContabilita", is("CAPITOLO")));
    }

    @Test
    void createDuplicateReturns409() throws Exception {
        String body = """
                {"idEntrata":"IMU","descrizione":"Dup","tipoContabilita":"SIOPE","codiceContabilita":"1"}""";
        mvc.perform(post("/entrate").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void createWithMissingFieldReturns400() throws Exception {
        String body = """
                {"idEntrata":"COSAP","tipoContabilita":"SIOPE","codiceContabilita":"1"}""";
        mvc.perform(post("/entrate").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void createWritesAudit() throws Exception {
        long before = countAudit("ENTRATA_CREATE");
        String body = """
                {"idEntrata":"AUD","descrizione":"Aud","tipoContabilita":"ALTRO","codiceContabilita":"1"}""";
        mvc.perform(post("/entrate").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        org.assertj.core.api.Assertions.assertThat(countAudit("ENTRATA_CREATE")).isEqualTo(before + 1);
    }

    // --- Replace (PUT) ---

    @Test
    void replaceWithCorrectIfMatchSucceeds() throws Exception {
        String etag = currentEtag("IMU");
        String body = """
                {"descrizione":"Imposta agg","tipoContabilita":"CAPITOLO","codiceContabilita":"7777"}""";
        String newEtag = mvc.perform(put("/entrate/IMU").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descrizione", is("Imposta agg")))
                .andExpect(jsonPath("$.tipoContabilita", is("CAPITOLO")))
                .andExpect(jsonPath("$.codiceContabilita", is("7777")))
                .andReturn().getResponse().getHeader("ETag");
        org.assertj.core.api.Assertions.assertThat(newEtag).isNotEqualTo(etag);
    }

    @Test
    void replaceWithWrongIfMatchReturns412() throws Exception {
        String body = """
                {"descrizione":"X","tipoContabilita":"SIOPE","codiceContabilita":"1"}""";
        mvc.perform(put("/entrate/IMU").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", "\"deadbeef\"")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPreconditionFailed())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void replaceWithoutIfMatchReturns428() throws Exception {
        String body = """
                {"descrizione":"X","tipoContabilita":"SIOPE","codiceContabilita":"1"}""";
        mvc.perform(put("/entrate/IMU").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPreconditionRequired())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void replaceUnknownReturns404() throws Exception {
        String body = """
                {"descrizione":"X","tipoContabilita":"SIOPE","codiceContabilita":"1"}""";
        mvc.perform(put("/entrate/NOPE").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", "*")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    // --- Patch (JSON Patch) ---

    @Test
    void patchReplaceFieldSucceeds() throws Exception {
        String etag = currentEtag("IMU");
        String patch = """
                [{"op":"replace","path":"/descrizione","value":"Imposta patchata"}]""";
        mvc.perform(patch("/entrate/IMU").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(JSON_PATCH).content(patch))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descrizione", is("Imposta patchata")))
                .andExpect(jsonPath("$.tipoContabilita", is("SIOPE")));
    }

    @Test
    void patchChangingIdEntrataReturns400() throws Exception {
        String etag = currentEtag("IMU");
        String patch = """
                [{"op":"replace","path":"/idEntrata","value":"XXX"}]""";
        mvc.perform(patch("/entrate/IMU").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(JSON_PATCH).content(patch))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("idEntrata")));
    }

    @Test
    void patchInvalidTipoContabilitaReturns400() throws Exception {
        String etag = currentEtag("IMU");
        String patch = """
                [{"op":"replace","path":"/tipoContabilita","value":"BOGUS"}]""";
        mvc.perform(patch("/entrate/IMU").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(JSON_PATCH).content(patch))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("tipoContabilita")));
    }

    @Test
    void patchWithWrongIfMatchReturns412() throws Exception {
        String patch = """
                [{"op":"replace","path":"/descrizione","value":"X"}]""";
        mvc.perform(patch("/entrate/IMU").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", "\"deadbeef\"")
                        .contentType(JSON_PATCH).content(patch))
                .andExpect(status().isPreconditionFailed());
    }

    private String currentEtag(String cod) throws Exception {
        return mvc.perform(get("/entrate/" + cod).with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("ETag");
    }

    private long countAudit(String azione) {
        return gpAuditRepository.findAll().stream()
                .filter(a -> azione.equals(a.getTipoOggetto()))
                .count();
    }
}
