package it.govpay.console.connettoredominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

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
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.JppaConfig;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Utenza;
import it.govpay.console.repository.ConnettoreProprietaRepository;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.repository.GpAuditRepository;
import it.govpay.console.repository.JppaConfigRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.UtenzaRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ConnettoreDominioControllerIntegrationTest {

    private static final String PRINCIPAL = "operatore1";
    private static final String PASSWORD = "secret";
    private static final String ID_DOMINIO = "12345678901";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private GovpayPasswordEncoder encoder;
    @Autowired
    private UtenzaRepository utenzaRepository;
    @Autowired
    private OperatoreRepository operatoreRepository;
    @Autowired
    private DominioRepository dominioRepository;
    @Autowired
    private ConnettoreProprietaRepository connettoreRepository;
    @Autowired
    private JppaConfigRepository jppaConfigRepository;
    @Autowired
    private GpAuditRepository gpAuditRepository;

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

        Dominio d = new Dominio();
        d.setCodDominio(ID_DOMINIO);
        d.setRagioneSociale("Comune Alfa");
        d.setAuxDigit(0);
        d.setAbilitato(true);
        d.setIntermediato(true);
        d.setScaricaFr(true);
        dominioRepository.save(d);
    }

    // --- Auth + routing ---

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/domini/" + ID_DOMINIO + "/connettori/mypivot"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void allFiveChannelsAreRoutableAndEmpty() throws Exception {
        for (String c : new String[] {"mypivot", "secim", "govpay", "hypersic-apk", "maggioli-jppa"}) {
            mvc.perform(get("/domini/" + ID_DOMINIO + "/connettori/" + c).with(httpBasic(PRINCIPAL, PASSWORD)))
                    .andExpect(status().isOk())
                    .andExpect(header().exists("ETag"))
                    .andExpect(jsonPath("$.abilitato", is(false)));
        }
    }

    @Test
    void getUnknownDominioReturns404() throws Exception {
        mvc.perform(get("/domini/99999999999/connettori/mypivot").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    // --- mypivot round-trip + storage fedele a V1 ---

    @Test
    void replaceMypivotPersistsV1EncodingAndGeneratesCodConnettore() throws Exception {
        String etag = currentEtag("mypivot");
        String body = """
                {"abilitato":true,"codiceIPA":"c_ente","tipoConnettore":"FILESYSTEM","versioneCsv":"1.0",
                 "emailIndirizzi":["a@x.it","b@x.it"],"fileSystemPath":"/tmp/out",
                 "tipiPendenza":["TARI","TOSAP"],"intervalloCreazioneTracciato":24}""";
        mvc.perform(put("/domini/" + ID_DOMINIO + "/connettori/mypivot")
                        .with(httpBasic(PRINCIPAL, PASSWORD)).header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abilitato", is(true)))
                .andExpect(jsonPath("$.codiceIPA", is("c_ente")))
                .andExpect(jsonPath("$.tipoConnettore", is("FILESYSTEM")))
                .andExpect(jsonPath("$.emailIndirizzi[0]", is("a@x.it")))
                .andExpect(jsonPath("$.emailIndirizzi[1]", is("b@x.it")))
                .andExpect(jsonPath("$.tipiPendenza[1]", is("TOSAP")))
                .andExpect(jsonPath("$.intervalloCreazioneTracciato", is(24)));

        String cod = "DOM_" + ID_DOMINIO + "_MYPIVOT";
        assertThat(dominioRepository.findByCodDominio(ID_DOMINIO).orElseThrow().getCodConnettoreMyPivot())
                .isEqualTo(cod);
        assertThat(propertyValue(cod, "TIPO_CONNETTORE")).isEqualTo("FILE_SYSTEM");
        assertThat(propertyValue(cod, "TIPO_TRACCIATO")).isEqualTo("MYPIVOT");
        assertThat(propertyValue(cod, "EMAIL_INDIRIZZO")).isEqualTo("a@x.it,b@x.it");
        assertThat(propertyValue(cod, "TIPI_PENDENZA")).isEqualTo("TARI,TOSAP");
        assertThat(propertyValue(cod, "INTERV_CREAZ_TRAC")).isEqualTo("24");
    }

    @Test
    void replaceSecimStoresCodiceClienteEIstituto() throws Exception {
        String etag = currentEtag("secim");
        String body = """
                {"abilitato":true,"codiceCliente":"CL01","codiceIstituto":"IST9","tipoConnettore":"EMAIL",
                 "versioneCsv":"7.0","intervalloCreazioneTracciato":12}""";
        mvc.perform(put("/domini/" + ID_DOMINIO + "/connettori/secim")
                        .with(httpBasic(PRINCIPAL, PASSWORD)).header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codiceCliente", is("CL01")))
                .andExpect(jsonPath("$.codiceIstituto", is("IST9")));
        String cod = "DOM_" + ID_DOMINIO + "_SECIM";
        assertThat(propertyValue(cod, "CODICE_CLIENTE")).isEqualTo("CL01");
        assertThat(propertyValue(cod, "CODICE_ISTITUTO")).isEqualTo("IST9");
    }

    // --- govpay REST + credenziali ---

    @Test
    void replaceGovpayRestRoundTripsWithVersioneApiAndContenuti() throws Exception {
        String etag = currentEtag("govpay");
        String body = """
                {"abilitato":true,"tipoConnettore":"REST","versioneZip":"7.0","url":"https://rest.ec",
                 "versioneApi":"REST v1","contenuti":["SINTESI_PAGAMENTI","RPP"],
                 "auth":{"tipoAutenticazione":"OAUTH2","clientId":"cid","scope":"sc","urlTokenEndpoint":"https://t"},
                 "intervalloCreazioneTracciato":6}""";
        mvc.perform(put("/domini/" + ID_DOMINIO + "/connettori/govpay")
                        .with(httpBasic(PRINCIPAL, PASSWORD)).header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoConnettore", is("REST")))
                .andExpect(jsonPath("$.versioneApi", is("REST v1")))
                .andExpect(jsonPath("$.contenuti[0]", is("SINTESI_PAGAMENTI")))
                .andExpect(jsonPath("$.auth.tipoAutenticazione", is("OAUTH2")))
                .andExpect(jsonPath("$.auth.clientId", is("cid")));

        String cod = "DOM_" + ID_DOMINIO + "_GOVPAY";
        assertThat(propertyValue(cod, "VERSIONE")).isEqualTo("REST_1");
        assertThat(propertyValue(cod, "VERSIONE_CSV")).isEqualTo("7.0");
        assertThat(propertyValue(cod, "CONTENUTI")).isEqualTo("SINTESI_PAGAMENTI,RPP");
        assertThat(propertyValue(cod, "TIPOAUTENTICAZIONE")).isEqualTo("OAUTH2_CLIENT_CREDENTIALS");
    }

    @Test
    void govpayCredenzialiPersistWriteOnly() throws Exception {
        String body = """
                {"clientSecret":"top-secret","subscriptionKey":"sub-1"}""";
        mvc.perform(put("/domini/" + ID_DOMINIO + "/connettori/govpay/credenziali")
                        .with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());

        String cod = "DOM_" + ID_DOMINIO + "_GOVPAY";
        assertThat(propertyValue(cod, "OAUTH2_CLIENT_CREDENTIALS_CLIENT_SECRET_NAME")).isEqualTo("top-secret");
        assertThat(propertyValue(cod, "SUBSCRIPTION_KEY_VALUE")).isEqualTo("sub-1");

        mvc.perform(get("/domini/" + ID_DOMINIO + "/connettori/govpay").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.auth.clientSecret").doesNotExist())
                .andExpect(jsonPath("$.subscriptionKey").doesNotExist());
    }

    @Test
    void hypersicApkRoundTrips() throws Exception {
        String etag = currentEtag("hypersic-apk");
        String body = """
                {"abilitato":true,"tipoConnettore":"EMAIL","versioneCsv":"2.0",
                 "emailIndirizzi":["h@x.it"],"emailSubject":"esiti","intervalloCreazioneTracciato":48}""";
        mvc.perform(put("/domini/" + ID_DOMINIO + "/connettori/hypersic-apk")
                        .with(httpBasic(PRINCIPAL, PASSWORD)).header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailSubject", is("esiti")))
                .andExpect(jsonPath("$.emailIndirizzi[0]", is("h@x.it")));
        assertThat(dominioRepository.findByCodDominio(ID_DOMINIO).orElseThrow().getCodConnettoreHyperSicApk())
                .isEqualTo("DOM_" + ID_DOMINIO + "_HYPER_SIC_APKAPPA");
    }

    // --- maggioli-jppa: jppa_config + preservazione data_ultima_rt ---

    @Test
    void replaceMaggioliCreatesJppaConfigAndReadsAbilitatoFromIt() throws Exception {
        String etag = currentEtag("maggioli-jppa");
        String body = """
                {"abilitato":true,"inviaTracciatoEsito":true,"url":"https://jppa.ec",
                 "auth":{"tipoAutenticazione":"HTTPBASIC","username":"u"}}""";
        mvc.perform(put("/domini/" + ID_DOMINIO + "/connettori/maggioli-jppa")
                        .with(httpBasic(PRINCIPAL, PASSWORD)).header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abilitato", is(true)))
                .andExpect(jsonPath("$.inviaTracciatoEsito", is(true)))
                .andExpect(jsonPath("$.dataUltimaRT").doesNotExist());

        String cod = "DOM_" + ID_DOMINIO + "_MAGGIOLI_JPPA";
        JppaConfig jc = jppaConfigRepository.findByCodDominio(ID_DOMINIO).orElseThrow();
        assertThat(jc.getCodConnettore()).isEqualTo(cod);
        assertThat(jc.isAbilitato()).isTrue();
        assertThat(propertyValue(cod, "URL")).isEqualTo("https://jppa.ec");
        assertThat(propertyValue(cod, "TIPOAUTENTICAZIONE")).isEqualTo("HTTPBasic");
    }

    @Test
    void replaceMaggioliPreservesDataUltimaRt() throws Exception {
        OffsetDateTime rt = OffsetDateTime.of(2020, 12, 31, 12, 34, 0, 0, ZoneOffset.UTC);
        JppaConfig seed = new JppaConfig();
        seed.setCodDominio(ID_DOMINIO);
        seed.setIdDominio(dominioRepository.findByCodDominio(ID_DOMINIO).orElseThrow().getId());
        seed.setCodConnettore("DOM_" + ID_DOMINIO + "_MAGGIOLI_JPPA");
        seed.setAbilitato(false);
        seed.setDataUltimaRt(rt);
        jppaConfigRepository.saveAndFlush(seed);

        String etag = currentEtag("maggioli-jppa");
        String body = """
                {"abilitato":true,"url":"https://jppa.ec","auth":{"tipoAutenticazione":"NONE"}}""";
        mvc.perform(put("/domini/" + ID_DOMINIO + "/connettori/maggioli-jppa")
                        .with(httpBasic(PRINCIPAL, PASSWORD)).header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        JppaConfig reloaded = jppaConfigRepository.findByCodDominio(ID_DOMINIO).orElseThrow();
        assertThat(reloaded.isAbilitato()).isTrue();
        assertThat(reloaded.getDataUltimaRt()).isNotNull();
        assertThat(reloaded.getDataUltimaRt().toInstant()).isEqualTo(rt.toInstant());
    }

    // --- concorrenza + audit ---

    @Test
    void replaceWithoutIfMatchReturns428() throws Exception {
        String body = """
                {"abilitato":true,"tipoConnettore":"EMAIL","intervalloCreazioneTracciato":24}""";
        mvc.perform(put("/domini/" + ID_DOMINIO + "/connettori/mypivot")
                        .with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPreconditionRequired());
    }

    @Test
    void replaceWithWrongIfMatchReturns412() throws Exception {
        String body = """
                {"abilitato":true,"tipoConnettore":"EMAIL","intervalloCreazioneTracciato":24}""";
        mvc.perform(put("/domini/" + ID_DOMINIO + "/connettori/mypivot")
                        .with(httpBasic(PRINCIPAL, PASSWORD)).header("If-Match", "\"deadbeef\"")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void replaceWritesAudit() throws Exception {
        long before = countAudit(ConnettoreDominioService.AZIONE_AUDIT_MODIFICA);
        String etag = currentEtag("mypivot");
        String body = """
                {"abilitato":true,"tipoConnettore":"EMAIL","intervalloCreazioneTracciato":24}""";
        mvc.perform(put("/domini/" + ID_DOMINIO + "/connettori/mypivot")
                        .with(httpBasic(PRINCIPAL, PASSWORD)).header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        assertThat(countAudit(ConnettoreDominioService.AZIONE_AUDIT_MODIFICA)).isEqualTo(before + 1);
    }

    @Test
    void configPutDoesNotTouchCredentials() throws Exception {
        seed("DOM_" + ID_DOMINIO + "_GOVPAY", "OAUTH2_CLIENT_CREDENTIALS_CLIENT_SECRET_NAME", "keep");
        dominioRepository.findByCodDominio(ID_DOMINIO).ifPresent(d -> {
            d.setCodConnettoreGovPay("DOM_" + ID_DOMINIO + "_GOVPAY");
            dominioRepository.save(d);
        });
        String etag = currentEtag("govpay");
        String bodyReq = """
                {"abilitato":true,"tipoConnettore":"REST","url":"https://x","auth":{"tipoAutenticazione":"NONE"},
                 "intervalloCreazioneTracciato":6}""";
        mvc.perform(put("/domini/" + ID_DOMINIO + "/connettori/govpay")
                        .with(httpBasic(PRINCIPAL, PASSWORD)).header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON).content(bodyReq))
                .andExpect(status().isOk());
        assertThat(propertyValue("DOM_" + ID_DOMINIO + "_GOVPAY", "OAUTH2_CLIENT_CREDENTIALS_CLIENT_SECRET_NAME"))
                .isEqualTo("keep");
    }

    // --- helpers ---

    private String currentEtag(String canale) throws Exception {
        return mvc.perform(get("/domini/" + ID_DOMINIO + "/connettori/" + canale)
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("ETag");
    }

    private void seed(String cod, String key, String value) {
        connettoreRepository.save(new ConnettoreProprieta(cod, key, value));
    }

    private String propertyValue(String cod, String key) {
        return connettoreRepository.findByCodConnettore(cod).stream()
                .filter(p -> key.equals(p.getCodProprieta()))
                .map(ConnettoreProprieta::getValore)
                .findFirst().orElse(null);
    }

    private long countAudit(String azione) {
        return gpAuditRepository.findAll().stream()
                .filter(a -> azione.equals(a.getTipoOggetto()))
                .count();
    }
}
