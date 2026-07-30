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
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.UtenzaRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MailPromemoriaControllerIntegrationTest {

    private static final String PRINCIPAL = "operatore1";
    private static final String PASSWORD = "secret";
    private static final String BASE = "/impostazioni/mail/template-promemoria";
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
    void getDefaultReturnsEmptyTemplatesWithTipoFreemarker() throws Exception {
        grantLettura();
        mvc.perform(get(BASE).with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.promemoriaAvviso.tipo", is("freemarker")))
                .andExpect(jsonPath("$.promemoriaRicevuta.tipo", is("freemarker")))
                .andExpect(jsonPath("$.promemoriaScadenza.tipo", is("freemarker")))
                .andExpect(jsonPath("$.promemoriaAvviso.oggetto").doesNotExist());
    }

    @Test
    void putConfigWithIfMatchRoundTrips() throws Exception {
        grantScrittura();
        String etag = currentEtag();
        String body = """
                {"promemoriaAvviso":{"tipo":"freemarker","oggetto":"Avviso ${numeroAvviso}",
                    "messaggio":"Gentile ${nomeDebitore}...","allegaPdf":true},
                 "promemoriaRicevuta":{"tipo":"freemarker","oggetto":"Ricevuta ${iuv}",
                    "messaggio":"Pagamento ricevuto...","soloEseguiti":true,"allegaPdf":false},
                 "promemoriaScadenza":{"tipo":"freemarker","oggetto":"Scadenza ${numeroAvviso}",
                    "messaggio":"Il suo avviso scade...","preavviso":5}}""";
        mvc.perform(put(BASE).with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.promemoriaAvviso.oggetto", is("Avviso ${numeroAvviso}")))
                .andExpect(jsonPath("$.promemoriaAvviso.allegaPdf", is(true)))
                .andExpect(jsonPath("$.promemoriaRicevuta.soloEseguiti", is(true)))
                .andExpect(jsonPath("$.promemoriaScadenza.preavviso", is(5)));
    }

    @Test
    void putConfigWithoutIfMatchReturns428() throws Exception {
        grantScrittura();
        mvc.perform(put(BASE).with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(minimalBody()))
                .andExpect(status().isPreconditionRequired());
    }

    @Test
    void patchReplacesSingleBlockAtomically() throws Exception {
        grantScrittura();
        String etag = currentEtag();
        String patchBody = """
                [{"op":"replace","path":"/promemoriaScadenza","value":
                    {"tipo":"freemarker","oggetto":"Nuovo oggetto","messaggio":"Nuovo messaggio","preavviso":10}}]""";
        mvc.perform(patch(BASE).with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(JSON_PATCH).content(patchBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.promemoriaScadenza.oggetto", is("Nuovo oggetto")))
                .andExpect(jsonPath("$.promemoriaScadenza.preavviso", is(10)))
                .andExpect(jsonPath("$.promemoriaAvviso.oggetto").doesNotExist());
    }

    @Test
    void patchWithoutDirittoReturns403() throws Exception {
        grantLettura();
        String etag = currentEtag();
        String patchBody = """
                [{"op":"replace","path":"/promemoriaScadenza","value":
                    {"tipo":"freemarker","preavviso":10}}]""";
        mvc.perform(patch(BASE).with(httpBasic(PRINCIPAL, PASSWORD))
                        .header("If-Match", etag)
                        .contentType(JSON_PATCH).content(patchBody))
                .andExpect(status().isForbidden());
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
                {"promemoriaAvviso":{"tipo":"freemarker"},
                 "promemoriaRicevuta":{"tipo":"freemarker"},
                 "promemoriaScadenza":{"tipo":"freemarker"}}""";
    }
}
