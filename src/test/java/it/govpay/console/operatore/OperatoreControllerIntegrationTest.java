package it.govpay.console.operatore;

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
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.TipoVersamento;
import it.govpay.console.entity.Utenza;
import it.govpay.console.repository.AclRepository;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.repository.GpAuditRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.TipoVersamentoRepository;
import it.govpay.console.repository.UtenzaRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OperatoreControllerIntegrationTest {

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
    private DominioRepository dominioRepository;
    @Autowired
    private TipoVersamentoRepository tipoVersamentoRepository;
    @Autowired
    private AclRepository aclRepository;
    @Autowired
    private GpAuditRepository gpAuditRepository;

    @BeforeEach
    void setup() {
        // Operatore autenticato (compare anch'esso nelle liste /operatori).
        newUtenzaOperatore(PRINCIPAL, "Operatore Uno", true, encoder.encode(PASSWORD));

        newDominio("12345678901", "Comune Alfa");
        newTipoVersamento("TARI", "Tassa Rifiuti");
        newRuoloCatalogo("OPERATORE");

        newUtenzaOperatore("op-alfa", "Alfa", true, null);
        newUtenzaOperatore("op-beta", "Beta", false, null);
        newUtenzaOperatore("op-gamma", "Gamma", true, null);
    }

    private void newUtenzaOperatore(String principal, String nome, boolean abilitato, String password) {
        Utenza u = new Utenza();
        u.setPrincipal(principal);
        u.setPrincipalOriginale(principal);
        u.setAbilitato(abilitato);
        u.setAutorizzazioneDominiStar(true);
        u.setAutorizzazioneTipiVersStar(true);
        u.setRuoli("OPERATORE");
        u.setPassword(password);
        utenzaRepository.save(u);

        Operatore op = new Operatore();
        op.setNome(nome);
        op.setIdUtenza(u.getId());
        operatoreRepository.save(op);
    }

    private void newDominio(String cod, String ragione) {
        Dominio d = new Dominio();
        d.setCodDominio(cod);
        d.setRagioneSociale(ragione);
        d.setAuxDigit(0);
        dominioRepository.save(d);
    }

    private void newTipoVersamento(String cod, String descrizione) {
        TipoVersamento t = new TipoVersamento();
        t.setCodTipoVersamento(cod);
        t.setDescrizione(descrizione);
        tipoVersamentoRepository.save(t);
    }

    private void newRuoloCatalogo(String ruolo) {
        Acl acl = new Acl();
        acl.setRuolo(ruolo);
        acl.setServizio("Pendenze");
        acl.setDiritti("R");
        acl.setIdUtenza(null);
        aclRepository.save(acl);
    }

    // --- List ---

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/operatori"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void listIncludesSeededAndAuthOperator() throws Exception {
        mvc.perform(get("/operatori").param("total", "true").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalResults", is(4)));
    }

    @Test
    void filterByPrincipalPartial() throws Exception {
        mvc.perform(get("/operatori").param("principal", "op-").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(3)));
    }

    @Test
    void filterByNomePartial() throws Exception {
        mvc.perform(get("/operatori").param("nome", "Beta").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].principal", is("op-beta")));
    }

    @Test
    void filterByAbilitato() throws Exception {
        mvc.perform(get("/operatori").param("abilitato", "false").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].principal", is("op-beta")));
    }

    @Test
    void sortByPrincipalAscThenDesc() throws Exception {
        mvc.perform(get("/operatori").param("principal", "op-").param("sort", "principal")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].principal", contains("op-alfa", "op-beta", "op-gamma")));
        mvc.perform(get("/operatori").param("principal", "op-").param("sort", "-principal")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].principal", contains("op-gamma", "op-beta", "op-alfa")));
    }

    @Test
    void unknownSortFieldReturns400() throws Exception {
        mvc.perform(get("/operatori").param("sort", "-bogus").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("bogus")));
    }

    // --- Get ---

    @Test
    void getExistingReturnsDetailWithEtag() throws Exception {
        mvc.perform(get("/operatori/op-alfa").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.principal", is("op-alfa")))
                .andExpect(jsonPath("$.nome", is("Alfa")))
                .andExpect(jsonPath("$.abilitato", is(true)))
                .andExpect(jsonPath("$.domini[0].idDominio", is("*")))
                .andExpect(jsonPath("$.ruoli[0].id", is("OPERATORE")));
    }

    @Test
    void getUnknownReturns404() throws Exception {
        mvc.perform(get("/operatori/op-none").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    // --- Create ---

    @Test
    void createReturns201WithLocationAndEtag() throws Exception {
        String body = """
                {"principal":"op-new","nome":"Nuovo","abilitato":true,
                 "domini":[{"idDominio":"12345678901"}],
                 "tipiPendenza":[{"idTipoPendenza":"TARI"}],
                 "ruoli":[{"id":"OPERATORE"}],
                 "acl":[{"servizio":"Pendenze","autorizzazioni":["R","W"]}]}""";
        mvc.perform(post("/operatori").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/operatori/op-new")))
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.principal", is("op-new")))
                .andExpect(jsonPath("$.nome", is("Nuovo")))
                .andExpect(jsonPath("$.domini[0].idDominio", is("12345678901")))
                .andExpect(jsonPath("$.tipiPendenza[0].idTipoPendenza", is("TARI")))
                .andExpect(jsonPath("$.acl[0].autorizzazioni", containsInAnyOrder("R", "W")));
    }

    @Test
    void createDuplicatePrincipalReturns409() throws Exception {
        String body = """
                {"principal":"op-alfa","nome":"Dup","abilitato":true}""";
        mvc.perform(post("/operatori").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void createDuplicatePrincipalOriginaleReturns409EvenIfPrincipalDiffers() throws Exception {
        // Utenza "migrata" con principal != principalOriginale: il valore esposto
        // dall'API (principalOriginale) e' gia' in uso → 409, per non rendere
        // ambigua la GET /operatori/{principal}.
        Utenza diverging = new Utenza();
        diverging.setPrincipal("CN=op-div,OU=cert");
        diverging.setPrincipalOriginale("op-div");
        diverging.setAbilitato(true);
        diverging.setAutorizzazioneDominiStar(false);
        diverging.setAutorizzazioneTipiVersStar(false);
        utenzaRepository.save(diverging);

        String body = """
                {"principal":"op-div","nome":"Div","abilitato":true}""";
        mvc.perform(post("/operatori").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void createWithMissingNomeReturns400() throws Exception {
        String body = """
                {"principal":"op-nn","abilitato":true}""";
        mvc.perform(post("/operatori").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWithUnknownDominioRefReturns404() throws Exception {
        String body = """
                {"principal":"op-badd","nome":"Bad","abilitato":true,
                 "domini":[{"idDominio":"99999999999"}]}""";
        mvc.perform(post("/operatori").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail", containsString("99999999999")));
    }

    @Test
    void createWithAutodeterminazioneReturns404() throws Exception {
        // Gli operatori non supportano l'autodeterminazione: e' un tipo pendenza inesistente.
        String body = """
                {"principal":"op-auto","nome":"Auto","abilitato":true,
                 "tipiPendenza":[{"idTipoPendenza":"autodeterminazione"}]}""";
        mvc.perform(post("/operatori").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void createWritesAudit() throws Exception {
        long before = countAudit("OPERATORE_CREATE");
        String body = """
                {"principal":"op-aud","nome":"Aud","abilitato":true}""";
        mvc.perform(post("/operatori").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        org.assertj.core.api.Assertions.assertThat(countAudit("OPERATORE_CREATE")).isEqualTo(before + 1);
    }

    // --- Replace (PUT) ---

    @Test
    void replaceWithCorrectIfMatchSucceeds() throws Exception {
        String etag = currentEtag("op-alfa");
        String body = """
                {"nome":"Alfa Aggiornato","abilitato":false,
                 "domini":[{"idDominio":"12345678901"}]}""";
        String newEtag = mvc.perform(put("/operatori/op-alfa").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome", is("Alfa Aggiornato")))
                .andExpect(jsonPath("$.abilitato", is(false)))
                .andExpect(jsonPath("$.domini[0].idDominio", is("12345678901")))
                .andReturn().getResponse().getHeader("ETag");
        org.assertj.core.api.Assertions.assertThat(newEtag).isNotEqualTo(etag);
    }

    @Test
    void replaceWithWrongIfMatchReturns412() throws Exception {
        String body = """
                {"nome":"X","abilitato":true}""";
        mvc.perform(put("/operatori/op-alfa").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", "\"deadbeef\"")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void replaceWithoutIfMatchReturns428() throws Exception {
        String body = """
                {"nome":"X","abilitato":true}""";
        mvc.perform(put("/operatori/op-alfa").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPreconditionRequired());
    }

    @Test
    void replaceUnknownReturns404() throws Exception {
        String body = """
                {"nome":"X","abilitato":true}""";
        mvc.perform(put("/operatori/op-none").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", "*")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    // --- Patch (JSON Patch) ---

    @Test
    void patchReplaceNomeSucceeds() throws Exception {
        String etag = currentEtag("op-alfa");
        String patch = """
                [{"op":"replace","path":"/nome","value":"Alfa Patchato"}]""";
        mvc.perform(patch("/operatori/op-alfa").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(JSON_PATCH).content(patch))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome", is("Alfa Patchato")))
                .andExpect(jsonPath("$.principal", is("op-alfa")));
    }

    @Test
    void patchChangingPrincipalReturns400() throws Exception {
        String etag = currentEtag("op-alfa");
        String patch = """
                [{"op":"replace","path":"/principal","value":"op-xxx"}]""";
        mvc.perform(patch("/operatori/op-alfa").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(JSON_PATCH).content(patch))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("principal")));
    }

    @Test
    void patchWithWrongIfMatchReturns412() throws Exception {
        String patch = """
                [{"op":"replace","path":"/nome","value":"X"}]""";
        mvc.perform(patch("/operatori/op-alfa").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", "\"deadbeef\"")
                        .contentType(JSON_PATCH).content(patch))
                .andExpect(status().isPreconditionFailed());
    }

    // --- Password (PUT /password) ---

    @Test
    void putPasswordValidReturns204AndStoresHash() throws Exception {
        grantScrittura("Anagrafica Ruoli");
        String body = """
                {"nuovaPassword":"NuovaPassword01"}""";
        mvc.perform(put("/operatori/op-alfa/password").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());
        String hash = utenzaRepository.findByPrincipal("op-alfa").orElseThrow().getPassword();
        org.assertj.core.api.Assertions.assertThat(hash).startsWith("$6$");
        org.assertj.core.api.Assertions.assertThat(encoder.matches("NuovaPassword01", hash)).isTrue();
    }

    @Test
    void putPasswordWithDirittoViaRuoloReturns204() throws Exception {
        // il diritto arriva dalla ACL di definizione del ruolo OPERATORE
        // (id_utenza IS NULL), non da una ACL diretta dell'utenza
        Acl acl = new Acl();
        acl.setRuolo("OPERATORE");
        acl.setServizio("Anagrafica Ruoli");
        acl.setDiritti("W");
        acl.setIdUtenza(null);
        aclRepository.save(acl);

        String body = """
                {"nuovaPassword":"NuovaPassword01"}""";
        mvc.perform(put("/operatori/op-alfa/password").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());
    }

    @Test
    void putPasswordViolatingPolicyReturns400WithDetail() throws Exception {
        grantScrittura("Anagrafica Ruoli");
        String body = """
                {"nuovaPassword":"corta1"}""";
        mvc.perform(put("/operatori/op-alfa/password").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.detail", containsString("almeno 8 caratteri")))
                .andExpect(jsonPath("$.detail", containsString("almeno una lettera maiuscola")));
    }

    @Test
    void putPasswordMissingFieldReturns400() throws Exception {
        grantScrittura("Anagrafica Ruoli");
        mvc.perform(put("/operatori/op-alfa/password").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void putPasswordWithoutDirittoReturns403() throws Exception {
        String body = """
                {"nuovaPassword":"NuovaPassword01"}""";
        mvc.perform(put("/operatori/op-alfa/password").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.detail", containsString("Anagrafica Ruoli")));
    }

    @Test
    void putPasswordUnknownOperatoreReturns404() throws Exception {
        grantScrittura("Anagrafica Ruoli");
        String body = """
                {"nuovaPassword":"NuovaPassword01"}""";
        mvc.perform(put("/operatori/op-none/password").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void putPasswordWritesAudit() throws Exception {
        grantScrittura("Anagrafica Ruoli");
        long before = countAudit("OPERATORE_CAMBIO_PASSWORD");
        String body = """
                {"nuovaPassword":"NuovaPassword01"}""";
        mvc.perform(put("/operatori/op-alfa/password").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());
        org.assertj.core.api.Assertions.assertThat(countAudit("OPERATORE_CAMBIO_PASSWORD")).isEqualTo(before + 1);
    }

    private void grantScrittura(String servizio) {
        Utenza utenza = utenzaRepository.findByPrincipal(PRINCIPAL).orElseThrow();
        Acl acl = new Acl();
        acl.setIdUtenza(utenza.getId());
        acl.setServizio(servizio);
        acl.setDiritti("RW");
        aclRepository.save(acl);
    }

    private String currentEtag(String principal) throws Exception {
        return mvc.perform(get("/operatori/" + principal).with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("ETag");
    }

    private long countAudit(String azione) {
        return gpAuditRepository.findAll().stream()
                .filter(a -> azione.equals(a.getTipoOggetto()))
                .count();
    }
}
