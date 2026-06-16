package it.govpay.console.profilo;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
                .andExpect(jsonPath("$.acl[0].autorizzazioni[1]", is("W")));
    }

    /**
     * Regressione: utenza autorizzata SOLO su una UO specifica di un dominio
     * (riga {@code utenze_domini} con {@code id_uo IS NOT NULL}, nessuna riga
     * con {@code id_uo IS NULL}). V1 (UtentiDAO.getDominiAutorizzati)
     * restituisce comunque il dominio padre; V2 prima del fix lo perdeva
     * perche' {@code buildDomini} guardava solo {@code idDominiInteri}.
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
}
