package it.govpay.console.impostazioni;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import it.govpay.common.configurazione.ConfigurazioneKeys;
import it.govpay.common.configurazione.model.Hardening;
import it.govpay.common.repository.ConfigurazioneRepository;
import it.govpay.console.entity.Acl;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Utenza;
import it.govpay.console.repository.AclRepository;
import it.govpay.console.repository.GpAuditRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.UtenzaRepository;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HardeningControllerIntegrationTest {

    private static final String PRINCIPAL = "operatore1";
    private static final String PASSWORD = "secret";
    private static final String BASE = "/impostazioni/hardening";
    private static final String SERVIZIO = "Configurazione e manutenzione";
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
    private GpAuditRepository gpAuditRepository;
    @Autowired
    private ConfigurazioneRepository configurazioneRepository;
    @Autowired
    private ObjectMapper objectMapper;

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

    @Test
    void getWithoutDirittoReturns403() throws Exception {
        mvc.perform(get(BASE).with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.detail", containsString(SERVIZIO)));
    }

    @Test
    void getDefaultReturnsDisabledWithoutSecretKey() throws Exception {
        grantLettura();
        mvc.perform(get(BASE).with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.abilitato", is(false)))
                .andExpect(jsonPath("$..secretKey").doesNotExist());
    }

    @Test
    void putConfigWithIfMatchRoundTrips() throws Exception {
        grantScrittura();
        String etag = currentEtag();
        String body = """
                {"abilitato":true,"captcha":{"serverURL":"https://www.google.com/recaptcha/api/siteverify",
                 "siteKey":"site-123","soglia":0.7,"parametro":"gRecaptchaResponse","denyOnFail":true,
                 "connectionTimeoutMs":5000,"readTimeoutMs":5000}}""";
        mvc.perform(put(BASE).with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.abilitato", is(true)))
                .andExpect(jsonPath("$.captcha.siteKey", is("site-123")))
                .andExpect(jsonPath("$.captcha.soglia", is(0.7)))
                .andExpect(jsonPath("$.captcha.denyOnFail", is(true)));
    }

    @Test
    void putConfigWithoutIfMatchReturns428() throws Exception {
        grantScrittura();
        mvc.perform(put(BASE).with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(minimalBody()))
                .andExpect(status().isPreconditionRequired());
    }

    @Test
    void putConfigWithWrongIfMatchReturns412() throws Exception {
        grantScrittura();
        mvc.perform(put(BASE).with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", "\"deadbeef\"")
                        .contentType(MediaType.APPLICATION_JSON).content(minimalBody()))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void patchReplacesCaptchaBlockAtomically() throws Exception {
        grantScrittura();
        putConfig(currentEtag(), minimalBody());
        String patchBody = """
                [{"op":"replace","path":"/captcha","value":
                    {"siteKey":"nuova-site","soglia":0.9,"denyOnFail":false}}]""";
        mvc.perform(patch(BASE).with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", currentEtag())
                        .contentType(JSON_PATCH).content(patchBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.captcha.siteKey", is("nuova-site")))
                .andExpect(jsonPath("$.captcha.soglia", is(0.9)));
    }

    @Test
    void putCredenzialiWithoutDirittoReturns403() throws Exception {
        grantLettura();
        mvc.perform(put(BASE + "/credenziali").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"secretKey":"s3cr3t"}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    void putCredenzialiReturns204AndSecretKeyNeverReturned() throws Exception {
        grantScrittura();
        putConfig(currentEtag(), minimalBody());
        mvc.perform(put(BASE + "/credenziali").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"secretKey":"s3cr3t"}"""))
                .andExpect(status().isNoContent());

        mvc.perform(get(BASE).with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$..secretKey").doesNotExist());

        String valore = configurazioneRepository.findByNome(ConfigurazioneKeys.KEY_HARDENING).orElseThrow().getValore();
        Hardening hardening = objectMapper.readValue(valore, Hardening.class);
        org.assertj.core.api.Assertions.assertThat(hardening.getGoogleCatpcha().getSecretKey()).isEqualTo("s3cr3t");
    }

    @Test
    void putConfigWritesAudit() throws Exception {
        grantScrittura();
        long before = countAudit(HardeningService.AZIONE_AUDIT_MODIFICA);
        putConfig(currentEtag(), minimalBody());
        org.assertj.core.api.Assertions.assertThat(countAudit(HardeningService.AZIONE_AUDIT_MODIFICA))
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
        boolean has = aclRepository.findByIdUtenza(utenza.getId()).stream()
                .anyMatch(a -> SERVIZIO.equals(a.getServizio()));
        if (!has) {
            grantLettura();
        }
    }

    private static String minimalBody() {
        return """
                {"abilitato":true,"captcha":{"siteKey":"site-abc","soglia":0.5,"denyOnFail":false}}""";
    }
}
