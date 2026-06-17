package it.govpay.console.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.common.auth.GovpayPasswordEncoder;
import it.govpay.common.auth.spi.AuthType;
import it.govpay.common.auth.spi.FailureReason;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Utenza;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.UtenzaRepository;
import jakarta.servlet.http.Cookie;

/**
 * Verifica end-to-end di {@code POST /auth/login} e {@code POST /auth/logout}:
 * <ul>
 *   <li>login con credenziali valide → 200 + body {@code Profilo} +
 *       cookie {@code JSESSIONID} + {@code XSRF-TOKEN};</li>
 *   <li>login con password errata / utenza disabilitata → 401
 *       {@code application/problem+json};</li>
 *   <li>login con body malformato → 400 {@code application/problem+json};</li>
 *   <li>logout con sessione valida e header CSRF → 204;</li>
 *   <li>logout senza sessione → 204 (idempotente; il CSRF e' condizionale
 *       e viene ignorato in assenza di session id).</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LoginLogoutIntegrationTest {

    private static final String PRINCIPAL = "alice";
    private static final String PASSWORD = "secret";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private GovpayPasswordEncoder encoder;
    @Autowired
    private UtenzaRepository utenzaRepository;
    @Autowired
    private OperatoreRepository operatoreRepository;
    @MockitoSpyBean
    private ConsoleAuthEventListener authEventListener;

    @BeforeEach
    void setupUtenza() {
        Utenza utenza = new Utenza();
        utenza.setPrincipal(PRINCIPAL);
        utenza.setPrincipalOriginale(PRINCIPAL);
        utenza.setPassword(encoder.encode(PASSWORD));
        utenza.setAbilitato(true);
        utenza.setAutorizzazioneDominiStar(true);
        utenza.setAutorizzazioneTipiVersStar(true);
        utenza.setRuoli("AMMINISTRATORE");
        utenza = utenzaRepository.save(utenza);

        Operatore operatore = new Operatore();
        operatore.setIdUtenza(utenza.getId());
        operatore.setNome("Alice Rossi");
        operatoreRepository.save(operatore);
    }

    @Test
    void loginWithValidCredentialsReturns200WithProfilo() throws Exception {
        MvcResult result = mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + PRINCIPAL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.nome", is("Alice Rossi")))
                .andExpect(jsonPath("$.principal", is(PRINCIPAL)))
                .andExpect(jsonPath("$.autenticazione", is("FORM")))
                .andExpect(jsonPath("$.domini[0].idDominio", is("*")))
                .andExpect(jsonPath("$.tipiPendenza[0].idTipoPendenza", is("*")))
                .andExpect(jsonPath("$.ruoli[0].id", is("AMMINISTRATORE")))
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        Cookie csrf = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(session).as("Sessione deve essere creata dopo login").isNotNull();
        assertThat(session.getAttributeNames().asIterator())
                .as("La sessione deve contenere il SecurityContext")
                .toIterable()
                .anyMatch(name -> name.toString().contains("SPRING_SECURITY_CONTEXT"));
        assertThat(csrf).as("XSRF-TOKEN deve essere impostato dopo login").isNotNull();
        assertThat(csrf.getValue()).isNotBlank();
    }

    @Test
    void loginWithBadPasswordReturns401Problem() throws Exception {
        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + PRINCIPAL + "\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.status", is(401)));
        // Il listener deve ricevere il principal tentato: se torna null, il
        // ramo PROFILO_LOGIN_FAILED in ConsoleAuthEventListener viene saltato
        // e l'audit non viene scritto.
        verify(authEventListener).onLoginFailed(
                eq(PRINCIPAL), eq(AuthType.FORM), eq(FailureReason.BAD_CREDENTIALS), any());
    }

    @Test
    void loginWithDisabledUtenzaReturns401Problem() throws Exception {
        Utenza disabled = utenzaRepository.findByPrincipal(PRINCIPAL).orElseThrow();
        disabled.setAbilitato(false);
        utenzaRepository.save(disabled);

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + PRINCIPAL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.status", is(401)));
        verify(authEventListener).onLoginFailed(
                eq(PRINCIPAL), eq(AuthType.FORM), eq(FailureReason.DISABLED), any());
    }

    @Test
    void loginWithMissingPasswordReturns400Problem() throws Exception {
        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + PRINCIPAL + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    void logoutWithValidSessionReturns204() throws Exception {
        MvcResult loginResult = mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + PRINCIPAL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        Cookie csrf = loginResult.getResponse().getCookie("XSRF-TOKEN");
        assertThat(session).isNotNull();
        assertThat(csrf).isNotNull();

        mvc.perform(post("/auth/logout")
                        .session(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isNoContent());
    }

    @Test
    void logoutWithoutSessionReturns204() throws Exception {
        // Semantica idempotente: retry dopo timeout / doppio click sul pulsante
        // di logout non devono fallire. L'audit PROFILO_LOGOUT viene saltato
        // perche' principal e' null nell'AuthEventListener.
        mvc.perform(post("/auth/logout"))
                .andExpect(status().isNoContent());
    }

    // Il 403 documentato dall'openapi (logout con sessione attiva e CSRF
    // mancante/non valido) non e' coperto da un MockMvc test: il matcher CSRF
    // della libreria bypassa l'enforcement quando getRequestedSessionId() e'
    // null, e MockMvc.session() non popola quel campo (lo farebbe il container
    // reale leggendo il cookie JSESSIONID). Forzature alternative (helper
    // SecurityMockMvcRequestPostProcessors.csrf().useInvalidToken(),
    // setRequestedSessionId manuale) hanno effetti collaterali sui test
    // adiacenti (perdita del cookie XSRF-TOKEN sul login successivo).
    // Il contratto resta documentato; verifica end-to-end demandata a test
    // con HTTP server reale (es. @SpringBootTest webEnvironment=RANDOM_PORT
    // + TestRestTemplate).
}
