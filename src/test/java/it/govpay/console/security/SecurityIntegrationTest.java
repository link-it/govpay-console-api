package it.govpay.console.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Utenza;
import it.govpay.console.entity.UtenzaDominio;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.UtenzaDominioRepository;
import it.govpay.console.repository.UtenzaRepository;
import it.govpay.console.web.TestProblemController;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestProblemController.class)
@Transactional
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UtenzaRepository utenzaRepository;

    @Autowired
    private OperatoreRepository operatoreRepository;

    @Autowired
    private UtenzaDominioRepository utenzaDominioRepository;

    @Autowired
    private DominioRepository dominioRepository;

    @Autowired
    private GovpayPasswordEncoder encoder;

    @Autowired
    private CurrentOperatorService currentOperatorService;

    private static final String PRINCIPAL = "mario";
    private static final String PASSWORD = "super-secret";

    @BeforeEach
    void setup() {
        SecurityContextHolder.clearContext();

        Utenza utenza = new Utenza();
        utenza.setPrincipal(PRINCIPAL);
        utenza.setPrincipalOriginale(PRINCIPAL);
        utenza.setAbilitato(true);
        utenza.setAutorizzazioneDominiStar(false);
        utenza.setAutorizzazioneTipiVersStar(false);
        utenza.setRuoli("AMMINISTRATORE,OPERATORE");
        utenza.setPassword(encoder.encode(PASSWORD));
        utenzaRepository.save(utenza);

        Operatore operatore = new Operatore();
        operatore.setNome("Mario Rossi");
        operatore.setIdUtenza(utenza.getId());
        operatoreRepository.save(operatore);

        Dominio dominio1 = new Dominio();
        dominio1.setCodDominio("11111111111");
        dominio1.setRagioneSociale("Comune Uno");
        dominioRepository.save(dominio1);

        Dominio dominio2 = new Dominio();
        dominio2.setCodDominio("22222222222");
        dominio2.setRagioneSociale("Comune Due");
        dominioRepository.save(dominio2);

        UtenzaDominio link1 = new UtenzaDominio();
        link1.setIdUtenza(utenza.getId());
        link1.setIdDominio(dominio1.getId());
        utenzaDominioRepository.save(link1);

        UtenzaDominio link2 = new UtenzaDominio();
        link2.setIdUtenza(utenza.getId());
        link2.setIdDominio(dominio2.getId());
        utenzaDominioRepository.save(link2);
    }

    @Test
    void unauthenticatedRequestReturns401ProblemJson() throws Exception {
        mockMvc.perform(get("/_test-problem/not-found"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.title", is("Unauthorized")));
    }

    @Test
    void wrongPasswordReturns401ProblemJson() throws Exception {
        mockMvc.perform(get("/_test-problem/not-found").with(httpBasic(PRINCIPAL, "wrong")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    void validBasicAuthReachesController() throws Exception {
        // L'endpoint /not-found e' protetto e lancia NotFoundException; col login valido la
        // SecurityFilterChain ci lascia passare e arriviamo al ProblemExceptionHandler.
        mockMvc.perform(get("/_test-problem/not-found").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.status", is(404)));
    }

    @Test
    void currentOperatorServiceResolvesPrincipalToOperatoreAndDomains() {
        Utenza utenza = utenzaRepository.findByPrincipal(PRINCIPAL).orElseThrow();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        PRINCIPAL, "n/a",
                        java.util.List.of(new SimpleGrantedAuthority("ROLE_AMMINISTRATORE"))));

        OperatoreCorrente corrente = currentOperatorService.get();

        assertThat(corrente.principal()).isEqualTo(PRINCIPAL);
        assertThat(corrente.idUtenza()).isEqualTo(utenza.getId());
        assertThat(corrente.idOperatore()).isNotNull();
        assertThat(corrente.nomeOperatore()).isEqualTo("Mario Rossi");
        assertThat(corrente.tuttiIDomini()).isFalse();
        assertThat(corrente.idDominiVisibili()).hasSize(2);
    }

    @Test
    void currentOperatorServiceFailsWithoutAuthentication() {
        SecurityContextHolder.clearContext();

        try {
            currentOperatorService.get();
            assertThat(false).as("Doveva lanciare IllegalStateException").isTrue();
        } catch (IllegalStateException expected) {
            assertThat(expected.getMessage()).contains("Nessun utente autenticato");
        }
    }
}
