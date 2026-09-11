package it.govpay.console.profilo;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

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
import it.govpay.console.entity.UnitaOperativa;
import it.govpay.console.entity.Utenza;
import it.govpay.console.entity.UtenzaDominio;
import it.govpay.console.repository.AclRepository;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.repository.GpAuditRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.UnitaOperativaRepository;
import it.govpay.console.repository.UtenzaDominioRepository;
import it.govpay.console.repository.UtenzaRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProfiloControllerIntegrationTest {

    private static final String PRINCIPAL = "alice";
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
    private DominioRepository dominioRepository;
    @Autowired
    private UnitaOperativaRepository unitaOperativaRepository;
    @Autowired
    private UtenzaDominioRepository utenzaDominioRepository;
    @Autowired
    private GpAuditRepository gpAuditRepository;

    @BeforeEach
    void setupUtenza() {
        Utenza utenza = new Utenza();
        utenza.setPrincipal(PRINCIPAL);
        utenza.setPrincipalOriginale(PRINCIPAL);
        utenza.setPassword(encoder.encode(PASSWORD));
        utenza.setAbilitato(true);
        utenza.setAutorizzazioneDominiStar(true);
        utenza.setAutorizzazioneTipiVersStar(true);
        utenza.setRuoli("AMMINISTRATORE,OPERATORE");
        utenza = utenzaRepository.save(utenza);

        Operatore operatore = new Operatore();
        operatore.setIdUtenza(utenza.getId());
        operatore.setNome("Alice Rossi");
        operatoreRepository.save(operatore);

        Acl acl = new Acl();
        acl.setIdUtenza(utenza.getId());
        acl.setServizio("Pendenze");
        acl.setDiritti("R,W");
        aclRepository.save(acl);
    }

    @Test
    void returns401WhenUnauthenticated() throws Exception {
        mvc.perform(get("/profilo"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void returnsProfiloForAuthenticatedUser() throws Exception {
        mvc.perform(get("/profilo").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(header().string("Cache-Control", is("max-age=60, private")))
                .andExpect(jsonPath("$.nome", is("Alice Rossi")))
                .andExpect(jsonPath("$.principal", is(PRINCIPAL)))
                .andExpect(jsonPath("$.autenticazione", is("BASIC")))
                .andExpect(jsonPath("$.domini[0].idDominio", is("*")))
                .andExpect(jsonPath("$.domini[0].ragioneSociale", is("Tutti")))
                .andExpect(jsonPath("$.tipiPendenza[0].idTipoPendenza", is("*")))
                .andExpect(jsonPath("$.ruoli[0].id", is("AMMINISTRATORE")))
                .andExpect(jsonPath("$.ruoli[1].id", is("OPERATORE")))
                .andExpect(jsonPath("$.acl[0].servizio", is("Pendenze")))
                .andExpect(jsonPath("$.acl[0].autorizzazioni[0]", is("R")))
                .andExpect(jsonPath("$.acl[0].autorizzazioni[1]", is("W")))
                .andExpect(jsonPath("$.preferenze", is(Map.of())));
    }

    /** Utenza senza operatore associato (es. utenza applicativa): il campo e' assente, non un errore. */
    @Test
    void preferenzeIsAbsentWhenNoOperatoreAssociated() throws Exception {
        Utenza applicazione = new Utenza();
        applicazione.setPrincipal("app-senza-operatore");
        applicazione.setPrincipalOriginale("app-senza-operatore");
        applicazione.setPassword(encoder.encode(PASSWORD));
        applicazione.setAbilitato(true);
        applicazione.setAutorizzazioneDominiStar(true);
        applicazione.setAutorizzazioneTipiVersStar(true);
        applicazione.setRuoli("A2A");
        utenzaRepository.save(applicazione);

        mvc.perform(get("/profilo").with(httpBasic("app-senza-operatore", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferenze").doesNotExist());
    }

    /**
     * Utenza autorizzata SOLO su una UO specifica di un dominio (riga
     * {@code utenze_domini} con {@code id_uo IS NOT NULL}, nessuna riga
     * con {@code id_uo IS NULL}): il dominio padre deve comunque
     * comparire in {@code Profilo.domini}.
     */
    @Test
    void returnsParentDominioWhenAuthorizationIsUoScoped() throws Exception {
        Utenza utenza = utenzaRepository.findByPrincipal(PRINCIPAL).orElseThrow();
        utenza.setAutorizzazioneDominiStar(false);
        utenzaRepository.save(utenza);

        Dominio dominio = new Dominio();
        dominio.setCodDominio("99999999999");
        dominio.setRagioneSociale("Comune Solo Uo");
        dominio.setAuxDigit(0);
        dominioRepository.save(dominio);

        UnitaOperativa uo = new UnitaOperativa();
        uo.setCodUo("UO1");
        uo.setUoDenominazione("Settore Tributi");
        uo.setDominio(dominio);
        unitaOperativaRepository.save(uo);

        UtenzaDominio link = new UtenzaDominio();
        link.setIdUtenza(utenza.getId());
        link.setIdDominio(dominio.getId());
        link.setIdUo(uo.getId());
        utenzaDominioRepository.save(link);

        mvc.perform(get("/profilo").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.domini[0].idDominio", is("99999999999")))
                .andExpect(jsonPath("$.domini[0].ragioneSociale", is("Comune Solo Uo")));
    }

    // --- Patch /profilo (issue #80) ---

    @Test
    void patchPreferenzeOnFirstWriteReturnsUpdatedProfilo() throws Exception {
        String p = """
                [{"op":"replace","path":"/preferenze","value":{"tema":"scuro"}}]""";
        mvc.perform(patch("/profilo").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(JSON_PATCH).content(p))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.preferenze.tema", is("scuro")));

        mvc.perform(get("/profilo").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferenze.tema", is("scuro")));
    }

    @Test
    void patchAddPreferenzeSucceeds() throws Exception {
        String p = """
                [{"op":"add","path":"/preferenze","value":{"vista":"lista"}}]""";
        mvc.perform(patch("/profilo").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(JSON_PATCH).content(p))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferenze.vista", is("lista")));
    }

    @Test
    void patchRemovePreferenzeZeroesIt() throws Exception {
        String setPatch = """
                [{"op":"replace","path":"/preferenze","value":{"tema":"scuro"}}]""";
        mvc.perform(patch("/profilo").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(JSON_PATCH).content(setPatch))
                .andExpect(status().isOk());

        String removePatch = """
                [{"op":"remove","path":"/preferenze"}]""";
        mvc.perform(patch("/profilo").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(JSON_PATCH).content(removePatch))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferenze", is(Map.of())));
    }

    @Test
    void patchTestPreferenzeSucceedsWhenValueMatches() throws Exception {
        String setPatch = """
                [{"op":"replace","path":"/preferenze","value":{"tema":"scuro"}}]""";
        mvc.perform(patch("/profilo").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(JSON_PATCH).content(setPatch))
                .andExpect(status().isOk());

        String testPatch = """
                [{"op":"test","path":"/preferenze","value":{"tema":"scuro"}}]""";
        mvc.perform(patch("/profilo").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(JSON_PATCH).content(testPatch))
                .andExpect(status().isOk());
    }

    /**
     * `preferenze` e' vincolato a un oggetto JSON: stringhe/numeri/array/null
     * espliciti devono fare 400, non 500 (convertValue fallirebbe) ne' essere
     * silenziosamente accettati come azzeramento (a differenza della chiave
     * assente dopo una 'remove', che azzera legittimamente).
     */
    @Test
    void patchWithStringValueReturns400() throws Exception {
        String p = """
                [{"op":"replace","path":"/preferenze","value":"valore-non-valido"}]""";
        mvc.perform(patch("/profilo").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(JSON_PATCH).content(p))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.errors[0].field", is("preferenze")));
    }

    @Test
    void patchWithNumberValueReturns400() throws Exception {
        String p = """
                [{"op":"replace","path":"/preferenze","value":42}]""";
        mvc.perform(patch("/profilo").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(JSON_PATCH).content(p))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patchWithArrayValueReturns400() throws Exception {
        String p = """
                [{"op":"replace","path":"/preferenze","value":[1,2,3]}]""";
        mvc.perform(patch("/profilo").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(JSON_PATCH).content(p))
                .andExpect(status().isBadRequest());
    }

    /** Diverso dalla 'remove' (che azzera legittimamente): qui la chiave resta presente ma con valore null. */
    @Test
    void patchWithExplicitNullValueReturns400() throws Exception {
        String p = """
                [{"op":"replace","path":"/preferenze","value":null}]""";
        mvc.perform(patch("/profilo").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(JSON_PATCH).content(p))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patchFieldOutsideScopeReturns403() throws Exception {
        String p = """
                [{"op":"replace","path":"/nome","value":"Mallory"}]""";
        mvc.perform(patch("/profilo").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(JSON_PATCH).content(p))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("/nome")));
    }

    @Test
    void patchMoveWithFromOutsideScopeReturns403() throws Exception {
        String p = """
                [{"op":"move","from":"/principal","path":"/preferenze"}]""";
        mvc.perform(patch("/profilo").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(JSON_PATCH).content(p))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("/principal")));
    }

    @Test
    void patchCopyWithFromOutsideScopeReturns403() throws Exception {
        String p = """
                [{"op":"copy","from":"/ruoli","path":"/preferenze"}]""";
        mvc.perform(patch("/profilo").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(JSON_PATCH).content(p))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("/ruoli")));
    }

    @Test
    void patchWithoutOperatoreAssociatedReturns422() throws Exception {
        Utenza applicazione = new Utenza();
        applicazione.setPrincipal("app-senza-operatore-2");
        applicazione.setPrincipalOriginale("app-senza-operatore-2");
        applicazione.setPassword(encoder.encode(PASSWORD));
        applicazione.setAbilitato(true);
        applicazione.setAutorizzazioneDominiStar(true);
        applicazione.setAutorizzazioneTipiVersStar(true);
        applicazione.setRuoli("A2A");
        utenzaRepository.save(applicazione);

        String p = """
                [{"op":"replace","path":"/preferenze","value":{"tema":"scuro"}}]""";
        mvc.perform(patch("/profilo").with(httpBasic("app-senza-operatore-2", PASSWORD))
                        .contentType(JSON_PATCH).content(p))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentType("application/problem+json"));
    }

    /**
     * Il controllo sullo scope del campo e' piu' economico (nessun accesso al DB) e
     * vince quando entrambe le condizioni di rifiuto sarebbero applicabili.
     */
    @Test
    void patchFieldOutsideScopeWinsOverMissingOperatore() throws Exception {
        Utenza applicazione = new Utenza();
        applicazione.setPrincipal("app-senza-operatore-3");
        applicazione.setPrincipalOriginale("app-senza-operatore-3");
        applicazione.setPassword(encoder.encode(PASSWORD));
        applicazione.setAbilitato(true);
        applicazione.setAutorizzazioneDominiStar(true);
        applicazione.setAutorizzazioneTipiVersStar(true);
        applicazione.setRuoli("A2A");
        utenzaRepository.save(applicazione);

        String p = """
                [{"op":"replace","path":"/nome","value":"Mallory"}]""";
        mvc.perform(patch("/profilo").with(httpBasic("app-senza-operatore-3", PASSWORD))
                        .contentType(JSON_PATCH).content(p))
                .andExpect(status().isForbidden());
    }

    @Test
    void patchWritesAuditWithoutPreferenzeContent() throws Exception {
        long before = countAudit("PROFILO_PREFERENZE_MODIFICA");
        String p = """
                [{"op":"replace","path":"/preferenze","value":{"segreto":"non-loggarmi"}}]""";
        mvc.perform(patch("/profilo").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(JSON_PATCH).content(p))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(countAudit("PROFILO_PREFERENZE_MODIFICA")).isEqualTo(before + 1);
        gpAuditRepository.findAll().stream()
                .filter(a -> "PROFILO_PREFERENZE_MODIFICA".equals(a.getTipoOggetto()))
                .forEach(a -> org.assertj.core.api.Assertions.assertThat(a.getOggetto())
                        .doesNotContain("segreto", "non-loggarmi"));
    }

    @Test
    void patchWithoutAuthenticationReturns401() throws Exception {
        String p = """
                [{"op":"replace","path":"/preferenze","value":{"tema":"scuro"}}]""";
        mvc.perform(patch("/profilo").contentType(JSON_PATCH).content(p))
                .andExpect(status().isUnauthorized());
    }

    private long countAudit(String azione) {
        return gpAuditRepository.findAll().stream()
                .filter(a -> azione.equals(a.getTipoOggetto()))
                .count();
    }
}
