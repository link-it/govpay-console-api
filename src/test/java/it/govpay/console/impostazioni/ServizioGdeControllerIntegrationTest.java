package it.govpay.console.impostazioni;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
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
import it.govpay.console.entity.ConnettoreProprieta;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Utenza;
import it.govpay.console.repository.AclRepository;
import it.govpay.console.repository.ConnettoreProprietaRepository;
import it.govpay.console.repository.GpAuditRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.UtenzaRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ServizioGdeControllerIntegrationTest {

    private static final String PRINCIPAL = "operatore1";
    private static final String PASSWORD = "secret";
    private static final String BASE = "/impostazioni/servizioGDE";
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
    private ConnettoreProprietaRepository connettoreProprietaRepository;
    @Autowired
    private GpAuditRepository gpAuditRepository;

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
    }

    // --- GET ---

    @Test
    void getWithoutDirittoReturns403() throws Exception {
        mvc.perform(get(BASE).with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.detail", containsString(SERVIZIO)));
    }

    @Test
    void getUnconfiguredReturnsDisabledWithEtag() throws Exception {
        grantLettura();
        mvc.perform(get(BASE).with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.abilitato", is(false)))
                .andExpect(jsonPath("$.url").doesNotExist());
    }

    // --- PUT config ---

    @Test
    void putConfigWithoutDirittoReturns403() throws Exception {
        grantLettura();
        String etag = currentEtag();
        mvc.perform(put(BASE).with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"abilitato":true,"url":"https://gde.example.org"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail", containsString(SERVIZIO)));
    }

    @Test
    void putConfigWithIfMatchRoundTrips() throws Exception {
        grantScrittura();
        String etag = currentEtag();
        String body = """
                {"abilitato":true,"url":"https://gde.example.org/api/v1",
                 "auth":{"tipoAutenticazione":"HTTPBASIC","username":"gde-svc"}}""";
        mvc.perform(put(BASE).with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.abilitato", is(true)))
                .andExpect(jsonPath("$.url", is("https://gde.example.org/api/v1")))
                .andExpect(jsonPath("$.auth.tipoAutenticazione", is("HTTPBASIC")))
                .andExpect(jsonPath("$.auth.username", is("gde-svc")));
    }

    @Test
    void putConfigWithoutIfMatchReturns428() throws Exception {
        grantScrittura();
        mvc.perform(put(BASE).with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"abilitato":true,"url":"https://x"}"""))
                .andExpect(status().isPreconditionRequired());
    }

    @Test
    void putConfigWithWrongIfMatchReturns412() throws Exception {
        grantScrittura();
        mvc.perform(put(BASE).with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", "\"deadbeef\"")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"abilitato":true,"url":"https://x"}"""))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void putConfigWithHeaderAuthPersistsFullAuthTypeSet() throws Exception {
        grantScrittura();
        String etag = currentEtag();
        String body = """
                {"abilitato":true,"url":"https://gde.example.org",
                 "auth":{"tipoAutenticazione":"HEADER","headerName":"X-Gde-Auth"}}""";
        mvc.perform(put(BASE).with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.auth.tipoAutenticazione", is("HEADER")))
                .andExpect(jsonPath("$.auth.headerName", is("X-Gde-Auth")));

        Map<String, String> eav = eavProperties();
        org.assertj.core.api.Assertions.assertThat(eav)
                .containsEntry("TIPOAUTENTICAZIONE", "HTTP_HEADER")
                .containsEntry("HTTP_HEADER_AUTH_HEADER_NAME", "X-Gde-Auth");
    }

    // --- PUT credenziali ---

    @Test
    void putCredenzialiWithoutDirittoReturns403() throws Exception {
        grantLettura();
        mvc.perform(put(BASE + "/credenziali").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"password":"s3cr3t"}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    void putCredenzialiReturns204AndPasswordNeverReturned() throws Exception {
        grantScrittura();
        putConfig(currentEtag(), """
                {"abilitato":true,"url":"https://gde.example.org",
                 "auth":{"tipoAutenticazione":"HTTPBASIC","username":"gde-svc"}}""");
        mvc.perform(put(BASE + "/credenziali").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"password":"s3cr3t"}"""))
                .andExpect(status().isNoContent());
        mvc.perform(get(BASE).with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.auth.username", is("gde-svc")))
                .andExpect(jsonPath("$..password").doesNotExist());

        Map<String, String> eav = eavProperties();
        org.assertj.core.api.Assertions.assertThat(eav).containsEntry("HTTPPASSW", "s3cr3t");
    }

    @Test
    void putConfigWritesAudit() throws Exception {
        grantScrittura();
        long before = countAudit(ServizioGdeService.AZIONE_AUDIT_MODIFICA);
        putConfig(currentEtag(), """
                {"abilitato":true,"url":"https://gde.example.org"}""");
        org.assertj.core.api.Assertions.assertThat(countAudit(ServizioGdeService.AZIONE_AUDIT_MODIFICA))
                .isEqualTo(before + 1);
    }

    // --- helpers ---

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

    private Map<String, String> eavProperties() {
        Map<String, String> map = new HashMap<>();
        for (ConnettoreProprieta p : connettoreProprietaRepository.findByCodConnettore(ServizioGdeService.COD_CONNETTORE_GDE)) {
            map.put(p.getCodProprieta(), p.getValore());
        }
        return map;
    }

    private long countAudit(String azione) {
        return gpAuditRepository.findAll().stream().filter(a -> azione.equals(a.getTipoOggetto())).count();
    }

    private void putConfig(String etag, String body) throws Exception {
        mvc.perform(put(BASE).with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    private String currentEtag() throws Exception {
        grantLetturaIfAbsent();
        return mvc.perform(get(BASE).with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("ETag");
    }

    private void grantLetturaIfAbsent() {
        Utenza utenza = utenzaRepository.findByPrincipal(PRINCIPAL).orElseThrow();
        boolean hasLettura = aclRepository.findByIdUtenza(utenza.getId()).stream()
                .anyMatch(a -> SERVIZIO.equals(a.getServizio()));
        if (!hasLettura) {
            grantLettura();
        }
    }
}
