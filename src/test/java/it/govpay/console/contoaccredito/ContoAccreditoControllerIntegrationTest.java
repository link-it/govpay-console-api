package it.govpay.console.contoaccredito;

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
import it.govpay.console.entity.IbanAccredito;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Utenza;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.repository.GpAuditRepository;
import it.govpay.console.repository.IbanAccreditoRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.UtenzaRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ContoAccreditoControllerIntegrationTest {

    private static final String PRINCIPAL = "operatore1";
    private static final String PASSWORD = "secret";
    private static final String ID_DOMINIO = "12345678901";
    private static final String ID_DOMINIO_2 = "12345678902";

    private static final String IBAN_A = "IT60X0542811101000000000001";
    private static final String IBAN_B = "IT60X0542811101000000000002";
    private static final String IBAN_C = "IT60X0542811101000000000003";

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
    private IbanAccreditoRepository ibanRepository;
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

        dominio = saveDominio(ID_DOMINIO, "Comune Alfa");
        saveDominio(ID_DOMINIO_2, "Comune Beta");

        newIban(dominio, IBAN_A, "Conto Tesoreria", true);
        newIban(dominio, IBAN_B, "Conto Postale", false);
        newIban(dominio, IBAN_C, "Conto Economato", true);
    }

    private Dominio saveDominio(String cod, String ragione) {
        Dominio d = new Dominio();
        d.setCodDominio(cod);
        d.setRagioneSociale(ragione);
        d.setAuxDigit(0);
        d.setAbilitato(true);
        d.setIntermediato(true);
        d.setScaricaFr(true);
        return dominioRepository.save(d);
    }

    private void newIban(Dominio d, String codIban, String descrizione, boolean abilitato) {
        IbanAccredito iban = new IbanAccredito();
        iban.setDominio(d);
        iban.setCodIban(codIban);
        iban.setDescrizione(descrizione);
        iban.setPostale(false);
        iban.setAbilitato(abilitato);
        ibanRepository.save(iban);
    }

    // --- List ---

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/domini/" + ID_DOMINIO + "/contiAccredito"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void listReturnsContiOfDominio() throws Exception {
        mvc.perform(get("/domini/" + ID_DOMINIO + "/contiAccredito").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(3)))
                .andExpect(jsonPath("$.results[*].ibanAccredito", contains(IBAN_A, IBAN_B, IBAN_C)));
    }

    @Test
    void listFilterByDescrizione() throws Exception {
        mvc.perform(get("/domini/" + ID_DOMINIO + "/contiAccredito").param("descrizione", "postale")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].ibanAccredito", is(IBAN_B)));
    }

    @Test
    void listFilterByAbilitato() throws Exception {
        mvc.perform(get("/domini/" + ID_DOMINIO + "/contiAccredito").param("abilitato", "false")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].ibanAccredito", is(IBAN_B)));
    }

    @Test
    void listTotalTrueIncludesCount() throws Exception {
        mvc.perform(get("/domini/" + ID_DOMINIO + "/contiAccredito").param("total", "true")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalResults", is(3)));
    }

    @Test
    void unknownSortFieldReturns400() throws Exception {
        mvc.perform(get("/domini/" + ID_DOMINIO + "/contiAccredito").param("sort", "-bogus")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("bogus")));
    }

    @Test
    void listUnknownDominioReturns404() throws Exception {
        mvc.perform(get("/domini/99999999999/contiAccredito").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    // --- Get detail ---

    @Test
    void getExistingReturnsDetailWithEtag() throws Exception {
        mvc.perform(get("/domini/" + ID_DOMINIO + "/contiAccredito/" + IBAN_A)
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.ibanAccredito", is(IBAN_A)))
                .andExpect(jsonPath("$.descrizione", is("Conto Tesoreria")))
                .andExpect(jsonPath("$.postale", is(false)))
                .andExpect(jsonPath("$.abilitato", is(true)));
    }

    @Test
    void getUnknownReturns404() throws Exception {
        mvc.perform(get("/domini/" + ID_DOMINIO + "/contiAccredito/IT60X0542811101000000000099")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getWithInvalidIbanInPathReturns400() throws Exception {
        mvc.perform(get("/domini/" + ID_DOMINIO + "/contiAccredito/NONVALIDO")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    // --- Create ---

    @Test
    void createReturns201AndPersists() throws Exception {
        String body = """
                {"ibanAccredito":"IT60X0542811101000000000010","postale":false,"abilitato":true,
                 "descrizione":"Conto Nuovo","bic":"UNCRITMM","intestatario":"Comune Alfa"}""";
        mvc.perform(post("/domini/" + ID_DOMINIO + "/contiAccredito").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/contiAccredito/IT60X0542811101000000000010")))
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.ibanAccredito", is("IT60X0542811101000000000010")))
                .andExpect(jsonPath("$.bic", is("UNCRITMM")));

        IbanAccredito created = ibanRepository
                .findByDominio_IdAndCodIban(dominio.getId(), "IT60X0542811101000000000010").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(created.getIntestatario()).isEqualTo("Comune Alfa");
    }

    @Test
    void createDuplicateSameDominioReturns409() throws Exception {
        String body = """
                {"ibanAccredito":"%s","postale":false,"abilitato":true}""".formatted(IBAN_A);
        mvc.perform(post("/domini/" + ID_DOMINIO + "/contiAccredito").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void createSameIbanOnDifferentDominioSucceeds() throws Exception {
        // unicita' e' per (cod_iban, id_dominio): lo stesso IBAN su un dominio diverso e' lecito.
        String body = """
                {"ibanAccredito":"%s","postale":false,"abilitato":true}""".formatted(IBAN_A);
        mvc.perform(post("/domini/" + ID_DOMINIO_2 + "/contiAccredito").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void createUnknownDominioReturns404() throws Exception {
        String body = """
                {"ibanAccredito":"IT60X0542811101000000000010","postale":false,"abilitato":true}""";
        mvc.perform(post("/domini/99999999999/contiAccredito").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void createMissingPostaleReturns400() throws Exception {
        String body = """
                {"ibanAccredito":"IT60X0542811101000000000010","abilitato":true}""";
        mvc.perform(post("/domini/" + ID_DOMINIO + "/contiAccredito").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createInvalidIbanReturns400() throws Exception {
        String body = """
                {"ibanAccredito":"NONVALIDO","postale":false,"abilitato":true}""";
        mvc.perform(post("/domini/" + ID_DOMINIO + "/contiAccredito").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWritesAudit() throws Exception {
        long before = countAudit(ContoAccreditoService.AZIONE_AUDIT_CREATE);
        String body = """
                {"ibanAccredito":"IT60X0542811101000000000020","postale":false,"abilitato":true}""";
        mvc.perform(post("/domini/" + ID_DOMINIO + "/contiAccredito").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        org.assertj.core.api.Assertions.assertThat(countAudit(ContoAccreditoService.AZIONE_AUDIT_CREATE))
                .isEqualTo(before + 1);
    }

    // --- Replace ---

    @Test
    void replaceUpdates() throws Exception {
        String etag = currentEtag(IBAN_A);
        String body = """
                {"postale":true,"abilitato":false,"descrizione":"Conto Tesoreria Agg"}""";
        String newEtag = mvc.perform(put("/domini/" + ID_DOMINIO + "/contiAccredito/" + IBAN_A)
                        .with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descrizione", is("Conto Tesoreria Agg")))
                .andExpect(jsonPath("$.postale", is(true)))
                .andExpect(jsonPath("$.abilitato", is(false)))
                .andReturn().getResponse().getHeader("ETag");
        org.assertj.core.api.Assertions.assertThat(newEtag).isNotEqualTo(etag);
    }

    @Test
    void replaceWithoutIfMatchReturns428() throws Exception {
        String body = """
                {"postale":false,"abilitato":true}""";
        mvc.perform(put("/domini/" + ID_DOMINIO + "/contiAccredito/" + IBAN_A).with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPreconditionRequired());
    }

    @Test
    void replaceWithWrongIfMatchReturns412() throws Exception {
        String body = """
                {"postale":false,"abilitato":true}""";
        mvc.perform(put("/domini/" + ID_DOMINIO + "/contiAccredito/" + IBAN_A).with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", "\"deadbeef\"")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void replaceUnknownReturns404() throws Exception {
        String body = """
                {"postale":false,"abilitato":true}""";
        mvc.perform(put("/domini/" + ID_DOMINIO + "/contiAccredito/IT60X0542811101000000000099")
                        .with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", "*")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    // --- Patch ---

    @Test
    void patchDescrizione() throws Exception {
        String etag = currentEtag(IBAN_A);
        String p = """
                [{"op":"replace","path":"/descrizione","value":"Conto Patchato"}]""";
        mvc.perform(patch("/domini/" + ID_DOMINIO + "/contiAccredito/" + IBAN_A).with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(JSON_PATCH).content(p))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descrizione", is("Conto Patchato")))
                .andExpect(jsonPath("$.abilitato", is(true)));
    }

    @Test
    void patchChangingIbanReturns400() throws Exception {
        String etag = currentEtag(IBAN_A);
        String p = """
                [{"op":"replace","path":"/ibanAccredito","value":"IT60X0542811101000000000077"}]""";
        mvc.perform(patch("/domini/" + ID_DOMINIO + "/contiAccredito/" + IBAN_A).with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(JSON_PATCH).content(p))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("ibanAccredito")));
    }

    @Test
    void patchDescrizioneTooLongReturns400() throws Exception {
        String etag = currentEtag(IBAN_A);
        String tooLong = "X".repeat(256);
        String p = "[{\"op\":\"replace\",\"path\":\"/descrizione\",\"value\":\"" + tooLong + "\"}]";
        mvc.perform(patch("/domini/" + ID_DOMINIO + "/contiAccredito/" + IBAN_A).with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(JSON_PATCH).content(p))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"));
    }

    private String currentEtag(String iban) throws Exception {
        return mvc.perform(get("/domini/" + ID_DOMINIO + "/contiAccredito/" + iban)
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
