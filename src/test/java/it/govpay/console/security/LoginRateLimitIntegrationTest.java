package it.govpay.console.security;

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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.common.auth.GovpayPasswordEncoder;
import it.govpay.common.auth.spi.AuthType;
import it.govpay.common.auth.spi.FailureReason;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Utenza;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.UtenzaRepository;

/**
 * Verifica end-to-end che, quando il rate-limit di {@code POST /auth/login}
 * scatta, il listener di console-api riceva l'{@code attemptedPrincipal}
 * estratto dal body (non {@code null}). Questo permette al
 * {@code ConsoleAuthEventListener} di scrivere la riga audit
 * {@code PROFILO_LOGIN_FAILED} con {@code motivo=RATE_LIMITED} per principal
 * noti — comportamento promesso dal contratto openapi (response 429 della
 * /auth/login).
 *
 * <p>Bug fix: prima il filter eseguiva il rate-limit check PRIMA del parsing
 * del body, quindi il principal non era disponibile e il listener veniva
 * sempre invocato con {@code attemptedPrincipal=null}; il consumer skippava
 * il DB audit per principal sconosciuti.
 *
 * <p>Test class dedicato con {@code attempts=1} per non disturbare gli altri
 * test del file {@link LoginLogoutIntegrationTest} (che usano i default).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "govpay.auth.form.rate-limit.attempts=1",
        "govpay.auth.form.rate-limit.window-minutes=15"})
@Transactional
class LoginRateLimitIntegrationTest {

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
        utenza.setRuoli("OPERATORE");
        utenza = utenzaRepository.save(utenza);

        Operatore operatore = new Operatore();
        operatore.setIdUtenza(utenza.getId());
        operatore.setNome("Alice Rossi");
        operatoreRepository.save(operatore);
    }

    @Test
    void rateLimitedLoginPropagatesAttemptedPrincipalToListener() throws Exception {
        // Prima richiesta: bad creds → 401 + listener(alice, BAD_CREDENTIALS).
        // Esaurisce il limite (attempts=1 → recordFailure satura la soglia).
        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + PRINCIPAL + "\",\"password\":\"WRONG\"}"))
                .andExpect(status().isUnauthorized());
        verify(authEventListener).onLoginFailed(
                eq(PRINCIPAL), eq(AuthType.FORM), eq(FailureReason.BAD_CREDENTIALS), any());

        // Seconda richiesta: stesso IP, stesso principal. Rate-limit DEVE
        // scattare e il listener DEVE ricevere lo username "alice" (NON null),
        // cosi' il branch DB-audit di ConsoleAuthEventListener (lookup Utenza
        // by principal) parte e PROFILO_LOGIN_FAILED viene scritto.
        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + PRINCIPAL + "\",\"password\":\"another-wrong\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.status", is(429)));
        verify(authEventListener).onLoginFailed(
                eq(PRINCIPAL), eq(AuthType.FORM), eq(FailureReason.RATE_LIMITED), any());
    }
}
