package it.govpay.console.sla;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.common.auth.GovpayPasswordEncoder;
import it.govpay.console.entity.Acl;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Utenza;
import it.govpay.console.repository.AclRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.UtenzaRepository;

/**
 * Verifica ACL, validazione e cablaggio end-to-end di {@code GET /metriche/sla}
 * (issue #36): mocka solo {@link PrometheusQueryClient} (il confine esterno),
 * lasciando {@link SlaService} calcolare per davvero — così il test copre
 * anche il calcolo dello stato, non solo il wiring HTTP.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SlaControllerIntegrationTest {

    private static final String PRINCIPAL = "operatore1";
    private static final String PASSWORD = "secret";
    private static final String SERVIZIO = "Configurazione e manutenzione";
    private static final String BASE = "/metriche/sla?dataDa=2026-07-01&dataA=2026-07-31";

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

    @MockitoBean
    private PrometheusQueryClient prometheusQueryClient;

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
    void senzaAutenticazioneReturns401() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    }

    @Test
    void senzaDirittoConfigurazioneEManutenzioneReturns403() throws Exception {
        mvc.perform(get(BASE).with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.detail", containsString(SERVIZIO)));
    }

    @Test
    void dataDaSuccessivaADataAReturns400() throws Exception {
        grantLettura();
        mvc.perform(get("/metriche/sla?dataDa=2026-07-31&dataA=2026-07-01")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void senzaParametriObbligatoriReturns400() throws Exception {
        grantLettura();
        mvc.perform(get("/metriche/sla").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void conDirittoRestituisceI4KpiCalcolati() throws Exception {
        grantLettura();
        when(prometheusQueryClient.query(anyString(), any())).thenReturn(Optional.of(100.0));

        mvc.perform(get(BASE).with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.periodo.da", is("2026-07-01")))
                .andExpect(jsonPath("$.periodo.a", is("2026-07-31")))
                .andExpect(jsonPath("$.kpi.length()", is(4)))
                .andExpect(jsonPath("$.kpi[0].sogliaSecondi", is(2.0)))
                .andExpect(jsonPath("$.kpi[0].sogliaPercentile", is(98)));
    }

    @Test
    void quandoPrometheusNonRaggiungibileReturns502() throws Exception {
        grantLettura();
        when(prometheusQueryClient.query(anyString(), any()))
                .thenThrow(new PrometheusNonRaggiungibileException("boom", null));

        mvc.perform(get(BASE).with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentType("application/problem+json"));
    }

    private static final String SERIE_STORICA_BASE =
            "/metriche/sla/TDP?dataDa=2026-07-01&dataA=2026-07-01&granularitaMinuti=360";

    @Test
    void serieStoricaSenzaAutenticazioneReturns401() throws Exception {
        mvc.perform(get(SERIE_STORICA_BASE)).andExpect(status().isUnauthorized());
    }

    @Test
    void serieStoricaSenzaDirittoReturns403() throws Exception {
        mvc.perform(get(SERIE_STORICA_BASE).with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void serieStoricaGranularitaSottoIlMinimoReturns400ConCampo() throws Exception {
        grantLettura();
        mvc.perform(get("/metriche/sla/TDP?dataDa=2026-07-01&dataA=2026-07-01&granularitaMinuti=1")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.errors[0].field", is("granularitaMinuti")));
    }

    @Test
    void serieStoricaOltreMaxPuntiReturns400ConCampo() throws Exception {
        grantLettura();
        mvc.perform(get("/metriche/sla/TDP?dataDa=2026-07-01&dataA=2026-07-31&granularitaMinuti=5")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.errors[0].field", is("granularitaMinuti")));
    }

    @Test
    void serieStoricaConDirittoRestituisceLaSerie() throws Exception {
        grantLettura();
        when(prometheusQueryClient.queryRange(anyString(), any(), any(), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(java.util.Map.of());

        mvc.perform(get(SERIE_STORICA_BASE).with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.codice", is("TDP")))
                .andExpect(jsonPath("$.metodo", is("paDemandPaymentNotice")))
                .andExpect(jsonPath("$.granularitaMinuti", is(360)))
                .andExpect(jsonPath("$.serieStorica.length()", is(4)))
                .andExpect(jsonPath("$.serieStorica[0].totale", is(0)))
                .andExpect(jsonPath("$.serieStorica[0].conformitaOsservata", nullValue()));
    }

    @Test
    void serieStoricaQuandoPrometheusNonRaggiungibileReturns502() throws Exception {
        grantLettura();
        when(prometheusQueryClient.queryRange(anyString(), any(), any(), org.mockito.ArgumentMatchers.anyLong()))
                .thenThrow(new PrometheusNonRaggiungibileException("boom", null));

        mvc.perform(get(SERIE_STORICA_BASE).with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentType("application/problem+json"));
    }

    private void grantLettura() {
        Utenza utenza = utenzaRepository.findByPrincipal(PRINCIPAL).orElseThrow();
        Acl acl = new Acl();
        acl.setIdUtenza(utenza.getId());
        acl.setServizio(SERVIZIO);
        acl.setDiritti("R");
        aclRepository.save(acl);
    }
}
