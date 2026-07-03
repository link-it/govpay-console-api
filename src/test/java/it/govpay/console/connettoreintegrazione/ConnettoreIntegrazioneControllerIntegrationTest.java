package it.govpay.console.connettoreintegrazione;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import java.util.HashMap;
import java.util.Map;

import it.govpay.common.auth.GovpayPasswordEncoder;
import it.govpay.console.entity.Applicazione;
import it.govpay.console.entity.ConnettoreProprieta;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Utenza;
import it.govpay.console.repository.ApplicazioneRepository;
import it.govpay.console.repository.ConnettoreProprietaRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.UtenzaRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ConnettoreIntegrazioneControllerIntegrationTest {

    private static final String PRINCIPAL = "operatore1";
    private static final String PASSWORD = "secret";
    private static final String BASE = "/applicazioni/APP-001/connettore-integrazione";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private GovpayPasswordEncoder encoder;
    @Autowired
    private UtenzaRepository utenzaRepository;
    @Autowired
    private OperatoreRepository operatoreRepository;
    @Autowired
    private ApplicazioneRepository applicazioneRepository;
    @Autowired
    private ConnettoreProprietaRepository connettoreProprietaRepository;

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

        Utenza u = new Utenza();
        u.setPrincipal("p-app1");
        u.setPrincipalOriginale("p-app1");
        u.setAbilitato(true);
        u.setAutorizzazioneDominiStar(false);
        u.setAutorizzazioneTipiVersStar(false);
        utenzaRepository.save(u);

        Applicazione app = new Applicazione();
        app.setCodApplicazione("APP-001");
        app.setUtenza(u);
        applicazioneRepository.save(app);
    }

    @Test
    void getUnconfiguredReturnsDisabledWithEtag() throws Exception {
        mvc.perform(get(BASE).with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.abilitato", is(false)))
                .andExpect(jsonPath("$.url").doesNotExist());
    }

    @Test
    void getUnknownApplicazioneReturns404() throws Exception {
        mvc.perform(get("/applicazioni/APP-999/connettore-integrazione").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void putConfigWithIfMatchRoundTrips() throws Exception {
        String etag = currentEtag();
        String body = """
                {"abilitato":true,"url":"https://a2a.example.org/integrazione",
                 "versione":"REST_V2","tipoAutenticazione":"BASIC","username":"svc",
                 "connectTimeoutMs":2000,"readTimeoutMs":5000}""";
        mvc.perform(put(BASE).with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.abilitato", is(true)))
                .andExpect(jsonPath("$.url", is("https://a2a.example.org/integrazione")))
                .andExpect(jsonPath("$.versione", is("REST_V2")))
                .andExpect(jsonPath("$.tipoAutenticazione", is("BASIC")))
                .andExpect(jsonPath("$.username", is("svc")))
                .andExpect(jsonPath("$.connectTimeoutMs", is(2000)))
                .andExpect(jsonPath("$.readTimeoutMs", is(5000)));
    }

    @Test
    void putConfigWithoutIfMatchReturns428() throws Exception {
        String body = """
                {"abilitato":true,"url":"https://x"}""";
        mvc.perform(put(BASE).with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPreconditionRequired());
    }

    @Test
    void putConfigWithWrongIfMatchReturns412() throws Exception {
        String body = """
                {"abilitato":true,"url":"https://x"}""";
        mvc.perform(put(BASE).with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", "\"deadbeef\"")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void disableViaAbilitatoFalse() throws Exception {
        putConfig(currentEtag(), """
                {"abilitato":true,"url":"https://x"}""");
        putConfig(currentEtag(), """
                {"abilitato":false}""");
        mvc.perform(get(BASE).with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abilitato", is(false)))
                .andExpect(jsonPath("$.url").doesNotExist());
    }

    @Test
    void putCredenzialiReturns204AndPasswordNeverReturned() throws Exception {
        putConfig(currentEtag(), """
                {"abilitato":true,"url":"https://x","tipoAutenticazione":"BASIC","username":"svc"}""");
        mvc.perform(put(BASE + "/credenziali").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"password":"s3cr3t"}"""))
                .andExpect(status().isNoContent());
        mvc.perform(get(BASE).with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.username", is("svc")));
    }

    @Test
    void putSslConfigAndCredentialsRoundTrips() throws Exception {
        String etag = currentEtag();
        String body = """
                {"abilitato":true,"url":"https://a2a.example.org","tipoAutenticazione":"SSL",
                 "sslTipo":"CLIENT","ksLocation":"/etc/ks.p12","ksType":"PKCS12",
                 "tsLocation":"/etc/ts.jks","tsType":"JKS","sslType":"TLSv1.2"}""";
        mvc.perform(put(BASE).with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoAutenticazione", is("SSL")))
                .andExpect(jsonPath("$.sslTipo", is("CLIENT")))
                .andExpect(jsonPath("$.ksLocation", is("/etc/ks.p12")))
                .andExpect(jsonPath("$.ksType", is("PKCS12")))
                .andExpect(jsonPath("$.tsLocation", is("/etc/ts.jks")))
                .andExpect(jsonPath("$.tsType", is("JKS")))
                .andExpect(jsonPath("$.sslType", is("TLSv1.2")));

        mvc.perform(put(BASE + "/credenziali").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"ksPassword":"ksSecret","tsPassword":"tsSecret","ksPKeyPasswd":"pkSecret"}"""))
                .andExpect(status().isNoContent());

        // Le credenziali SSL non tornano nel GET...
        mvc.perform(get(BASE).with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sslTipo", is("CLIENT")))
                .andExpect(jsonPath("$.ksPassword").doesNotExist())
                .andExpect(jsonPath("$.tsPassword").doesNotExist())
                .andExpect(jsonPath("$.ksPKeyPasswd").doesNotExist());

        // ...ma sono persistite sulle chiavi EAV V1 attese.
        Map<String, String> eav = eavProperties();
        org.assertj.core.api.Assertions.assertThat(eav)
                .containsEntry("SSLKSPASSWD", "ksSecret")
                .containsEntry("SSLTSPASSWD", "tsSecret")
                .containsEntry("SSLPKEYPASSWD", "pkSecret")
                .containsEntry("TIPOSSL", "CLIENT")
                .containsEntry("SSLKSLOCATION", "/etc/ks.p12")
                .containsEntry("TIPOAUTENTICAZIONE", "SSL");
    }

    @Test
    void sslWithoutSslTipoReturns422() throws Exception {
        String etag = currentEtag();
        String body = """
                {"abilitato":true,"url":"https://x","tipoAutenticazione":"SSL"}""";
        mvc.perform(put(BASE).with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("sslTipo")));
    }

    private Map<String, String> eavProperties() {
        Map<String, String> map = new HashMap<>();
        for (ConnettoreProprieta p : connettoreProprietaRepository.findByCodConnettore("APP-001_INTEGRAZIONE")) {
            map.put(p.getCodProprieta(), p.getValore());
        }
        return map;
    }

    @Test
    void putCredenzialiUnknownApplicazioneReturns404() throws Exception {
        mvc.perform(put("/applicazioni/APP-999/connettore-integrazione/credenziali")
                        .with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"password":"x"}"""))
                .andExpect(status().isNotFound());
    }

    private void putConfig(String etag, String body) throws Exception {
        mvc.perform(put(BASE).with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    private String currentEtag() throws Exception {
        return mvc.perform(get(BASE).with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("ETag");
    }
}
