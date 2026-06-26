package it.govpay.console.connettore;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import it.govpay.console.entity.ConnettoreProprieta;
import it.govpay.console.entity.Intermediario;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Utenza;
import it.govpay.console.repository.ConnettoreProprietaRepository;
import it.govpay.console.repository.IntermediarioRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.UtenzaRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ConnettoreControllerIntegrationTest {

    private static final String PRINCIPAL = "operatore1";
    private static final String PASSWORD = "secret";
    private static final String PDD_COD = "INT-001";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private GovpayPasswordEncoder encoder;
    @Autowired
    private UtenzaRepository utenzaRepository;
    @Autowired
    private OperatoreRepository operatoreRepository;
    @Autowired
    private IntermediarioRepository intermediarioRepository;
    @Autowired
    private ConnettoreProprietaRepository connettoreRepository;

    @BeforeEach
    void setup() {
        Utenza utenza = new Utenza();
        utenza.setPrincipal(PRINCIPAL);
        utenza.setPrincipalOriginale(PRINCIPAL);
        utenza.setAbilitato(true);
        utenza.setAutorizzazioneDominiStar(true);
        utenza.setAutorizzazioneTipiVersStar(true);
        utenza.setRuoli("OPERATORE");
        utenza.setPassword(encoder.encode(PASSWORD));
        utenzaRepository.save(utenza);

        Operatore op = new Operatore();
        op.setNome("Operatore Uno");
        op.setIdUtenza(utenza.getId());
        operatoreRepository.save(op);

        Intermediario i = new Intermediario();
        i.setCodIntermediario("INT-001");
        i.setDenominazione("Alfa");
        i.setPrincipal("p");
        i.setPrincipalOriginale("p");
        i.setCodConnettorePdd(PDD_COD);
        i.setAbilitato(true);
        intermediarioRepository.save(i);
    }

    private void seed(String cod, String key, String value) {
        connettoreRepository.save(new ConnettoreProprieta(cod, key, value));
    }

    // --- Auth ---

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/intermediari/INT-001/connettori/pagopa"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"));
    }

    // --- GET ---

    @Test
    void getEmptyPagopaSlotReturnsDisabledNone() throws Exception {
        mvc.perform(get("/intermediari/INT-001/connettori/pagopa").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.abilitato", is(false)))
                .andExpect(jsonPath("$.auth.tipoAutenticazione", is("NONE")));
    }

    @Test
    void getPagopaReadsSeededConfigWithoutCredentials() throws Exception {
        seed(PDD_COD, "ABILITATO", "true");
        seed(PDD_COD, "URL", "https://pdd.example");
        seed(PDD_COD, "TIPOAUTENTICAZIONE", "API_KEY");
        seed(PDD_COD, "API_KEY_AUTH_API_ID_NAME", "my-api-id");
        seed(PDD_COD, "API_KEY_AUTH_API_KEY_NAME", "super-secret");

        mvc.perform(get("/intermediari/INT-001/connettori/pagopa").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abilitato", is(true)))
                .andExpect(jsonPath("$.urlRPT", is("https://pdd.example")))
                .andExpect(jsonPath("$.auth.tipoAutenticazione", is("APIKEY")))
                .andExpect(jsonPath("$.auth.apiId", is("my-api-id")))
                .andExpect(jsonPath("$.subscriptionKey").doesNotExist())
                .andExpect(jsonPath("$.auth.apiKey").doesNotExist());
    }

    @Test
    void getAcaEmptySlotHasSecondaryShape() throws Exception {
        mvc.perform(get("/intermediari/INT-001/connettori/pagopa-aca").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abilitato", is(false)))
                .andExpect(jsonPath("$.abilitaGDE", is(false)))
                .andExpect(jsonPath("$.auth.tipoAutenticazione", is("NONE")));
    }

    @Test
    void getUnknownIntermediarioReturns404() throws Exception {
        mvc.perform(get("/intermediari/INT-NOPE/connettori/pagopa").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void allSixChannelsAreRoutable() throws Exception {
        for (String c : new String[] {"pagopa", "pagopa-aca", "pagopa-gpd", "pagopa-fr",
                "pagopa-backoffice-ec", "pagopa-recupero-rt"}) {
            mvc.perform(get("/intermediari/INT-001/connettori/" + c).with(httpBasic(PRINCIPAL, PASSWORD)))
                    .andExpect(status().isOk());
        }
    }

    // --- PUT config ---

    @Test
    void replacePagopaConfigWithIfMatchSucceeds() throws Exception {
        String etag = currentEtag("pagopa");
        String body = """
                {"abilitato":true,"urlRPT":"https://new.pdd","auth":{"tipoAutenticazione":"SSL","sslTipo":"CLIENT","ksLocation":"/ks"}}""";
        String newEtag = mvc.perform(put("/intermediari/INT-001/connettori/pagopa")
                        .with(httpBasic(PRINCIPAL, PASSWORD)).header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abilitato", is(true)))
                .andExpect(jsonPath("$.urlRPT", is("https://new.pdd")))
                .andExpect(jsonPath("$.auth.tipoAutenticazione", is("SSL")))
                .andExpect(jsonPath("$.auth.sslTipo", is("CLIENT")))
                .andReturn().getResponse().getHeader("ETag");
        org.assertj.core.api.Assertions.assertThat(newEtag).isNotEqualTo(etag);
        org.assertj.core.api.Assertions.assertThat(propertyValue(PDD_COD, "TIPOAUTENTICAZIONE")).isEqualTo("SSL");
    }

    @Test
    void replaceConfigPreservesCredentials() throws Exception {
        seed(PDD_COD, "API_KEY_AUTH_API_KEY_NAME", "keep-me");
        String etag = currentEtag("pagopa");
        String body = """
                {"abilitato":true,"urlRPT":"https://x","auth":{"tipoAutenticazione":"NONE"}}""";
        mvc.perform(put("/intermediari/INT-001/connettori/pagopa")
                        .with(httpBasic(PRINCIPAL, PASSWORD)).header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(propertyValue(PDD_COD, "API_KEY_AUTH_API_KEY_NAME"))
                .isEqualTo("keep-me");
    }

    @Test
    void replaceWithWrongIfMatchReturns412() throws Exception {
        String body = """
                {"abilitato":true,"urlRPT":"https://x","auth":{"tipoAutenticazione":"NONE"}}""";
        mvc.perform(put("/intermediari/INT-001/connettori/pagopa")
                        .with(httpBasic(PRINCIPAL, PASSWORD)).header("If-Match", "\"deadbeef\"")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void replaceWithoutIfMatchReturns428() throws Exception {
        String body = """
                {"abilitato":true,"urlRPT":"https://x","auth":{"tipoAutenticazione":"NONE"}}""";
        mvc.perform(put("/intermediari/INT-001/connettori/pagopa")
                        .with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPreconditionRequired());
    }

    @Test
    void replaceUnknownIntermediarioReturns404() throws Exception {
        String body = """
                {"abilitato":true,"urlRPT":"https://x","auth":{"tipoAutenticazione":"NONE"}}""";
        mvc.perform(put("/intermediari/INT-NOPE/connettori/pagopa")
                        .with(httpBasic(PRINCIPAL, PASSWORD)).header("If-Match", "*")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void replaceEmptyAcaSlotGeneratesCodConnettore() throws Exception {
        String etag = currentEtag("pagopa-aca");
        String body = """
                {"abilitato":true,"url":"https://aca","abilitaGDE":true,"auth":{"tipoAutenticazione":"NONE"}}""";
        mvc.perform(put("/intermediari/INT-001/connettori/pagopa-aca")
                        .with(httpBasic(PRINCIPAL, PASSWORD)).header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abilitaGDE", is(true)));
        Intermediario reloaded = intermediarioRepository.findByCodIntermediario("INT-001").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(reloaded.getCodConnettoreAca()).isEqualTo("INT-001_ACA");
        org.assertj.core.api.Assertions.assertThat(propertyValue("INT-001_ACA", "URL")).isEqualTo("https://aca");
    }

    @Test
    void oauth2AuthRoundTrips() throws Exception {
        String etag = currentEtag("pagopa-gpd");
        String body = """
                {"abilitato":true,"url":"https://gpd","abilitaGDE":false,"auth":{"tipoAutenticazione":"OAUTH2","clientId":"cid","scope":"sc","urlTokenEndpoint":"https://token"}}""";
        mvc.perform(put("/intermediari/INT-001/connettori/pagopa-gpd")
                        .with(httpBasic(PRINCIPAL, PASSWORD)).header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.auth.tipoAutenticazione", is("OAUTH2")))
                .andExpect(jsonPath("$.auth.clientId", is("cid")))
                .andExpect(jsonPath("$.auth.scope", is("sc")));
    }

    // --- PUT credenziali ---

    @Test
    void putCredenzialiReturns204AndPersistsWithoutExposing() throws Exception {
        String body = """
                {"subscriptionKey":"sub-123","apiKey":"api-secret","clientSecret":"cs"}""";
        mvc.perform(put("/intermediari/INT-001/connettori/pagopa/credenziali")
                        .with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());

        org.assertj.core.api.Assertions.assertThat(propertyValue(PDD_COD, "SUBSCRIPTION_KEY_VALUE"))
                .isEqualTo("sub-123");
        org.assertj.core.api.Assertions.assertThat(propertyValue(PDD_COD, "API_KEY_AUTH_API_KEY_NAME"))
                .isEqualTo("api-secret");

        mvc.perform(get("/intermediari/INT-001/connettori/pagopa").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptionKey").doesNotExist())
                .andExpect(jsonPath("$.auth.apiKey").doesNotExist());
    }

    @Test
    void putCredenzialiDoesNotTouchConfig() throws Exception {
        seed(PDD_COD, "URL", "https://keep");
        seed(PDD_COD, "ABILITATO", "true");
        String body = """
                {"subscriptionKey":"sub-xyz"}""";
        mvc.perform(put("/intermediari/INT-001/connettori/pagopa/credenziali")
                        .with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());
        org.assertj.core.api.Assertions.assertThat(propertyValue(PDD_COD, "URL")).isEqualTo("https://keep");
    }

    @Test
    void unsupportedChannelReturns404() throws Exception {
        mvc.perform(get("/intermediari/INT-001/connettori/ftp").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void pagopaEtagIgnoresOutOfRepresentationProperty() throws Exception {
        seed(PDD_COD, "URL", "https://x");
        String etagBefore = currentEtag("pagopa");
        // ABILITA_GDE non e' esposto dalla shape pagopa: cambiarlo non deve cambiare l'ETag.
        seed(PDD_COD, "ABILITA_GDE", "true");
        String etagAfter = currentEtag("pagopa");
        org.assertj.core.api.Assertions.assertThat(etagAfter).isEqualTo(etagBefore);
    }

    @Test
    void weakIfMatchIsRejectedOnConfigPut() throws Exception {
        String weak = "W/" + currentEtag("pagopa");
        String body = """
                {"abilitato":true,"urlRPT":"https://x","auth":{"tipoAutenticazione":"NONE"}}""";
        mvc.perform(put("/intermediari/INT-001/connettori/pagopa")
                        .with(httpBasic(PRINCIPAL, PASSWORD)).header("If-Match", weak)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPreconditionFailed());
    }

    private String currentEtag(String canale) throws Exception {
        return mvc.perform(get("/intermediari/INT-001/connettori/" + canale)
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("ETag");
    }

    private String propertyValue(String cod, String key) {
        return connettoreRepository.findByCodConnettore(cod).stream()
                .filter(p -> key.equals(p.getCodProprieta()))
                .map(ConnettoreProprieta::getValore)
                .findFirst().orElse(null);
    }
}
