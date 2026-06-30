package it.govpay.console.tipopendenzadominio;

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
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.TipoVersamento;
import it.govpay.console.entity.TipoVersamentoDominio;
import it.govpay.console.entity.Utenza;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.repository.GpAuditRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.TipoVersamentoDominioRepository;
import it.govpay.console.repository.TipoVersamentoRepository;
import it.govpay.console.repository.UtenzaRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TipoPendenzaDominioControllerIntegrationTest {

    private static final String PRINCIPAL = "operatore1";
    private static final String PASSWORD = "secret";
    private static final String ID_DOMINIO = "12345678901";
    private static final String ID_DOMINIO_2 = "12345678902";

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
    private TipoVersamentoRepository tipoVersamentoRepository;
    @Autowired
    private TipoVersamentoDominioRepository tvdRepository;
    @Autowired
    private GpAuditRepository gpAuditRepository;

    private Dominio dominio;
    private TipoVersamento tari;
    private TipoVersamento imu;
    private TipoVersamento tasi;

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

        tari = saveTipoVersamento("TARI", "Tassa Rifiuti");
        imu = saveTipoVersamento("IMU", "Imposta Municipale");
        tasi = saveTipoVersamento("TASI", "Tributo Servizi");
        saveTipoVersamento("COSAP", "Canone Suolo");

        newTvd(dominio, tari, true);
        newTvd(dominio, imu, false);
        newTvd(dominio, tasi, true);
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

    private TipoVersamento saveTipoVersamento(String cod, String descrizione) {
        TipoVersamento t = new TipoVersamento();
        t.setCodTipoVersamento(cod);
        t.setDescrizione(descrizione);
        return tipoVersamentoRepository.save(t);
    }

    private void newTvd(Dominio d, TipoVersamento tv, boolean abilitato) {
        TipoVersamentoDominio tvd = new TipoVersamentoDominio();
        tvd.setDominio(d);
        tvd.setTipoVersamento(tv);
        tvd.setAbilitato(abilitato);
        tvdRepository.save(tvd);
    }

    // --- List ---

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/domini/" + ID_DOMINIO + "/tipiPendenza"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void listReturnsTipiOfDominio() throws Exception {
        mvc.perform(get("/domini/" + ID_DOMINIO + "/tipiPendenza").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(3)))
                .andExpect(jsonPath("$.results[*].idTipoPendenza", contains("IMU", "TARI", "TASI")))
                .andExpect(jsonPath("$.results[?(@.idTipoPendenza=='TARI')].descrizione", contains("Tassa Rifiuti")));
    }

    @Test
    void listFilterByDescrizione() throws Exception {
        mvc.perform(get("/domini/" + ID_DOMINIO + "/tipiPendenza").param("descrizione", "rifiuti")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].idTipoPendenza", is("TARI")));
    }

    @Test
    void listFilterByAbilitato() throws Exception {
        mvc.perform(get("/domini/" + ID_DOMINIO + "/tipiPendenza").param("abilitato", "false")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].idTipoPendenza", is("IMU")));
    }

    @Test
    void listTotalTrueIncludesCount() throws Exception {
        mvc.perform(get("/domini/" + ID_DOMINIO + "/tipiPendenza").param("total", "true")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalResults", is(3)));
    }

    @Test
    void unknownSortFieldReturns400() throws Exception {
        mvc.perform(get("/domini/" + ID_DOMINIO + "/tipiPendenza").param("sort", "-bogus")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("bogus")));
    }

    @Test
    void listUnknownDominioReturns404() throws Exception {
        mvc.perform(get("/domini/99999999999/tipiPendenza").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    // --- Get detail ---

    @Test
    void getExistingReturnsDetailWithEtagAndRef() throws Exception {
        mvc.perform(get("/domini/" + ID_DOMINIO + "/tipiPendenza/TARI").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.idTipoPendenza", is("TARI")))
                .andExpect(jsonPath("$.abilitato", is(true)))
                .andExpect(jsonPath("$.tipoPendenza.idTipoPendenza", is("TARI")))
                .andExpect(jsonPath("$.tipoPendenza.descrizione", is("Tassa Rifiuti")));
    }

    @Test
    void getUnknownReturns404() throws Exception {
        mvc.perform(get("/domini/" + ID_DOMINIO + "/tipiPendenza/COSAP").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    // --- Create ---

    @Test
    void createReturns201WithNestedConfigAndRef() throws Exception {
        String body = """
                {"idTipoPendenza":"COSAP","abilitato":true,"pagaTerzi":false,"codificaIUV":"3",
                 "portaleBackoffice":{"abilitato":true,"inoltro":"APP-1"},
                 "avvisaturaAppIO":{"apiKey":"io-key-123",
                   "promemoriaAvviso":{"abilitato":true}}}""";
        mvc.perform(post("/domini/" + ID_DOMINIO + "/tipiPendenza").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/tipiPendenza/COSAP")))
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.idTipoPendenza", is("COSAP")))
                .andExpect(jsonPath("$.codificaIUV", is("3")))
                .andExpect(jsonPath("$.portaleBackoffice.abilitato", is(true)))
                .andExpect(jsonPath("$.portaleBackoffice.inoltro", is("APP-1")))
                .andExpect(jsonPath("$.avvisaturaAppIO.apiKey", is("io-key-123")))
                .andExpect(jsonPath("$.avvisaturaAppIO.promemoriaAvviso.abilitato", is(true)))
                .andExpect(jsonPath("$.tipoPendenza.descrizione", is("Canone Suolo")));

        TipoVersamentoDominio created = tvdRepository
                .findByDominio_IdAndTipoVersamento_CodTipoVersamento(dominio.getId(), "COSAP").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(created.getAppIoApiKey()).isEqualTo("io-key-123");
        org.assertj.core.api.Assertions.assertThat(created.getBoCodApplicazione()).isEqualTo("APP-1");
    }

    @Test
    void createWithUnknownGlobalReturns422() throws Exception {
        String body = """
                {"idTipoPendenza":"NOPE","abilitato":true}""";
        mvc.perform(post("/domini/" + ID_DOMINIO + "/tipiPendenza").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail", containsString("NOPE")));
    }

    @Test
    void createDuplicateReturns409() throws Exception {
        String body = """
                {"idTipoPendenza":"TARI","abilitato":true}""";
        mvc.perform(post("/domini/" + ID_DOMINIO + "/tipiPendenza").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void createUnknownDominioReturns404() throws Exception {
        String body = """
                {"idTipoPendenza":"TARI","abilitato":true}""";
        mvc.perform(post("/domini/99999999999/tipiPendenza").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void createSameOnDifferentDominioSucceeds() throws Exception {
        String body = """
                {"idTipoPendenza":"TARI","abilitato":true}""";
        mvc.perform(post("/domini/" + ID_DOMINIO_2 + "/tipiPendenza").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void createWritesAudit() throws Exception {
        long before = countAudit(TipoPendenzaDominioService.AZIONE_AUDIT_CREATE);
        String body = """
                {"idTipoPendenza":"COSAP","abilitato":true}""";
        mvc.perform(post("/domini/" + ID_DOMINIO + "/tipiPendenza").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        org.assertj.core.api.Assertions.assertThat(countAudit(TipoPendenzaDominioService.AZIONE_AUDIT_CREATE))
                .isEqualTo(before + 1);
    }

    // --- Replace ---

    @Test
    void replaceUpdates() throws Exception {
        String etag = currentEtag("TARI");
        String body = """
                {"abilitato":false,"codificaIUV":"9","portalePagamento":{"abilitato":true}}""";
        String newEtag = mvc.perform(put("/domini/" + ID_DOMINIO + "/tipiPendenza/TARI")
                        .with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abilitato", is(false)))
                .andExpect(jsonPath("$.codificaIUV", is("9")))
                .andExpect(jsonPath("$.portalePagamento.abilitato", is(true)))
                .andReturn().getResponse().getHeader("ETag");
        org.assertj.core.api.Assertions.assertThat(newEtag).isNotEqualTo(etag);
    }

    @Test
    void replaceWithoutIfMatchReturns428() throws Exception {
        String body = """
                {"abilitato":true}""";
        mvc.perform(put("/domini/" + ID_DOMINIO + "/tipiPendenza/TARI").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPreconditionRequired());
    }

    @Test
    void replaceWithWrongIfMatchReturns412() throws Exception {
        String body = """
                {"abilitato":true}""";
        mvc.perform(put("/domini/" + ID_DOMINIO + "/tipiPendenza/TARI").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", "\"deadbeef\"")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void replaceUnknownReturns404() throws Exception {
        String body = """
                {"abilitato":true}""";
        mvc.perform(put("/domini/" + ID_DOMINIO + "/tipiPendenza/COSAP").with(httpBasic(PRINCIPAL, PASSWORD))
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
        mvc.perform(patch("/domini/" + ID_DOMINIO + "/tipiPendenza/TARI").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(JSON_PATCH).content(p))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abilitato", is(false)))
                .andExpect(jsonPath("$.tipoPendenza.idTipoPendenza", is("TARI")));
    }

    @Test
    void patchChangingIdReturns400() throws Exception {
        String etag = currentEtag("TARI");
        String p = """
                [{"op":"replace","path":"/idTipoPendenza","value":"IMU"}]""";
        mvc.perform(patch("/domini/" + ID_DOMINIO + "/tipiPendenza/TARI").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(JSON_PATCH).content(p))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("idTipoPendenza")));
    }

    @Test
    void patchChangingTipoPendenzaReturns400() throws Exception {
        String etag = currentEtag("TARI");
        String p = """
                [{"op":"replace","path":"/tipoPendenza/descrizione","value":"Altro"}]""";
        mvc.perform(patch("/domini/" + ID_DOMINIO + "/tipiPendenza/TARI").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(JSON_PATCH).content(p))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("tipoPendenza")));
    }

    private String currentEtag(String idTipoPendenza) throws Exception {
        return mvc.perform(get("/domini/" + ID_DOMINIO + "/tipiPendenza/" + idTipoPendenza)
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
