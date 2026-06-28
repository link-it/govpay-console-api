package it.govpay.console.dominio;

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
import it.govpay.console.entity.UnitaOperativa;
import it.govpay.console.entity.Utenza;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.repository.GpAuditRepository;
import it.govpay.console.repository.IntermediarioRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.StazioneRepository;
import it.govpay.console.repository.UnitaOperativaRepository;
import it.govpay.console.repository.UtenzaRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DominioControllerIntegrationTest {

    private static final String PRINCIPAL = "operatore1";
    private static final String PASSWORD = "secret";
    private static final String COD_STAZIONE = "STAZ01";

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
    private UnitaOperativaRepository uoRepository;
    @Autowired
    private GpAuditRepository gpAuditRepository;

    private Stazione stazione;

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

        Intermediario intermediario = new Intermediario();
        intermediario.setCodIntermediario("INT-001");
        intermediario.setDenominazione("Intermediario Uno");
        intermediario.setPrincipal("p-int");
        intermediario.setPrincipalOriginale("p-int");
        intermediario.setCodConnettorePdd("INT-001");
        intermediario.setAbilitato(true);
        intermediarioRepository.save(intermediario);

        stazione = new Stazione();
        stazione.setCodStazione(COD_STAZIONE);
        stazione.setPassword("");
        stazione.setApplicationCode(1);
        stazione.setVersione("V2");
        stazione.setAbilitato(true);
        stazione.setIntermediario(intermediario);
        stazioneRepository.save(stazione);

        newDominio("12345678901", "Comune Alfa", true);
        newDominio("12345678902", "Comune Beta", false);
        newDominio("12345678903", "Comune Gamma", true);
    }

    private void newDominio(String cod, String ragione, boolean abilitato) {
        Dominio d = new Dominio();
        d.setCodDominio(cod);
        d.setRagioneSociale(ragione);
        d.setGln("1234567890123");
        d.setAuxDigit(0);
        d.setAbilitato(abilitato);
        d.setIntermediato(true);
        d.setScaricaFr(true);
        d.setStazione(stazione);
        dominioRepository.save(d);

        UnitaOperativa ec = new UnitaOperativa();
        ec.setCodUo("EC");
        ec.setAbilitato(true);
        ec.setDominio(d);
        ec.setUoCodiceIdentificativo(cod);
        ec.setUoDenominazione(ragione);
        ec.setUoIndirizzo("Via Roma");
        ec.setUoEmail("info@" + cod + ".it");
        uoRepository.save(ec);
    }

    // --- List ---

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/domini"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void listDefaultIsSliceWithoutTotals() throws Exception {
        mvc.perform(get("/domini").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(3)))
                .andExpect(jsonPath("$.pagination.hasNextPage", is(false)))
                .andExpect(jsonPath("$.pagination.totalResults").doesNotExist());
    }

    @Test
    void listTotalTrueIncludesCount() throws Exception {
        mvc.perform(get("/domini").param("total", "true").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalResults", is(3)));
    }

    @Test
    void filterByRagioneSocialePartial() throws Exception {
        mvc.perform(get("/domini").param("ragioneSociale", "beta").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].idDominio", is("12345678902")));
    }

    @Test
    void filterByAbilitato() throws Exception {
        mvc.perform(get("/domini").param("abilitato", "false").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].idDominio", is("12345678902")));
    }

    @Test
    void defaultSortByIdAsc() throws Exception {
        mvc.perform(get("/domini").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idDominio",
                        contains("12345678901", "12345678902", "12345678903")));
    }

    @Test
    void unknownSortFieldReturns400() throws Exception {
        mvc.perform(get("/domini").param("sort", "-bogus").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("bogus")));
    }

    // --- Get detail ---

    @Test
    void getExistingReturnsDetailWithAnagraficaAndRef() throws Exception {
        mvc.perform(get("/domini/12345678901").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.idDominio", is("12345678901")))
                .andExpect(jsonPath("$.ragioneSociale", is("Comune Alfa")))
                .andExpect(jsonPath("$.indirizzo", is("Via Roma")))
                .andExpect(jsonPath("$.email", is("info@12345678901.it")))
                .andExpect(jsonPath("$.idStazione", is(COD_STAZIONE)))
                .andExpect(jsonPath("$.riferimentoIntermediario.idIntermediario", is("INT-001")))
                .andExpect(jsonPath("$.riferimentoIntermediario.denominazione", is("Intermediario Uno")));
    }

    @Test
    void getUnknownReturns404() throws Exception {
        mvc.perform(get("/domini/99999999999").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType("application/problem+json"));
    }

    // --- Create ---

    @Test
    void createReturns201AndPersistsEcAnagrafica() throws Exception {
        String body = """
                {"idDominio":"12345678910","ragioneSociale":"Comune Delta","gln":"0011223344556",
                 "idStazione":"STAZ01","abilitato":true,"scaricaFr":true,
                 "indirizzo":"Corso Italia","cap":"00100","email":"d@delta.it","auxDigit":3,"segregationCode":8}""";
        mvc.perform(post("/domini").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/domini/12345678910")))
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.idDominio", is("12345678910")))
                .andExpect(jsonPath("$.indirizzo", is("Corso Italia")))
                .andExpect(jsonPath("$.auxDigit", is(3)))
                .andExpect(jsonPath("$.segregationCode", is(8)))
                .andExpect(jsonPath("$.riferimentoIntermediario.idIntermediario", is("INT-001")));

        // anagrafica scritta sulla UO EC
        Dominio created = dominioRepository.findByCodDominio("12345678910").orElseThrow();
        UnitaOperativa ec = uoRepository.findByDominio_IdAndCodUo(created.getId(), "EC").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(ec.getUoIndirizzo()).isEqualTo("Corso Italia");
        org.assertj.core.api.Assertions.assertThat(ec.getUoCap()).isEqualTo("00100");
    }

    @Test
    void createDuplicateReturns409() throws Exception {
        String body = """
                {"idDominio":"12345678901","ragioneSociale":"Dup","gln":"1234567890123","idStazione":"STAZ01","abilitato":true,"scaricaFr":true}""";
        mvc.perform(post("/domini").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void createWithUnknownStazioneReturns422() throws Exception {
        String body = """
                {"idDominio":"12345678910","ragioneSociale":"X","gln":"1234567890123","idStazione":"NOPE","abilitato":true,"scaricaFr":true}""";
        mvc.perform(post("/domini").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.detail", containsString("NOPE")));
    }

    @Test
    void createAuxDigit3WithoutSegregationReturns422() throws Exception {
        String body = """
                {"idDominio":"12345678910","ragioneSociale":"X","gln":"1234567890123","idStazione":"STAZ01",
                 "abilitato":true,"scaricaFr":true,"auxDigit":3}""";
        mvc.perform(post("/domini").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.detail", containsString("segregationCode")));
    }

    @Test
    void createWithMalformedIuvPrefixReturns400() throws Exception {
        // "ABC%(a)" non rispetta il pattern sintattico dei placeholder -> Bean Validation 400.
        String body = """
                {"idDominio":"12345678910","ragioneSociale":"X","gln":"1234567890123","idStazione":"STAZ01",
                 "abilitato":true,"scaricaFr":true,"iuvPrefix":"ABC%(a)"}""";
        mvc.perform(post("/domini").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWithTooLongIuvPrefixReturns422() throws Exception {
        // Sintatticamente valido (sole cifre) ma genera un prefisso di 14 cifre -> semantica 422.
        String body = """
                {"idDominio":"12345678910","ragioneSociale":"X","gln":"1234567890123","idStazione":"STAZ01",
                 "abilitato":true,"scaricaFr":true,"iuvPrefix":"12345678901234"}""";
        mvc.perform(post("/domini").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail", containsString("IUV")));
    }

    @Test
    void createInvalidGlnReturns400() throws Exception {
        String body = """
                {"idDominio":"12345678910","ragioneSociale":"X","gln":"abc","idStazione":"STAZ01","abilitato":true,"scaricaFr":true}""";
        mvc.perform(post("/domini").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createSegregationCodeOutOfRangeReturns400() throws Exception {
        String body = """
                {"idDominio":"12345678910","ragioneSociale":"X","gln":"1234567890123","idStazione":"STAZ01",
                 "abilitato":true,"scaricaFr":true,"auxDigit":3,"segregationCode":99}""";
        mvc.perform(post("/domini").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createNonIntermediatoSucceedsWithoutGlnAndStazione() throws Exception {
        String body = """
                {"idDominio":"12345678910","ragioneSociale":"Non Intermediato","abilitato":true,
                 "scaricaFr":false,"intermediato":false}""";
        mvc.perform(post("/domini").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.intermediato", is(false)))
                .andExpect(jsonPath("$.idStazione").doesNotExist())
                .andExpect(jsonPath("$.riferimentoIntermediario").doesNotExist())
                .andExpect(jsonPath("$.auxDigit").doesNotExist());
    }

    @Test
    void patchNonIntermediatoDominioSucceeds() throws Exception {
        // Regressione: per un dominio non intermediato riferimentoIntermediario e' assente,
        // il confronto nel PATCH non deve sollevare NPE (500).
        String create = """
                {"idDominio":"12345678910","ragioneSociale":"Non Int","abilitato":true,
                 "scaricaFr":false,"intermediato":false}""";
        mvc.perform(post("/domini").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isCreated());

        String etag = currentEtag("12345678910");
        String p = """
                [{"op":"replace","path":"/ragioneSociale","value":"Non Int Mod"}]""";
        mvc.perform(patch("/domini/12345678910").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(JSON_PATCH).content(p))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ragioneSociale", is("Non Int Mod")))
                .andExpect(jsonPath("$.auxDigit").doesNotExist());
    }

    @Test
    void createNonIntermediatoWithGlnReturns422() throws Exception {
        String body = """
                {"idDominio":"12345678910","ragioneSociale":"X","abilitato":true,"scaricaFr":false,
                 "intermediato":false,"gln":"1234567890123"}""";
        mvc.perform(post("/domini").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail", containsString("gln")));
    }

    @Test
    void createIntermediatoWithoutGlnReturns422() throws Exception {
        String body = """
                {"idDominio":"12345678910","ragioneSociale":"X","idStazione":"STAZ01","abilitato":true,"scaricaFr":true}""";
        mvc.perform(post("/domini").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail", containsString("gln")));
    }

    @Test
    void createWithNumericIuvPrefixSucceeds() throws Exception {
        String body = """
                {"idDominio":"12345678910","ragioneSociale":"X","gln":"1234567890123","idStazione":"STAZ01",
                 "abilitato":true,"scaricaFr":true,"iuvPrefix":"%(y)%(a)"}""";
        mvc.perform(post("/domini").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void createWithMissingRequiredReturns400() throws Exception {
        String body = """
                {"idDominio":"12345678910","gln":"1234567890123","idStazione":"STAZ01","abilitato":true,"scaricaFr":true}""";
        mvc.perform(post("/domini").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createInvalidIdDominioReturns400() throws Exception {
        String body = """
                {"idDominio":"ABC","ragioneSociale":"X","gln":"1234567890123","idStazione":"STAZ01","abilitato":true,"scaricaFr":true}""";
        mvc.perform(post("/domini").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWritesAudit() throws Exception {
        long before = countAudit("DOMINIO_CREATE");
        String body = """
                {"idDominio":"12345678920","ragioneSociale":"Aud","gln":"1234567890123","idStazione":"STAZ01","abilitato":true,"scaricaFr":true}""";
        mvc.perform(post("/domini").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        org.assertj.core.api.Assertions.assertThat(countAudit("DOMINIO_CREATE")).isEqualTo(before + 1);
    }

    // --- Replace ---

    @Test
    void replaceUpdatesDominioAndEc() throws Exception {
        String etag = currentEtag("12345678901");
        String body = """
                {"ragioneSociale":"Comune Alfa Agg","gln":"1234567890123","idStazione":"STAZ01","abilitato":false,"scaricaFr":false,
                 "indirizzo":"Nuova Via","localita":"Roma"}""";
        String newEtag = mvc.perform(put("/domini/12345678901").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ragioneSociale", is("Comune Alfa Agg")))
                .andExpect(jsonPath("$.abilitato", is(false)))
                .andExpect(jsonPath("$.indirizzo", is("Nuova Via")))
                .andExpect(jsonPath("$.localita", is("Roma")))
                .andReturn().getResponse().getHeader("ETag");
        org.assertj.core.api.Assertions.assertThat(newEtag).isNotEqualTo(etag);
    }

    @Test
    void replaceWithoutIfMatchReturns428() throws Exception {
        String body = """
                {"ragioneSociale":"X","gln":"1234567890123","idStazione":"STAZ01","abilitato":true,"scaricaFr":true}""";
        mvc.perform(put("/domini/12345678901").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPreconditionRequired());
    }

    @Test
    void replaceWithWrongIfMatchReturns412() throws Exception {
        String body = """
                {"ragioneSociale":"X","gln":"1234567890123","idStazione":"STAZ01","abilitato":true,"scaricaFr":true}""";
        mvc.perform(put("/domini/12345678901").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", "\"deadbeef\"")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void replaceUnknownReturns404() throws Exception {
        String body = """
                {"ragioneSociale":"X","gln":"1234567890123","idStazione":"STAZ01","abilitato":true,"scaricaFr":true}""";
        mvc.perform(put("/domini/99999999999").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", "*")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    // --- Patch ---

    @Test
    void patchReplaceRagioneSociale() throws Exception {
        String etag = currentEtag("12345678901");
        String p = """
                [{"op":"replace","path":"/ragioneSociale","value":"Comune Patchato"}]""";
        mvc.perform(patch("/domini/12345678901").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(JSON_PATCH).content(p))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ragioneSociale", is("Comune Patchato")))
                .andExpect(jsonPath("$.indirizzo", is("Via Roma")));
    }

    @Test
    void patchAnagraficaField() throws Exception {
        String etag = currentEtag("12345678901");
        String p = """
                [{"op":"replace","path":"/indirizzo","value":"Via Patchata"}]""";
        mvc.perform(patch("/domini/12345678901").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(JSON_PATCH).content(p))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indirizzo", is("Via Patchata")));
    }

    @Test
    void patchRagioneSocialeTooLongReturns400() throws Exception {
        String etag = currentEtag("12345678901");
        String tooLong = "X".repeat(71);
        String p = "[{\"op\":\"replace\",\"path\":\"/ragioneSociale\",\"value\":\"" + tooLong + "\"}]";
        mvc.perform(patch("/domini/12345678901").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(JSON_PATCH).content(p))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void patchAuxDigit3WithoutSegregationReturns422() throws Exception {
        String etag = currentEtag("12345678901");
        String p = """
                [{"op":"replace","path":"/auxDigit","value":3}]""";
        mvc.perform(patch("/domini/12345678901").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(JSON_PATCH).content(p))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail", containsString("segregationCode")));
    }

    @Test
    void patchRiferimentoIntermediarioReturns400() throws Exception {
        String etag = currentEtag("12345678901");
        String p = """
                [{"op":"replace","path":"/riferimentoIntermediario","value":{"idIntermediario":"INT-999","denominazione":"X"}}]""";
        mvc.perform(patch("/domini/12345678901").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(JSON_PATCH).content(p))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("riferimentoIntermediario")));
    }

    @Test
    void patchChangingIdReturns400() throws Exception {
        String etag = currentEtag("12345678901");
        String p = """
                [{"op":"replace","path":"/idDominio","value":"99999999999"}]""";
        mvc.perform(patch("/domini/12345678901").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(JSON_PATCH).content(p))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("idDominio")));
    }

    private String currentEtag(String cod) throws Exception {
        return mvc.perform(get("/domini/" + cod).with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("ETag");
    }

    private long countAudit(String azione) {
        return gpAuditRepository.findAll().stream()
                .filter(a -> azione.equals(a.getTipoOggetto()))
                .count();
    }
}
