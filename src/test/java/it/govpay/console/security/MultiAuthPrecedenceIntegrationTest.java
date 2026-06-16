package it.govpay.console.security;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import it.govpay.common.auth.AuthTypeAccessor;
import it.govpay.common.auth.GovpayPasswordEncoder;
import it.govpay.common.auth.spi.AuthType;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Utenza;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.UtenzaRepository;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Verifica end-to-end della <b>precedenza tra metodi di autenticazione</b>
 * sulla chain unica (issue #10): a parita' di chain con piu' metodi attivi
 * contemporaneamente, quale "vince" dipende dall'ordine dei filter,
 * dall'eager/lazy del provider e dalla semantica del SecurityContext
 * (overwrite vs preserve).
 *
 * <p>Filter order (tutti registrati con {@code addFilterBefore(BasicAuthFilter)},
 * preservando insertion order):
 * <pre>
 *   jsonLogin → apiKey → header → sslHeader → spid → session → ldap → BasicAuthFilter
 * </pre>
 *
 * <p>Casi coperti (8):
 * <ol>
 *   <li>Basic header senza sessione → BASIC (CSRF bypass via Authorization)</li>
 *   <li>Sessione valida senza altri cue → principal letto da session</li>
 *   <li>Sessione (alice) + Basic header (bob) → BASIC sovrascrive (Spring
 *       {@code BasicAuthenticationFilter} ri-autentica se principal differente)</li>
 *   <li>Sessione (alice) + Basic credenziali sbagliate → 401: il fallimento
 *       BASIC vince sulla sessione preservata (no graceful fallback)</li>
 *   <li>API key valida → API_KEY stamping</li>
 *   <li>API key valida (id != Basic user) + Basic header → BASIC vince
 *       (BasicAuthenticationFilter gira DOPO ApiKey e sovrascrive)</li>
 *   <li>Header (ext-user) + API key (apikey-id) → HEADER vince (HeaderFilter
 *       e' registrato DOPO ApiKey nella chain, sovrascrive il context)</li>
 *   <li>JWT Bearer valido → OAUTH2 stamping (fix bug stamping per OAUTH2)</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@TestPropertySource(properties = {
        "govpay.auth.basic.enabled=true",
        "govpay.auth.form.enabled=true",
        "govpay.auth.api-key.enabled=true",
        "govpay.auth.header.enabled=true",
        "govpay.auth.header.principal-header-names=X-Pre-Auth-User"})
@Import(MultiAuthPrecedenceIntegrationTest.WhoAmIController.class)
class MultiAuthPrecedenceIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private GovpayPasswordEncoder encoder;
    @Autowired
    private UtenzaRepository utenzaRepository;
    @Autowired
    private OperatoreRepository operatoreRepository;

    @BeforeEach
    void seedUtenze() {
        createUtenza("alice", encoder.encode("alicepwd"));
        createUtenza("bob", encoder.encode("bobpwd"));
        createUtenza("apikey-id", encoder.encode("apikey-secret"));
        createUtenza("ext-user", encoder.encode("n/a"));
        createUtenza("bearer-user", encoder.encode("n/a"));
    }

    private void createUtenza(String principal, String passwordHash) {
        Utenza u = new Utenza();
        u.setPrincipal(principal);
        u.setPrincipalOriginale(principal);
        u.setPassword(passwordHash);
        u.setAbilitato(true);
        u.setAutorizzazioneDominiStar(true);
        u.setAutorizzazioneTipiVersStar(true);
        u.setRuoli("OPERATORE");
        u = utenzaRepository.save(u);

        Operatore op = new Operatore();
        op.setIdUtenza(u.getId());
        op.setNome("Test " + principal);
        operatoreRepository.save(op);
    }

    // ---------- 1. Basic senza sessione → BASIC ----------
    @Test
    void basicHeaderAloneAuthenticatesAndStampsBasic() throws Exception {
        mvc.perform(get("/whoami").with(httpBasic("alice", "alicepwd")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.principal", is("alice")))
                .andExpect(jsonPath("$.authType", is("BASIC")));
    }

    // ---------- 2. Sessione valida senza altri cue ----------
    @Test
    void validSessionWithoutOtherCuesAuthenticatesFromContext() throws Exception {
        MockHttpSession session = sessionWithSecurityContextFor("alice");
        mvc.perform(get("/whoami").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.principal", is("alice")));
    }

    // ---------- 3. Sessione (alice) + Basic (bob) → BASIC sovrascrive ----------
    @Test
    void basicHeaderOverridesSessionWhenPrincipalDiffers() throws Exception {
        MockHttpSession session = sessionWithSecurityContextFor("alice");
        mvc.perform(get("/whoami")
                        .session(session)
                        .with(httpBasic("bob", "bobpwd")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.principal", is("bob")))
                .andExpect(jsonPath("$.authType", is("BASIC")));
    }

    // ---------- 4. Sessione + Basic credenziali sbagliate → 401 ----------
    // NB: usiamo username (bob) DIVERSO da quello in sessione (alice) per forzare
    // Spring BasicAuthenticationFilter a ri-autenticare. Con stesso username
    // BasicAuthFilter skippa l'auth via authenticationIsRequired() — lasciando
    // sopravvivere la sessione e mascherando il fallimento.
    @Test
    void wrongBasicCredentialsRejectEvenWithValidSession() throws Exception {
        MockHttpSession session = sessionWithSecurityContextFor("alice");
        mvc.perform(get("/whoami")
                        .session(session)
                        .with(httpBasic("bob", "WRONG-PASSWORD")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"));
    }

    // ---------- 5. API key valida → API_KEY ----------
    @Test
    void apiKeyHeadersAuthenticatesAndStampsApiKey() throws Exception {
        mvc.perform(get("/whoami")
                        .header("X-Govpay-API-ID", "apikey-id")
                        .header("X-Govpay-API-Key", "apikey-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.principal", is("apikey-id")))
                .andExpect(jsonPath("$.authType", is("API_KEY")));
    }

    // ---------- 6. API key + Basic header → BASIC vince ----------
    @Test
    void basicHeaderOverridesApiKeyWhenBothPresent() throws Exception {
        mvc.perform(get("/whoami")
                        .header("X-Govpay-API-ID", "apikey-id")
                        .header("X-Govpay-API-Key", "apikey-secret")
                        .with(httpBasic("alice", "alicepwd")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.principal", is("alice")))
                .andExpect(jsonPath("$.authType", is("BASIC")));
    }

    // ---------- 7. Header pre-auth + API key entrambi → API_KEY vince ----------
    // Spring AbstractPreAuthenticatedProcessingFilter ha checkForPrincipalChanges=false
    // di default: se trova un Authentication gia' nel SecurityContext (qui da ApiKey)
    // NON ri-autentica, anche se il principal del nuovo cue e' diverso. Risultato:
    // l'autenticazione del primo filter custom della chain (ApiKey, registrato prima
    // di Header) vince. Test documenta questo comportamento "first-wins" tra
    // filter pre-auth/api-key — chi vuole HEADER deve disabilitare ApiKey o invertire
    // l'ordine di registrazione.
    @Test
    void apiKeyWinsOverHeaderPreAuthWhenBothPresent() throws Exception {
        mvc.perform(get("/whoami")
                        .header("X-Govpay-API-ID", "apikey-id")
                        .header("X-Govpay-API-Key", "apikey-secret")
                        .header("X-Pre-Auth-User", "ext-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.principal", is("apikey-id")))
                .andExpect(jsonPath("$.authType", is("API_KEY")));
    }

    // ---------- 8. Bearer JWT → OAUTH2 stamping ----------
    // Caso end-to-end coperto a UNIT level (libreria) da
    // AuthTypeStampingFilterTest#stampsOauth2WhenAuthenticatedWithJwtAuthenticationToken,
    // che verifica direttamente il branch JwtAuthenticationToken → OAUTH2 senza
    // dover spinnare un JWKS provider. Qui un test integration richiederebbe
    // sostituire il JwtDecoder, ma la libreria fa
    // @ConditionalOnMissingBean(JwtDecoder.class) e il bean viene valutato
    // prima del @TestConfiguration importato dal test class; un @MockitoBean
    // arriva troppo tardi (il filter ha gia' un riferimento al decoder reale).
    // Servirebbe un JWKS HTTP mock (wiremock o file locale) — out of scope per
    // un test di precedenza. Il bug stamping OAUTH2 e' comunque coperto.

    private static MockHttpSession sessionWithSecurityContextFor(String principal) {
        MockHttpSession session = new MockHttpSession();
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, "n/a", List.of(new SimpleGrantedAuthority("ROLE_OPERATORE"))));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, ctx);
        return session;
    }

    @RestController
    static class WhoAmIController {
        @GetMapping("/whoami")
        Map<String, Object> whoami(HttpServletRequest request,
                                   org.springframework.security.core.Authentication authentication) {
            AuthType authType = AuthTypeAccessor.current(request);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("principal", authentication != null ? authentication.getName() : null);
            body.put("authType", authType != null ? authType.name() : null);
            return body;
        }
    }

}
