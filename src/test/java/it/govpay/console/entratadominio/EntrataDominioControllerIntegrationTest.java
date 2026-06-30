package it.govpay.console.entratadominio;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.IbanAccredito;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.TipoTributo;
import it.govpay.console.entity.Tributo;
import it.govpay.console.entity.Utenza;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.repository.GpAuditRepository;
import it.govpay.console.repository.IbanAccreditoRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.TipoTributoRepository;
import it.govpay.console.repository.TributoRepository;
import it.govpay.console.repository.UtenzaRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EntrataDominioControllerIntegrationTest {

    private static final String PRINCIPAL = "operatore1";
    private static final String PASSWORD = "secret";
    private static final String ID_DOMINIO = "12345678901";
    private static final String ID_DOMINIO_2 = "12345678902";
    private static final String IBAN_ACC = "IT60X0542811101000000000001";

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
    private DominioRepository dominioRepository;
    @Autowired
    private TipoTributoRepository tipoTributoRepository;
    @Autowired
    private TributoRepository tributoRepository;
    @Autowired
    private IbanAccreditoRepository ibanRepository;
    @Autowired
    private GpAuditRepository gpAuditRepository;

    private Dominio dominio;
    private TipoTributo tari;
    private TipoTributo imu;
    private TipoTributo tasi;

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

        dominio = saveDominio(ID_DOMINIO, "Comune Alfa");
        saveDominio(ID_DOMINIO_2, "Comune Beta");

        tari = saveTipoTributo("TARI", "Tassa Rifiuti");
        imu = saveTipoTributo("IMU", "Imposta Municipale");
        tasi = saveTipoTributo("TASI", "Tributo Servizi");
        // entrata globale senza alcuna entrata di dominio associata
        saveTipoTributo("COSAP", "Canone Suolo");

        IbanAccredito iban = new IbanAccredito();
        iban.setDominio(dominio);
        iban.setCodIban(IBAN_ACC);
        iban.setPostale(false);
        iban.setAbilitato(true);
        ibanRepository.save(iban);

        newTributo(dominio, tari, true);
        newTributo(dominio, imu, false);
        newTributo(dominio, tasi, true);
    }

    private Dominio saveDominio(String cod, String ragione) {
        Dominio d = new Dominio();
        d.setCodDominio(cod);
        d.setRagioneSociale(ragione);
        d.setAuxDigit(0);
        d.setAbilitato(true);
        d.setIntermediato(true);
        d.setScaricaFr(true);
        return dominioRepository.save(d);
    }

    private TipoTributo saveTipoTributo(String cod, String descrizione) {
        TipoTributo t = new TipoTributo();
        t.setCodTributo(cod);
        t.setDescrizione(descrizione);
        t.setTipoContabilita("0");
        t.setCodContabilita("CAP-" + cod);
        return tipoTributoRepository.save(t);
    }

    private void newTributo(Dominio d, TipoTributo tipoTributo, boolean abilitato) {
        Tributo t = new Tributo();
        t.setDominio(d);
        t.setTipoTributo(tipoTributo);
        t.setAbilitato(abilitato);
        tributoRepository.save(t);
    }

    // --- List ---

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/domini/" + ID_DOMINIO + "/entrate"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void listReturnsEntrateOfDominio() throws Exception {
        mvc.perform(get("/domini/" + ID_DOMINIO + "/entrate").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(3)))
                .andExpect(jsonPath("$.results[*].idEntrata", contains("IMU", "TARI", "TASI")))
                .andExpect(jsonPath("$.results[?(@.idEntrata=='TARI')].descrizione", contains("Tassa Rifiuti")));
    }

    @Test
    void listFilterByDescrizione() throws Exception {
        mvc.perform(get("/domini/" + ID_DOMINIO + "/entrate").param("descrizione", "rifiuti")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].idEntrata", is("TARI")));
    }

    @Test
    void listFilterByIdEntrata() throws Exception {
        mvc.perform(get("/domini/" + ID_DOMINIO + "/entrate").param("idEntrata", "imu")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].idEntrata", is("IMU")));
    }

    @Test
    void listFilterByAbilitato() throws Exception {
        mvc.perform(get("/domini/" + ID_DOMINIO + "/entrate").param("abilitato", "false")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].idEntrata", is("IMU")));
    }

    @Test
    void listTotalTrueIncludesCount() throws Exception {
        mvc.perform(get("/domini/" + ID_DOMINIO + "/entrate").param("total", "true")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalResults", is(3)));
    }

    @Test
    void unknownSortFieldReturns400() throws Exception {
        mvc.perform(get("/domini/" + ID_DOMINIO + "/entrate").param("sort", "-bogus")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("bogus")));
    }

    @Test
    void listUnknownDominioReturns404() throws Exception {
        mvc.perform(get("/domini/99999999999/entrate").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    // --- Get detail ---

    @Test
    void getExistingReturnsDetailWithEtagAndRef() throws Exception {
        mvc.perform(get("/domini/" + ID_DOMINIO + "/entrate/TARI").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.idEntrata", is("TARI")))
                .andExpect(jsonPath("$.abilitato", is(true)))
                .andExpect(jsonPath("$.tipoEntrata.idEntrata", is("TARI")))
                .andExpect(jsonPath("$.tipoEntrata.descrizione", is("Tassa Rifiuti")))
                .andExpect(jsonPath("$.tipoEntrata.tipoContabilita", is("CAPITOLO")))
                .andExpect(jsonPath("$.tipoEntrata.codiceContabilita", is("CAP-TARI")));
    }

    @Test
    void getUnknownReturns404() throws Exception {
        mvc.perform(get("/domini/" + ID_DOMINIO + "/entrate/COSAP").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    // --- Create ---

    @Test
    void createReturns201AndResolvesIbanAndRef() throws Exception {
        String body = """
                {"idEntrata":"COSAP","abilitato":true,"tipoContabilita":"CAPITOLO","codiceContabilita":"CC-1",
                 "ibanAccredito":"%s"}""".formatted(IBAN_ACC);
        mvc.perform(post("/domini/" + ID_DOMINIO + "/entrate").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/entrate/COSAP")))
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.idEntrata", is("COSAP")))
                .andExpect(jsonPath("$.ibanAccredito", is(IBAN_ACC)))
                .andExpect(jsonPath("$.tipoContabilita", is("CAPITOLO")))
                .andExpect(jsonPath("$.tipoEntrata.descrizione", is("Canone Suolo")));

        Tributo created = tributoRepository
                .findByDominio_IdAndTipoTributo_CodTributo(dominio.getId(), "COSAP").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(created.getCodiceContabilita()).isEqualTo("CC-1");
        org.assertj.core.api.Assertions.assertThat(created.getIbanAccredito()).isNotNull();
    }

    @Test
    void createWithUnknownGlobalEntrataReturns422() throws Exception {
        String body = """
                {"idEntrata":"NOPE","abilitato":true}""";
        mvc.perform(post("/domini/" + ID_DOMINIO + "/entrate").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail", containsString("NOPE")));
    }

    @Test
    void createDuplicateReturns409() throws Exception {
        String body = """
                {"idEntrata":"TARI","abilitato":true}""";
        mvc.perform(post("/domini/" + ID_DOMINIO + "/entrate").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void createWithUnknownIbanReturns422() throws Exception {
        String body = """
                {"idEntrata":"COSAP","abilitato":true,"ibanAccredito":"IT60X0542811101000000000099"}""";
        mvc.perform(post("/domini/" + ID_DOMINIO + "/entrate").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail", containsString("ibanAccredito")));
    }

    @Test
    void createUnknownDominioReturns404() throws Exception {
        String body = """
                {"idEntrata":"TARI","abilitato":true}""";
        mvc.perform(post("/domini/99999999999/entrate").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void createSameEntrataOnDifferentDominioSucceeds() throws Exception {
        // unicita' e' per (id_dominio, id_tipo_tributo): la stessa entrata su un dominio diverso e' lecita.
        String body = """
                {"idEntrata":"TARI","abilitato":true}""";
        mvc.perform(post("/domini/" + ID_DOMINIO_2 + "/entrate").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void createWritesAudit() throws Exception {
        long before = countAudit(EntrataDominioService.AZIONE_AUDIT_CREATE);
        String body = """
                {"idEntrata":"COSAP","abilitato":true}""";
        mvc.perform(post("/domini/" + ID_DOMINIO + "/entrate").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        org.assertj.core.api.Assertions.assertThat(countAudit(EntrataDominioService.AZIONE_AUDIT_CREATE))
                .isEqualTo(before + 1);
    }

    // --- Replace ---

    @Test
    void replaceUpdates() throws Exception {
        String etag = currentEtag("TARI");
        String body = """
                {"abilitato":false,"tipoContabilita":"SIOPE","codiceContabilita":"CC-NEW","ibanAccredito":"%s"}"""
                .formatted(IBAN_ACC);
        String newEtag = mvc.perform(put("/domini/" + ID_DOMINIO + "/entrate/TARI")
                        .with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abilitato", is(false)))
                .andExpect(jsonPath("$.tipoContabilita", is("SIOPE")))
                .andExpect(jsonPath("$.ibanAccredito", is(IBAN_ACC)))
                .andReturn().getResponse().getHeader("ETag");
        org.assertj.core.api.Assertions.assertThat(newEtag).isNotEqualTo(etag);
    }

    @Test
    void replaceWithUnknownIbanReturns422() throws Exception {
        String etag = currentEtag("TARI");
        String body = """
                {"abilitato":true,"ibanAccredito":"IT60X0542811101000000000099"}""";
        mvc.perform(put("/domini/" + ID_DOMINIO + "/entrate/TARI").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void replaceWithoutIfMatchReturns428() throws Exception {
        String body = """
                {"abilitato":true}""";
        mvc.perform(put("/domini/" + ID_DOMINIO + "/entrate/TARI").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPreconditionRequired());
    }

    @Test
    void replaceWithWrongIfMatchReturns412() throws Exception {
        String body = """
                {"abilitato":true}""";
        mvc.perform(put("/domini/" + ID_DOMINIO + "/entrate/TARI").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", "\"deadbeef\"")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void replaceUnknownReturns404() throws Exception {
        String body = """
                {"abilitato":true}""";
        mvc.perform(put("/domini/" + ID_DOMINIO + "/entrate/COSAP").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", "*")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    // --- Patch ---

    @Test
    void patchAbilitato() throws Exception {
        String etag = currentEtag("TARI");
        String p = """
                [{"op":"replace","path":"/abilitato","value":false}]""";
        mvc.perform(patch("/domini/" + ID_DOMINIO + "/entrate/TARI").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(JSON_PATCH).content(p))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abilitato", is(false)))
                .andExpect(jsonPath("$.tipoEntrata.idEntrata", is("TARI")));
    }

    @Test
    void patchChangingIdEntrataReturns400() throws Exception {
        String etag = currentEtag("TARI");
        String p = """
                [{"op":"replace","path":"/idEntrata","value":"IMU"}]""";
        mvc.perform(patch("/domini/" + ID_DOMINIO + "/entrate/TARI").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(JSON_PATCH).content(p))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("idEntrata")));
    }

    @Test
    void patchChangingTipoEntrataReturns400() throws Exception {
        String etag = currentEtag("TARI");
        String p = """
                [{"op":"replace","path":"/tipoEntrata/descrizione","value":"Altro"}]""";
        mvc.perform(patch("/domini/" + ID_DOMINIO + "/entrate/TARI").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(JSON_PATCH).content(p))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("tipoEntrata")));
    }

    private String currentEtag(String idEntrata) throws Exception {
        return mvc.perform(get("/domini/" + ID_DOMINIO + "/entrate/" + idEntrata)
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("ETag");
    }

    private long countAudit(String azione) {
        return gpAuditRepository.findAll().stream()
                .filter(a -> azione.equals(a.getTipoOggetto()))
                .count();
    }
}
