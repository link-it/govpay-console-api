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
import it.govpay.common.configurazione.model.MailBatch;
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
class MailServerControllerIntegrationTest {

    private static final String PRINCIPAL = "operatore1";
    private static final String PASSWORD = "secret";
    private static final String BASE = "/impostazioni/mail/server";
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
    void getDefaultReturnsDisabledWithoutPassword() throws Exception {
        grantLettura();
        mvc.perform(get(BASE).with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.abilitato", is(false)))
                .andExpect(jsonPath("$.passwordImpostata", is(false)))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void putConfigWithIfMatchRoundTrips() throws Exception {
        grantScrittura();
        String etag = currentEtag();
        String body = """
                {"abilitato":true,"host":"smtp.example.org","port":587,"username":"pagopa",
                 "from":"pagopa@example.org","readTimeoutMs":120000,"connectionTimeoutMs":10000,
                 "startTls":true,
                 "ssl":{"abilitato":true,"tipo":"TLS","hostnameVerifier":true,
                        "trustStore":{"location":"/etc/ts.jks","tipo":"JKS","managementAlgorithm":"SunX509"},
                        "keyStore":{"location":"/etc/ks.p12","tipo":"PKCS12","managementAlgorithm":"SunX509"}}}""";
        mvc.perform(put(BASE).with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.host", is("smtp.example.org")))
                .andExpect(jsonPath("$.port", is(587)))
                .andExpect(jsonPath("$.ssl.abilitato", is(true)))
                .andExpect(jsonPath("$.ssl.trustStore.location", is("/etc/ts.jks")))
                .andExpect(jsonPath("$.ssl.keyStore.tipo", is("PKCS12")));
    }

    @Test
    void putConfigWithoutIfMatchReturns428() throws Exception {
        grantScrittura();
        mvc.perform(put(BASE).with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"abilitato":true}"""))
                .andExpect(status().isPreconditionRequired());
    }

    @Test
    void putConfigWithWrongIfMatchReturns412() throws Exception {
        grantScrittura();
        mvc.perform(put(BASE).with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", "\"deadbeef\"")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"abilitato":true}"""))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void patchReplacesSslBlockAtomically() throws Exception {
        grantScrittura();
        putConfig(currentEtag(), """
                {"abilitato":true,"host":"smtp.example.org"}""");
        String patchBody = """
                [{"op":"replace","path":"/ssl","value":
                    {"abilitato":true,"tipo":"TLS","hostnameVerifier":true}}]""";
        mvc.perform(patch(BASE).with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", currentEtag())
                        .contentType(JSON_PATCH).content(patchBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ssl.abilitato", is(true)))
                .andExpect(jsonPath("$.ssl.tipo", is("TLS")))
                .andExpect(jsonPath("$.host", is("smtp.example.org")));
    }

    @Test
    void putPasswordWithoutDirittoReturns403() throws Exception {
        grantLettura();
        mvc.perform(put(BASE + "/password").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"nuovaPassword":"s3cr3t"}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    void putPasswordReturns204AndNeverInGet() throws Exception {
        grantScrittura();
        putConfig(currentEtag(), """
                {"abilitato":true,"host":"smtp.example.org"}""");
        mvc.perform(put(BASE + "/password").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"nuovaPassword":"s3cr3t","ksPassword":"ksSecret","tsPassword":"tsSecret"}"""))
                .andExpect(status().isNoContent());

        mvc.perform(get(BASE).with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordImpostata", is(true)))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.ksPassword").doesNotExist())
                .andExpect(jsonPath("$.tsPassword").doesNotExist());

        String valore = configurazioneRepository.findByNome(ConfigurazioneKeys.KEY_MAIL_BATCH).orElseThrow().getValore();
        MailBatch mailBatch = objectMapper.readValue(valore, MailBatch.class);
        org.assertj.core.api.Assertions.assertThat(mailBatch.getMailserver().getPassword()).isEqualTo("s3cr3t");
        org.assertj.core.api.Assertions.assertThat(mailBatch.getMailserver().getSslConfig().getKeyStore().getPassword()).isEqualTo("ksSecret");
        org.assertj.core.api.Assertions.assertThat(mailBatch.getMailserver().getSslConfig().getTrustStore().getPassword()).isEqualTo("tsSecret");
    }

    @Test
    void putConfigWritesAudit() throws Exception {
        grantScrittura();
        long before = countAudit(MailServerService.AZIONE_AUDIT_MODIFICA);
        putConfig(currentEtag(), """
                {"abilitato":true,"host":"smtp.example.org"}""");
        org.assertj.core.api.Assertions.assertThat(countAudit(MailServerService.AZIONE_AUDIT_MODIFICA))
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
}
