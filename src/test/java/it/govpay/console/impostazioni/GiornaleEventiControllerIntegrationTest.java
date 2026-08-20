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
import it.govpay.console.entity.Acl;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Utenza;
import it.govpay.console.repository.AclRepository;
import it.govpay.console.repository.GpAuditRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.UtenzaRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GiornaleEventiControllerIntegrationTest {

    private static final String PRINCIPAL = "operatore1";
    private static final String PASSWORD = "secret";
    private static final String BASE = "/impostazioni/giornale-eventi";
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
    void getDefaultReturnsAllInterfacceDisabledWithLinks() throws Exception {
        grantLettura();
        mvc.perform(get(BASE).with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.apiEnte.letture.log", is("MAI")))
                .andExpect(jsonPath("$.apiEnte.letture.dump", is("MAI")))
                .andExpect(jsonPath("$.apiMaggioliJPPA.scritture.log", is("MAI")))
                .andExpect(jsonPath("$._links.servizioGDE.href", is("/impostazioni/servizioGDE")));
    }

    @Test
    void putWithoutDirittoReturns403() throws Exception {
        grantLettura();
        String etag = currentEtag();
        mvc.perform(put(BASE).with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON).content(fullBody("SEMPRE", "MAI")))
                .andExpect(status().isForbidden());
    }

    @Test
    void putConfigWithIfMatchRoundTrips() throws Exception {
        grantScrittura();
        String etag = currentEtag();
        mvc.perform(put(BASE).with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON).content(fullBody("SEMPRE", "SOLO_ERRORE")))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.apiEnte.letture.log", is("SEMPRE")))
                .andExpect(jsonPath("$.apiEnte.letture.dump", is("SOLO_ERRORE")))
                .andExpect(jsonPath("$.apiMaggioliJPPA.scritture.log", is("SEMPRE")));
    }

    @Test
    void putConfigWithoutIfMatchReturns428() throws Exception {
        grantScrittura();
        mvc.perform(put(BASE).with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(fullBody("SEMPRE", "MAI")))
                .andExpect(status().isPreconditionRequired());
    }

    @Test
    void putConfigWithWrongIfMatchReturns412() throws Exception {
        grantScrittura();
        mvc.perform(put(BASE).with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", "\"deadbeef\"")
                        .contentType(MediaType.APPLICATION_JSON).content(fullBody("SEMPRE", "MAI")))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void patchReplacesSingleBlockAtomically() throws Exception {
        grantScrittura();
        String etag = currentEtag();
        String patchBody = """
                [{"op":"replace","path":"/apiPagamento","value":
                    {"letture":{"log":"SEMPRE","dump":"SEMPRE"},"scritture":{"log":"SOLO_ERRORE","dump":"MAI"}}}]""";
        mvc.perform(patch(BASE).with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(JSON_PATCH).content(patchBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiPagamento.letture.log", is("SEMPRE")))
                .andExpect(jsonPath("$.apiPagamento.letture.dump", is("SEMPRE")))
                .andExpect(jsonPath("$.apiPagamento.scritture.log", is("SOLO_ERRORE")))
                .andExpect(jsonPath("$.apiPagamento.scritture.dump", is("MAI")))
                // Le altre interfacce non sono toccate dal blocco patchato.
                .andExpect(jsonPath("$.apiEnte.letture.log", is("MAI")));
    }

    @Test
    void patchWithNestedPointerReturns400() throws Exception {
        grantScrittura();
        String etag = currentEtag();
        String patchBody = """
                [{"op":"replace","path":"/apiPagamento/letture/log","value":"SEMPRE"}]""";
        mvc.perform(patch(BASE).with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(JSON_PATCH).content(patchBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patchWithoutDirittoReturns403() throws Exception {
        grantLettura();
        String etag = currentEtag();
        String patchBody = """
                [{"op":"replace","path":"/apiPagamento","value":
                    {"letture":{"log":"SEMPRE","dump":"SEMPRE"},"scritture":{"log":"MAI","dump":"MAI"}}}]""";
        mvc.perform(patch(BASE).with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(JSON_PATCH).content(patchBody))
                .andExpect(status().isForbidden());
    }

    @Test
    void putWritesAudit() throws Exception {
        grantScrittura();
        long before = countAudit(GiornaleEventiService.AZIONE_AUDIT_MODIFICA);
        String etag = currentEtag();
        mvc.perform(put(BASE).with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON).content(fullBody("SEMPRE", "MAI")))
                .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(countAudit(GiornaleEventiService.AZIONE_AUDIT_MODIFICA))
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

    private static String fullBody(String log, String dump) {
        String blocco = "{\"letture\":{\"log\":\"" + log + "\",\"dump\":\"" + dump + "\"},"
                + "\"scritture\":{\"log\":\"" + log + "\",\"dump\":\"" + dump + "\"}}";
        return "{"
                + "\"apiEnte\":" + blocco + ","
                + "\"apiPagamento\":" + blocco + ","
                + "\"apiRagioneria\":" + blocco + ","
                + "\"apiBackoffice\":" + blocco + ","
                + "\"apiPagoPA\":" + blocco + ","
                + "\"apiPendenze\":" + blocco + ","
                + "\"apiBackendIO\":" + blocco + ","
                + "\"apiMaggioliJPPA\":" + blocco
                + "}";
    }
}
