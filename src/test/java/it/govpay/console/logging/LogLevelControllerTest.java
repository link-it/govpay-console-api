package it.govpay.console.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.common.auth.GovpayPasswordEncoder;
import it.govpay.common.configurazione.model.LogLevelConfig;
import it.govpay.common.configurazione.service.ConfigurazioneService;
import it.govpay.common.logging.level.DynamicLogLevelService;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Utenza;
import it.govpay.console.model.AclServizio;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.UtenzaRepository;
import it.govpay.console.security.AclAuthorizer;


/**
 * Verifica degli endpoint di gestione dei livelli di log (BP-LOG-1) e della
 * loro protezione ACL (BP-SEC-2).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
// L'utenza di appoggio e la riga log_level vengono scritte sul database H2
// condiviso: senza rollback inquinerebbero i conteggi degli altri test.
@Transactional
@DisplayName("Test Gestione Livelli di Log")
class LogLevelControllerTest {

    private static final String LOGGER_GESTITO = "it.govpay.console.logging";
    private static final String PRINCIPAL = "operatore-logging";
    private static final String PASSWORD = "secret";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DynamicLogLevelService logLevelService;

    @Autowired
    private ConfigurazioneService configurazioneService;

    @Autowired
    private GovpayPasswordEncoder encoder;

    @Autowired
    private UtenzaRepository utenzaRepository;

    @Autowired
    private OperatoreRepository operatoreRepository;

    /**
     * L'autorizzazione ACL vera e' gia' coperta dai test di AclAuthorizer: qui
     * il bean e' mockato per verificare che il controller deleghi sul servizio
     * giusto e che un diniego blocchi effettivamente la modifica.
     */
    @MockitoBean
    private AclAuthorizer aclAuthorizer;

    @BeforeEach
    void creaUtenza() {
        if (utenzaRepository.findByPrincipal(PRINCIPAL).isEmpty()) {
            Utenza utenza = new Utenza();
            utenza.setPrincipal(PRINCIPAL);
            utenza.setPrincipalOriginale(PRINCIPAL);
            utenza.setAbilitato(true);
            utenza.setAutorizzazioneDominiStar(true);
            utenza.setAutorizzazioneTipiVersStar(true);
            utenza.setRuoli("OPERATORE");
            utenza.setPassword(encoder.encode(PASSWORD));
            utenzaRepository.save(utenza);

            Operatore operatore = new Operatore();
            operatore.setNome(PRINCIPAL);
            operatore.setIdUtenza(utenza.getId());
            operatoreRepository.save(operatore);
        }
    }

    @Test
    @DisplayName("Il servizio dei livelli dinamici e' attivo nel contesto")
    void servizioAttivoNelContesto() {
        assertNotNull(logLevelService);
        assertTrue(logLevelService.loggerGestiti().contains("it.govpay"));
    }

    @Test
    @DisplayName("GET /admin/logging/loggers elenca i logger gestibili")
    void elencoLoggerGestibili() throws Exception {
        mockMvc.perform(get("/admin/logging/loggers").accept(MediaType.APPLICATION_JSON)
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk());

        verify(aclAuthorizer).requireLettura(AclServizio.CONFIGURAZIONE_E_MANUTENZIONE);
    }

    @Test
    @DisplayName("PUT applica il livello e lo persiste per gli altri nodi")
    void impostaLivelloEPersiste() throws Exception {
        mockMvc.perform(put("/admin/logging/loggers/{logger}", LOGGER_GESTITO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"livello\":\"DEBUG\"}")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNoContent());

        verify(aclAuthorizer).requireScrittura(AclServizio.CONFIGURAZIONE_E_MANUTENZIONE);
        assertEquals("DEBUG", logLevelService.livelliCorrenti().get(LOGGER_GESTITO));

        LogLevelConfig persistita = configurazioneService.getLogLevel().orElseThrow();
        assertEquals("DEBUG", persistita.getLoggers().get(LOGGER_GESTITO));

        // ripristino: lascia il contesto come lo ha trovato
        mockMvc.perform(delete("/admin/logging/loggers/{logger}", LOGGER_GESTITO)
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNoContent());
        assertTrue(logLevelService.livelliCorrenti().isEmpty());
    }

    @Test
    @DisplayName("Un logger fuori ambito e' rifiutato con problem+json 400")
    void loggerFuoriAmbitoRifiutato() throws Exception {
        mockMvc.perform(put("/admin/logging/loggers/{logger}", "org.hibernate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"livello\":\"DEBUG\"}")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(result -> assertTrue(result.getResponse().getContentType()
                        .startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE)));
    }

    @Test
    @DisplayName("Un livello non riconosciuto e' rifiutato con 400")
    void livelloNonValidoRifiutato() throws Exception {
        mockMvc.perform(put("/admin/logging/loggers/{logger}", LOGGER_GESTITO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"livello\":\"VERBOSE\"}")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Senza diritto ACL la modifica e' negata")
    void senzaDirittoAclLaModificaEnegata() throws Exception {
        doThrow(new AccessDeniedException("niente diritto"))
                .when(aclAuthorizer).requireScrittura(any(AclServizio.class));

        mockMvc.perform(put("/admin/logging/loggers/{logger}", LOGGER_GESTITO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"livello\":\"DEBUG\"}")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isForbidden());

        assertTrue(logLevelService.livelliCorrenti().isEmpty());
    }
}
