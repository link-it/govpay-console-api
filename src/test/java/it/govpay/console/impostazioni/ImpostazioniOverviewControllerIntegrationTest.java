package it.govpay.console.impostazioni;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.common.auth.GovpayPasswordEncoder;
import it.govpay.console.entity.Acl;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Utenza;
import it.govpay.console.repository.AclRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.UtenzaRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ImpostazioniOverviewControllerIntegrationTest {

    private static final String PRINCIPAL = "operatore1";
    private static final String PASSWORD = "secret";
    private static final String BASE = "/impostazioni";
    private static final String SERVIZIO = "Configurazione e manutenzione";

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
    private tools.jackson.databind.ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        Utenza operatore = new Utenza();
        operatore.setPrincipal(PRINCIPAL);
        operatore.setPrincipalOriginale(PRINCIPAL);
        operatore.setAbilitato(true);
        operatore.setAutorizzazioneDominiStar(true);
        operatore.setAutorizzazioneTipiVersStar(true);
        operatore.setRuoli("OPERATORE");
        operatore.setPassword(encoder.encode(PASSWORD));
        utenzaRepository.save(operatore);

        Operatore op = new Operatore();
        op.setNome("Operatore Uno");
        op.setIdUtenza(operatore.getId());
        operatoreRepository.save(op);

        grantLettura();
    }

    @Test
    void getWithoutDirittoReturns403() throws Exception {
        aclRepository.deleteAll();
        mvc.perform(get(BASE).with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.detail", containsString(SERVIZIO)));
    }

    @Test
    void getReturnsAllEightAree() throws Exception {
        mvc.perform(get(BASE).with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aree", hasSize(8)));
    }

    @Test
    void getIncludesServizioGdeAsEighthArea() throws Exception {
        mvc.perform(get(BASE).with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aree[?(@.codice=='servizioGDE')].href", org.hamcrest.Matchers.contains("/impostazioni/servizioGDE")))
                .andExpect(jsonPath("$.aree[?(@.codice=='giornale-eventi')].href", org.hamcrest.Matchers.contains("/impostazioni/giornale-eventi")));
    }

    @Test
    void abilitataPresentOnlyForToggleableAree() throws Exception {
        mvc.perform(get(BASE).with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aree[?(@.codice=='servizioGDE')].abilitata", org.hamcrest.Matchers.contains(false)))
                .andExpect(jsonPath("$.aree[?(@.codice=='mail-server')].abilitata", org.hamcrest.Matchers.contains(false)))
                .andExpect(jsonPath("$.aree[?(@.codice=='app-io-server')].abilitata", org.hamcrest.Matchers.contains(false)))
                .andExpect(jsonPath("$.aree[?(@.codice=='hardening')].abilitata", org.hamcrest.Matchers.contains(false)))
                .andExpect(jsonPath("$.aree[?(@.codice=='giornale-eventi')].abilitata").doesNotExist())
                .andExpect(jsonPath("$.aree[?(@.codice=='tracciati-csv')].abilitata").doesNotExist());
    }

    @Test
    void ultimaModificaPopulatedAfterPutOnResource() throws Exception {
        grantScrittura();
        mvc.perform(get(BASE).with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(jsonPath("$.aree[?(@.codice=='mail-server')].ultimaModifica").doesNotExist());

        String etag = mvc.perform(get("/impostazioni/mail/server").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("ETag");
        mvc.perform(put("/impostazioni/mail/server").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"abilitato":true,"host":"smtp.example.org"}"""))
                .andExpect(status().isOk());

        mvc.perform(get(BASE).with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aree[?(@.codice=='mail-server')].abilitata", org.hamcrest.Matchers.contains(true)))
                .andExpect(jsonPath("$.aree[?(@.codice=='mail-server')].ultimaModifica").exists());
    }

    @Test
    void hrefsAreResolvable() throws Exception {
        String json = mvc.perform(get(BASE).with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var node = objectMapper.readTree(json).get("aree");
        for (var area : node) {
            String href = area.get("href").asText();
            mvc.perform(get(href).with(httpBasic(PRINCIPAL, PASSWORD)))
                    .andExpect(status().isOk());
        }
    }

    private void grantLettura() {
        grant("R");
    }

    private void grantScrittura() {
        grant("RW");
    }

    private void grant(String diritti) {
        Utenza utenza = utenzaRepository.findByPrincipal(PRINCIPAL).orElseThrow();
        Acl acl = new Acl();
        acl.setIdUtenza(utenza.getId());
        acl.setServizio(SERVIZIO);
        acl.setDiritti(diritti);
        aclRepository.save(acl);
    }
}
