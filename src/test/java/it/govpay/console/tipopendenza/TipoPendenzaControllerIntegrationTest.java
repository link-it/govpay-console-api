package it.govpay.console.tipopendenza;

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
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.TipoVersamento;
import it.govpay.console.entity.Utenza;
import it.govpay.console.repository.GpAuditRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.TipoVersamentoRepository;
import it.govpay.console.repository.UtenzaRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TipoPendenzaControllerIntegrationTest {

    private static final String PRINCIPAL = "operatore1";
    private static final String PASSWORD = "secret";

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
    private TipoVersamentoRepository tipoVersamentoRepository;
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

        newTipo("IMU", "Imposta municipale", true);
        newTipo("TARI", "Tassa rifiuti", false);
        newTipo("TOSAP", "Occupazione suolo", true);
    }

    private void newTipo(String cod, String descr, boolean abilitato) {
        TipoVersamento t = new TipoVersamento();
        t.setCodTipoVersamento(cod);
        t.setDescrizione(descr);
        t.setPagaTerzi(false);
        t.setAbilitato(abilitato);
        tipoVersamentoRepository.save(t);
    }

    // --- List ---

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/tipiPendenza"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void listDefaultIsSliceWithoutTotals() throws Exception {
        mvc.perform(get("/tipiPendenza").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(3)))
                .andExpect(jsonPath("$.pagination.hasNextPage", is(false)))
                .andExpect(jsonPath("$.pagination.totalResults").doesNotExist());
    }

    @Test
    void listTotalTrueIncludesCount() throws Exception {
        mvc.perform(get("/tipiPendenza").param("total", "true").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalResults", is(3)))
                .andExpect(jsonPath("$.pagination.totalPages", is(1)));
    }

    @Test
    void filterByDescrizionePartial() throws Exception {
        mvc.perform(get("/tipiPendenza").param("descrizione", "rifiuti").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].idTipoPendenza", is("TARI")));
    }

    @Test
    void filterByAbilitato() throws Exception {
        mvc.perform(get("/tipiPendenza").param("abilitato", "false").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].idTipoPendenza", is("TARI")));
    }

    @Test
    void defaultSortByIdAsc() throws Exception {
        mvc.perform(get("/tipiPendenza").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idTipoPendenza", contains("IMU", "TARI", "TOSAP")));
    }

    @Test
    void unknownSortFieldReturns400() throws Exception {
        mvc.perform(get("/tipiPendenza").param("sort", "-bogus").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.detail", containsString("bogus")));
    }

    // --- Get detail ---

    @Test
    void getExistingReturnsDetailWithEtag() throws Exception {
        mvc.perform(get("/tipiPendenza/IMU").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.idTipoPendenza", is("IMU")))
                .andExpect(jsonPath("$.descrizione", is("Imposta municipale")))
                .andExpect(jsonPath("$.abilitato", is(true)))
                .andExpect(jsonPath("$.portaleBackoffice.abilitato", is(false)))
                .andExpect(jsonPath("$.avvisaturaMail.promemoriaAvviso.abilitato", is(false)));
    }

    @Test
    void getUnknownReturns404() throws Exception {
        mvc.perform(get("/tipiPendenza/NOPE").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType("application/problem+json"));
    }

    // --- Create (con config annidata completa) ---

    @Test
    void createWithFullNestedConfigRoundTrips() throws Exception {
        String body = """
                {
                  "idTipoPendenza":"COSAP",
                  "descrizione":"Canone occupazione",
                  "codificaIUV":"01",
                  "pagaTerzi":true,
                  "abilitato":true,
                  "portaleBackoffice":{
                    "abilitato":true,
                    "form":{"tipo":"angular","definizione":{"campi":["a","b"]}},
                    "validazione":{"type":"object"},
                    "trasformazione":{"tipo":"freemarker","definizione":{"tpl":"x"}},
                    "inoltro":"IDA2A01"
                  },
                  "avvisaturaMail":{
                    "promemoriaAvviso":{"abilitato":true,"tipo":"freemarker","oggetto":{"t":"o"},"messaggio":{"t":"m"},"allegaPdf":true},
                    "promemoriaScadenza":{"abilitato":true,"preavviso":5}
                  },
                  "visualizzazione":{"layout":"compact"},
                  "tracciatoCsv":{"tipo":"freemarker","intestazione":"a,b,c","richiesta":{"r":1},"risposta":{"s":2}}
                }""";
        mvc.perform(post("/tipiPendenza").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/tipiPendenza/COSAP")))
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.codificaIUV", is("01")))
                .andExpect(jsonPath("$.pagaTerzi", is(true)))
                .andExpect(jsonPath("$.portaleBackoffice.abilitato", is(true)))
                .andExpect(jsonPath("$.portaleBackoffice.form.tipo", is("angular")))
                .andExpect(jsonPath("$.portaleBackoffice.form.definizione.campi[0]", is("a")))
                .andExpect(jsonPath("$.portaleBackoffice.trasformazione.tipo", is("freemarker")))
                .andExpect(jsonPath("$.portaleBackoffice.inoltro", is("IDA2A01")))
                .andExpect(jsonPath("$.avvisaturaMail.promemoriaAvviso.allegaPdf", is(true)))
                .andExpect(jsonPath("$.avvisaturaMail.promemoriaScadenza.preavviso", is(5)))
                .andExpect(jsonPath("$.visualizzazione.layout", is("compact")))
                .andExpect(jsonPath("$.tracciatoCsv.intestazione", is("a,b,c")))
                .andExpect(jsonPath("$.tracciatoCsv.richiesta.r", is(1)));
    }

    @Test
    void createMinimalReturns201() throws Exception {
        String body = """
                {"idTipoPendenza":"MIN","descrizione":"Minimale"}""";
        mvc.perform(post("/tipiPendenza").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pagaTerzi", is(false)))
                .andExpect(jsonPath("$.abilitato", is(true)))
                .andExpect(jsonPath("$.tracciatoCsv").doesNotExist());
    }

    @Test
    void createDuplicateReturns409() throws Exception {
        String body = """
                {"idTipoPendenza":"IMU","descrizione":"Dup"}""";
        mvc.perform(post("/tipiPendenza").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void createWithMissingDescrizioneReturns400() throws Exception {
        String body = """
                {"idTipoPendenza":"NODESC"}""";
        mvc.perform(post("/tipiPendenza").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void createInvalidCodificaIuvReturns400() throws Exception {
        String body = """
                {"idTipoPendenza":"BADIUV","descrizione":"X","codificaIUV":"ABCD"}""";
        mvc.perform(post("/tipiPendenza").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWritesAudit() throws Exception {
        long before = countAudit("TIPO_PENDENZA_CREATE");
        String body = """
                {"idTipoPendenza":"AUD","descrizione":"Aud"}""";
        mvc.perform(post("/tipiPendenza").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        org.assertj.core.api.Assertions.assertThat(countAudit("TIPO_PENDENZA_CREATE")).isEqualTo(before + 1);
    }

    // --- Replace ---

    @Test
    void replaceWithCorrectIfMatchSucceeds() throws Exception {
        String etag = currentEtag("IMU");
        String body = """
                {"descrizione":"Imposta agg","abilitato":false,"portalePagamento":{"abilitato":true}}""";
        String newEtag = mvc.perform(put("/tipiPendenza/IMU").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descrizione", is("Imposta agg")))
                .andExpect(jsonPath("$.abilitato", is(false)))
                .andExpect(jsonPath("$.portalePagamento.abilitato", is(true)))
                .andReturn().getResponse().getHeader("ETag");
        org.assertj.core.api.Assertions.assertThat(newEtag).isNotEqualTo(etag);
    }

    @Test
    void replaceWithoutIfMatchReturns428() throws Exception {
        String body = """
                {"descrizione":"X"}""";
        mvc.perform(put("/tipiPendenza/IMU").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPreconditionRequired());
    }

    @Test
    void replaceWithWrongIfMatchReturns412() throws Exception {
        String body = """
                {"descrizione":"X"}""";
        mvc.perform(put("/tipiPendenza/IMU").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", "\"deadbeef\"")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void replaceUnknownReturns404() throws Exception {
        String body = """
                {"descrizione":"X"}""";
        mvc.perform(put("/tipiPendenza/NOPE").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", "*")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    // --- Patch ---

    @Test
    void patchReplaceDescrizioneSucceeds() throws Exception {
        String etag = currentEtag("IMU");
        String p = """
                [{"op":"replace","path":"/descrizione","value":"Imposta patchata"}]""";
        mvc.perform(patch("/tipiPendenza/IMU").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(JSON_PATCH).content(p))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descrizione", is("Imposta patchata")));
    }

    @Test
    void patchReplaceWholeNestedObject() throws Exception {
        String etag = currentEtag("IMU");
        String p = """
                [{"op":"replace","path":"/avvisaturaMail","value":{"promemoriaAvviso":{"abilitato":true,"allegaPdf":true}}}]""";
        mvc.perform(patch("/tipiPendenza/IMU").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(JSON_PATCH).content(p))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avvisaturaMail.promemoriaAvviso.abilitato", is(true)))
                .andExpect(jsonPath("$.avvisaturaMail.promemoriaAvviso.allegaPdf", is(true)));
    }

    @Test
    void patchNestedPointerReturns400() throws Exception {
        String etag = currentEtag("IMU");
        String p = """
                [{"op":"replace","path":"/avvisaturaMail/promemoriaAvviso/abilitato","value":true}]""";
        mvc.perform(patch("/tipiPendenza/IMU").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(JSON_PATCH).content(p))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patchInvalidCodificaIuvReturns400() throws Exception {
        String etag = currentEtag("IMU");
        String p = """
                [{"op":"replace","path":"/codificaIUV","value":"ABCD"}]""";
        mvc.perform(patch("/tipiPendenza/IMU").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(JSON_PATCH).content(p))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void patchChangingIdReturns400() throws Exception {
        String etag = currentEtag("IMU");
        String p = """
                [{"op":"replace","path":"/idTipoPendenza","value":"XXX"}]""";
        mvc.perform(patch("/tipiPendenza/IMU").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(JSON_PATCH).content(p))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("idTipoPendenza")));
    }

    @Test
    void patchRemovingDescrizioneReturns400() throws Exception {
        String etag = currentEtag("IMU");
        String p = """
                [{"op":"remove","path":"/descrizione"}]""";
        mvc.perform(patch("/tipiPendenza/IMU").with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(JSON_PATCH).content(p))
                .andExpect(status().isBadRequest());
    }

    private String currentEtag(String cod) throws Exception {
        return mvc.perform(get("/tipiPendenza/" + cod).with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("ETag");
    }

    private long countAudit(String azione) {
        return gpAuditRepository.findAll().stream()
                .filter(a -> azione.equals(a.getTipoOggetto()))
                .count();
    }
}
