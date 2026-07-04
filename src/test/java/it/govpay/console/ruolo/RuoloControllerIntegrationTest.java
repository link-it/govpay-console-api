package it.govpay.console.ruolo;

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
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Utenza;
import it.govpay.console.repository.AclRepository;
import it.govpay.console.repository.GpAuditRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.UtenzaRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RuoloControllerIntegrationTest {

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
    private AclRepository aclRepository;
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

        // Catalogo ruoli: righe ACL con id_utenza IS NULL.
        newAclRuolo("AMMINISTRATORE", "Anagrafica Ruoli", "R");
        newAclRuolo("AMMINISTRATORE", "Pendenze", "RW");
        newAclRuolo("OPERATORE", "Pendenze", "R");
        newAclRuolo("VISUALIZZATORE", "Rendicontazioni e Incassi", "R");
    }

    private void newAclRuolo(String ruolo, String servizio, String diritti) {
        Acl acl = new Acl();
        acl.setRuolo(ruolo);
        acl.setServizio(servizio);
        acl.setDiritti(diritti);
        acl.setIdUtenza(null);
        aclRepository.save(acl);
    }

    // --- List ---

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/ruoli"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void listDefaultIsSliceWithoutTotals() throws Exception {
        mvc.perform(get("/ruoli").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(3)))
                .andExpect(jsonPath("$.pagination.hasNextPage", is(false)))
                .andExpect(jsonPath("$.pagination.totalResults").doesNotExist());
    }

    @Test
    void listTotalTrueIncludesCount() throws Exception {
        mvc.perform(get("/ruoli").param("total", "true").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalResults", is(3)))
                .andExpect(jsonPath("$.pagination.totalPages", is(1)));
    }

    @Test
    void listSliceSignalsHasNextPage() throws Exception {
        mvc.perform(get("/ruoli").param("limit", "2").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(2)))
                .andExpect(jsonPath("$.pagination.hasNextPage", is(true)));
    }

    @Test
    void filterByIdRuoloPartial() throws Exception {
        mvc.perform(get("/ruoli").param("idRuolo", "AMMIN").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].idRuolo", is("AMMINISTRATORE")));
    }

    @Test
    void defaultSortByIdRuoloAsc() throws Exception {
        mvc.perform(get("/ruoli").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idRuolo",
                        contains("AMMINISTRATORE", "OPERATORE", "VISUALIZZATORE")));
    }

    @Test
    void sortByIdRuoloDesc() throws Exception {
        mvc.perform(get("/ruoli").param("sort", "-idRuolo").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idRuolo",
                        contains("VISUALIZZATORE", "OPERATORE", "AMMINISTRATORE")));
    }

    @Test
    void unknownSortFieldReturns400() throws Exception {
        mvc.perform(get("/ruoli").param("sort", "-bogus").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("bogus")));
    }

    // --- Get detail ---

    @Test
    void getExistingReturnsDetailWithEtag() throws Exception {
        mvc.perform(get("/ruoli/AMMINISTRATORE").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.idRuolo", is("AMMINISTRATORE")))
                .andExpect(jsonPath("$.acl", hasSize(2)))
                .andExpect(jsonPath("$.acl[*].servizio",
                        contains("Anagrafica Ruoli", "Pendenze")))
                .andExpect(jsonPath("$.acl[1].autorizzazioni", containsInAnyOrder("R", "W")));
    }

    @Test
    void getUnknownReturns404() throws Exception {
        mvc.perform(get("/ruoli/BOGUS").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    // --- Create ---

    @Test
    void createReturns201WithLocationEtagAndByteCompatDiritti() throws Exception {
        String body = """
                {"idRuolo":"NUOVO","acl":[{"servizio":"Pendenze","autorizzazioni":["R","W"]}]}""";
        mvc.perform(post("/ruoli").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/ruoli/NUOVO")))
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.idRuolo", is("NUOVO")))
                .andExpect(jsonPath("$.acl[0].servizio", is("Pendenze")))
                .andExpect(jsonPath("$.acl[0].autorizzazioni", containsInAnyOrder("R", "W")));

        // Byte-compat V1: diritti persistiti come concatenazione senza separatore.
        String diritti = aclRepository.findByRuoloAndIdUtenzaIsNull("NUOVO").get(0).getDiritti();
        org.assertj.core.api.Assertions.assertThat(diritti).isEqualTo("RW");
    }

    @Test
    void createWithApiServiceRoundTrips() throws Exception {
        // I servizi API_* fanno parte del set V1 e non devono essere scartati.
        String body = """
                {"idRuolo":"A2A","acl":[{"servizio":"API Pagamenti","autorizzazioni":["R","W"]}]}""";
        mvc.perform(post("/ruoli").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.acl", hasSize(1)))
                .andExpect(jsonPath("$.acl[0].servizio", is("API Pagamenti")));

        mvc.perform(get("/ruoli/A2A").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acl[0].servizio", is("API Pagamenti")));
    }

    @Test
    void createWithEmptyAutorizzazioniReturns400() throws Exception {
        String body = """
                {"idRuolo":"NOAUTH","acl":[{"servizio":"Pendenze","autorizzazioni":[]}]}""";
        mvc.perform(post("/ruoli").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patchToEmptyAutorizzazioniReturns422() throws Exception {
        String etag = currentEtag("OPERATORE");
        String patch = """
                [{"op":"replace","path":"/acl","value":[{"servizio":"Pendenze","autorizzazioni":[]}]}]""";
        mvc.perform(patch("/ruoli/OPERATORE").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(JSON_PATCH).content(patch))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void createDuplicateReturns409() throws Exception {
        String body = """
                {"idRuolo":"OPERATORE","acl":[{"servizio":"Pendenze","autorizzazioni":["R"]}]}""";
        mvc.perform(post("/ruoli").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void createWithEmptyAclReturns400() throws Exception {
        String body = """
                {"idRuolo":"VUOTO","acl":[]}""";
        mvc.perform(post("/ruoli").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWritesAudit() throws Exception {
        long before = countAudit("RUOLO_CREATE");
        String body = """
                {"idRuolo":"AUDIT","acl":[{"servizio":"Pendenze","autorizzazioni":["R"]}]}""";
        mvc.perform(post("/ruoli").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        org.assertj.core.api.Assertions.assertThat(countAudit("RUOLO_CREATE")).isEqualTo(before + 1);
    }

    // --- Replace (PUT) ---

    @Test
    void replaceWithCorrectIfMatchSucceeds() throws Exception {
        String etag = currentEtag("OPERATORE");
        String body = """
                {"acl":[{"servizio":"Pagamenti","autorizzazioni":["R","W"]},
                        {"servizio":"Pendenze","autorizzazioni":["R"]}]}""";
        String newEtag = mvc.perform(put("/ruoli/OPERATORE").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acl", hasSize(2)))
                .andExpect(jsonPath("$.acl[*].servizio", contains("Pagamenti", "Pendenze")))
                .andReturn().getResponse().getHeader("ETag");
        org.assertj.core.api.Assertions.assertThat(newEtag).isNotEqualTo(etag);
    }

    @Test
    void replaceWithWrongIfMatchReturns412() throws Exception {
        String body = """
                {"acl":[{"servizio":"Pendenze","autorizzazioni":["R"]}]}""";
        mvc.perform(put("/ruoli/OPERATORE").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", "\"deadbeef\"")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void replaceWithoutIfMatchReturns428() throws Exception {
        String body = """
                {"acl":[{"servizio":"Pendenze","autorizzazioni":["R"]}]}""";
        mvc.perform(put("/ruoli/OPERATORE").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPreconditionRequired());
    }

    @Test
    void replaceUnknownReturns404() throws Exception {
        String body = """
                {"acl":[{"servizio":"Pendenze","autorizzazioni":["R"]}]}""";
        mvc.perform(put("/ruoli/BOGUS").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", "*")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    // --- Patch (JSON Patch) ---

    @Test
    void patchReplaceAclSucceeds() throws Exception {
        String etag = currentEtag("OPERATORE");
        String patch = """
                [{"op":"replace","path":"/acl","value":[
                    {"servizio":"Pagamenti","autorizzazioni":["R"]},
                    {"servizio":"Pendenze","autorizzazioni":["R"]}]}]""";
        mvc.perform(patch("/ruoli/OPERATORE").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(JSON_PATCH).content(patch))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acl", hasSize(2)))
                .andExpect(jsonPath("$.acl[*].servizio", contains("Pagamenti", "Pendenze")));
    }

    @Test
    void patchChangingIdRuoloReturns400() throws Exception {
        String etag = currentEtag("OPERATORE");
        String patch = """
                [{"op":"replace","path":"/idRuolo","value":"XXX"}]""";
        mvc.perform(patch("/ruoli/OPERATORE").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(JSON_PATCH).content(patch))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("idRuolo")));
    }

    @Test
    void patchWithWrongIfMatchReturns412() throws Exception {
        String patch = """
                [{"op":"add","path":"/acl/-","value":{"servizio":"Pagamenti","autorizzazioni":["R"]}}]""";
        mvc.perform(patch("/ruoli/OPERATORE").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", "\"deadbeef\"")
                        .contentType(JSON_PATCH).content(patch))
                .andExpect(status().isPreconditionFailed());
    }

    private String currentEtag(String idRuolo) throws Exception {
        return mvc.perform(get("/ruoli/" + idRuolo).with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("ETag");
    }

    private long countAudit(String azione) {
        return gpAuditRepository.findAll().stream()
                .filter(a -> azione.equals(a.getTipoOggetto()))
                .count();
    }
}
