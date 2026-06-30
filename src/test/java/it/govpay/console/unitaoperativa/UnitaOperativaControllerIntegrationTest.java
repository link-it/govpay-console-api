package it.govpay.console.unitaoperativa;

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
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.UnitaOperativa;
import it.govpay.console.entity.Utenza;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.repository.GpAuditRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.UnitaOperativaRepository;
import it.govpay.console.repository.UtenzaRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UnitaOperativaControllerIntegrationTest {

    private static final String PRINCIPAL = "operatore1";
    private static final String PASSWORD = "secret";
    private static final String ID_DOMINIO = "12345678901";

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
    private DominioRepository dominioRepository;
    @Autowired
    private UnitaOperativaRepository uoRepository;
    @Autowired
    private GpAuditRepository gpAuditRepository;

    private Dominio dominio;

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

        dominio = new Dominio();
        dominio.setCodDominio(ID_DOMINIO);
        dominio.setRagioneSociale("Comune Alfa");
        dominio.setAuxDigit(0);
        dominio.setAbilitato(true);
        dominio.setIntermediato(true);
        dominio.setScaricaFr(true);
        dominioRepository.save(dominio);

        // EC: anagrafica del dominio, NON una unita' operativa della sub-resource.
        newUo("EC", "Comune Alfa", true);
        newUo("UO-A", "Ufficio Tributi", true);
        newUo("UO-B", "Ufficio Anagrafe", false);
        newUo("UO-C", "Ufficio Tecnico", true);
    }

    private void newUo(String codUo, String denominazione, boolean abilitato) {
        UnitaOperativa uo = new UnitaOperativa();
        uo.setCodUo(codUo);
        uo.setUoCodiceIdentificativo(codUo);
        uo.setUoDenominazione(denominazione);
        uo.setUoIndirizzo("Via Roma");
        uo.setUoEmail(codUo.toLowerCase() + "@alfa.it");
        uo.setAbilitato(abilitato);
        uo.setDominio(dominio);
        uoRepository.save(uo);
    }

    // --- List ---

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/domini/" + ID_DOMINIO + "/unitaOperative"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void listExcludesEcAndReturnsOperatingUnits() throws Exception {
        mvc.perform(get("/domini/" + ID_DOMINIO + "/unitaOperative").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(3)))
                .andExpect(jsonPath("$.results[*].idUnitaOperativa", contains("UO-A", "UO-B", "UO-C")));
    }

    @Test
    void listTotalTrueIncludesCount() throws Exception {
        mvc.perform(get("/domini/" + ID_DOMINIO + "/unitaOperative").param("total", "true")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalResults", is(3)));
    }

    @Test
    void listFilterByRagioneSociale() throws Exception {
        mvc.perform(get("/domini/" + ID_DOMINIO + "/unitaOperative").param("ragioneSociale", "anagrafe")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].idUnitaOperativa", is("UO-B")));
    }

    @Test
    void listFilterByAbilitato() throws Exception {
        mvc.perform(get("/domini/" + ID_DOMINIO + "/unitaOperative").param("abilitato", "false")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].idUnitaOperativa", is("UO-B")));
    }

    @Test
    void unknownSortFieldReturns400() throws Exception {
        mvc.perform(get("/domini/" + ID_DOMINIO + "/unitaOperative").param("sort", "-bogus")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("bogus")));
    }

    @Test
    void listUnknownDominioReturns404() throws Exception {
        mvc.perform(get("/domini/99999999999/unitaOperative").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    // --- Get detail ---

    @Test
    void getExistingReturnsDetailWithEtag() throws Exception {
        mvc.perform(get("/domini/" + ID_DOMINIO + "/unitaOperative/UO-A").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.idUnitaOperativa", is("UO-A")))
                .andExpect(jsonPath("$.ragioneSociale", is("Ufficio Tributi")))
                .andExpect(jsonPath("$.indirizzo", is("Via Roma")))
                .andExpect(jsonPath("$.email", is("uo-a@alfa.it")));
    }

    @Test
    void getEcReturns404() throws Exception {
        mvc.perform(get("/domini/" + ID_DOMINIO + "/unitaOperative/EC").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getUnknownReturns404() throws Exception {
        mvc.perform(get("/domini/" + ID_DOMINIO + "/unitaOperative/UO-Z").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    // --- Create ---

    @Test
    void createReturns201AndPersists() throws Exception {
        String body = """
                {"idUnitaOperativa":"UO-NEW","ragioneSociale":"Ufficio Nuovo","abilitato":true,
                 "indirizzo":"Corso Italia","cap":"00100","email":"new@alfa.it"}""";
        mvc.perform(post("/domini/" + ID_DOMINIO + "/unitaOperative").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/unitaOperative/UO-NEW")))
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.idUnitaOperativa", is("UO-NEW")))
                .andExpect(jsonPath("$.indirizzo", is("Corso Italia")));

        UnitaOperativa created = uoRepository
                .findByDominio_IdAndCodUo(dominio.getId(), "UO-NEW").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(created.getUoCodiceIdentificativo()).isEqualTo("UO-NEW");
        org.assertj.core.api.Assertions.assertThat(created.getUoCap()).isEqualTo("00100");
    }

    @Test
    void createDuplicateReturns409() throws Exception {
        String body = """
                {"idUnitaOperativa":"UO-A","ragioneSociale":"Dup","abilitato":true}""";
        mvc.perform(post("/domini/" + ID_DOMINIO + "/unitaOperative").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void createWithReservedEcCodeReturns422() throws Exception {
        String body = """
                {"idUnitaOperativa":"EC","ragioneSociale":"X","abilitato":true}""";
        mvc.perform(post("/domini/" + ID_DOMINIO + "/unitaOperative").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail", containsString("EC")));
    }

    @Test
    void createUnknownDominioReturns404() throws Exception {
        String body = """
                {"idUnitaOperativa":"UO-NEW","ragioneSociale":"X","abilitato":true}""";
        mvc.perform(post("/domini/99999999999/unitaOperative").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void createMissingRagioneSocialeReturns400() throws Exception {
        String body = """
                {"idUnitaOperativa":"UO-NEW","abilitato":true}""";
        mvc.perform(post("/domini/" + ID_DOMINIO + "/unitaOperative").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createInvalidIdUnitaOperativaReturns400() throws Exception {
        String body = """
                {"idUnitaOperativa":"non valido!","ragioneSociale":"X","abilitato":true}""";
        mvc.perform(post("/domini/" + ID_DOMINIO + "/unitaOperative").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWritesAudit() throws Exception {
        long before = countAudit(UnitaOperativaService.AZIONE_AUDIT_CREATE);
        String body = """
                {"idUnitaOperativa":"UO-AUD","ragioneSociale":"Aud","abilitato":true}""";
        mvc.perform(post("/domini/" + ID_DOMINIO + "/unitaOperative").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        org.assertj.core.api.Assertions.assertThat(countAudit(UnitaOperativaService.AZIONE_AUDIT_CREATE))
                .isEqualTo(before + 1);
    }

    // --- Replace ---

    @Test
    void replaceUpdates() throws Exception {
        String etag = currentEtag("UO-A");
        String body = """
                {"ragioneSociale":"Ufficio Tributi Agg","abilitato":false,"localita":"Roma"}""";
        String newEtag = mvc.perform(put("/domini/" + ID_DOMINIO + "/unitaOperative/UO-A")
                        .with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ragioneSociale", is("Ufficio Tributi Agg")))
                .andExpect(jsonPath("$.abilitato", is(false)))
                .andExpect(jsonPath("$.localita", is("Roma")))
                .andReturn().getResponse().getHeader("ETag");
        org.assertj.core.api.Assertions.assertThat(newEtag).isNotEqualTo(etag);
    }

    @Test
    void replaceWithoutIfMatchReturns428() throws Exception {
        String body = """
                {"ragioneSociale":"X","abilitato":true}""";
        mvc.perform(put("/domini/" + ID_DOMINIO + "/unitaOperative/UO-A").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPreconditionRequired());
    }

    @Test
    void replaceWithWrongIfMatchReturns412() throws Exception {
        String body = """
                {"ragioneSociale":"X","abilitato":true}""";
        mvc.perform(put("/domini/" + ID_DOMINIO + "/unitaOperative/UO-A").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", "\"deadbeef\"")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void replaceUnknownReturns404() throws Exception {
        String body = """
                {"ragioneSociale":"X","abilitato":true}""";
        mvc.perform(put("/domini/" + ID_DOMINIO + "/unitaOperative/UO-Z").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", "*")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    // --- Patch ---

    @Test
    void patchRagioneSociale() throws Exception {
        String etag = currentEtag("UO-A");
        String p = """
                [{"op":"replace","path":"/ragioneSociale","value":"Ufficio Patchato"}]""";
        mvc.perform(patch("/domini/" + ID_DOMINIO + "/unitaOperative/UO-A").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(JSON_PATCH).content(p))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ragioneSociale", is("Ufficio Patchato")))
                .andExpect(jsonPath("$.indirizzo", is("Via Roma")));
    }

    @Test
    void patchChangingIdReturns400() throws Exception {
        String etag = currentEtag("UO-A");
        String p = """
                [{"op":"replace","path":"/idUnitaOperativa","value":"UO-X"}]""";
        mvc.perform(patch("/domini/" + ID_DOMINIO + "/unitaOperative/UO-A").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(JSON_PATCH).content(p))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("idUnitaOperativa")));
    }

    @Test
    void patchRagioneSocialeTooLongReturns400() throws Exception {
        String etag = currentEtag("UO-A");
        String tooLong = "X".repeat(71);
        String p = "[{\"op\":\"replace\",\"path\":\"/ragioneSociale\",\"value\":\"" + tooLong + "\"}]";
        mvc.perform(patch("/domini/" + ID_DOMINIO + "/unitaOperative/UO-A").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(JSON_PATCH).content(p))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"));
    }

    private String currentEtag(String codUo) throws Exception {
        return mvc.perform(get("/domini/" + ID_DOMINIO + "/unitaOperative/" + codUo)
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
