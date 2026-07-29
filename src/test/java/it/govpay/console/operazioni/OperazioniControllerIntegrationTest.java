package it.govpay.console.operazioni;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDateTime;

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
import it.govpay.common.batch.dto.BatchInfo;
import it.govpay.common.batch.dto.LastExecutionInfo;
import it.govpay.common.batch.dto.NextExecutionInfo;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Utenza;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.UtenzaRepository;

/**
 * Il client verso il microservizio batch (govpay-common {@code AbstractBatchController})
 * e' mockato a livello di bean: il comportamento HTTP/deserializzazione e'
 * gia' verificato in {@link OperazioneBatchClientTest}, qui si verifica solo
 * il cablaggio catalogo/mappatura/nullabilita' del contratto.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OperazioniControllerIntegrationTest {

    private static final String PRINCIPAL = "operatore1";
    private static final String PASSWORD = "secret";
    private static final String IBAN_URL = "http://iban-batch:8080/api/batch";
    private static final String EC_SYNC_URL = "http://ec-sync-batch:8080/api/batch";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private GovpayPasswordEncoder encoder;
    @Autowired
    private UtenzaRepository utenzaRepository;
    @Autowired
    private OperatoreRepository operatoreRepository;

    @MockitoBean
    private OperazioneBatchClient client;

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

        when(client.info(anyString())).thenReturn(BatchInfo.builder()
                .jobName("job").displayName("Nome batch").description("Descrizione batch").build());
        when(client.lastExecution(anyString())).thenReturn(LastExecutionInfo.builder().build());
        when(client.nextExecution(anyString())).thenReturn(NextExecutionInfo.builder().build());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/operazioni"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void catalogoContieneTutteLeOperazioniConfigurate() throws Exception {
        mvc.perform(get("/operazioni").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("IBAN_CHECK"))
                .andExpect(jsonPath("$[1].id").value("RESET_CACHE"))
                .andExpect(jsonPath("$[2].id").value("EC_SYNC"));
    }

    /**
     * frequenzaSchedulata (assente per RESET_CACHE, non batch-backed) e
     * lockAttivo (sempre null, nessun ShedLock configurato) sono nullable nel
     * contratto: la risposta deve comunque includere la chiave con valore
     * null, non ometterla ne' violare un vincolo required-non-null.
     */
    @Test
    void frequenzaSchedulataELockAttivo_rispettanoLaNullabilitaDelContratto() throws Exception {
        when(client.nextExecution(IBAN_URL)).thenReturn(NextExecutionInfo.builder()
                .intervalMillis(Duration.ofHours(2).toMillis()).build());

        mvc.perform(get("/operazioni").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].frequenzaSchedulata").value("PT2H"))
                .andExpect(jsonPath("$[1].frequenzaSchedulata").value((Object) null))
                .andExpect(jsonPath("$[0].lockAttivo").value((Object) null))
                .andExpect(jsonPath("$[1].lockAttivo").value((Object) null));
    }

    /**
     * STARTING non e' mai restituito da {@code GET /lastExecution} di
     * govpay-common (esclude le esecuzioni non terminali) — verifica solo
     * che il mapper gestisca correttamente il ramo, in via difensiva.
     */
    @Test
    void operazioneInCoda_haDataInizioDaCreateTimeEStatoInCoda() throws Exception {
        LocalDateTime createTime = LocalDateTime.now();
        when(client.lastExecution(IBAN_URL)).thenReturn(LastExecutionInfo.builder()
                .executionId(1L).status("STARTING").startTime(createTime).build());

        mvc.perform(get("/operazioni").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ultimaEsecuzione.stato").value("IN_CODA"))
                .andExpect(jsonPath("$[0].ultimaEsecuzione.dataInizio").exists())
                .andExpect(jsonPath("$[0].ultimaEsecuzione.dataFine").value((Object) null))
                .andExpect(jsonPath("$[0].prossimaEsecuzione").value((Object) null));
    }

    @Test
    void operazioneConEsecuzione_haUltimaEsecuzionePopolata() throws Exception {
        LocalDateTime start = LocalDateTime.now().minusHours(3);
        LocalDateTime end = LocalDateTime.now().minusHours(2);
        when(client.lastExecution(IBAN_URL)).thenReturn(LastExecutionInfo.builder()
                .executionId(1L).status("COMPLETED").startTime(start).endTime(end).build());
        when(client.nextExecution(IBAN_URL)).thenReturn(NextExecutionInfo.builder()
                .nextExecutionTime(end.plusHours(2)).build());

        mvc.perform(get("/operazioni").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ultimaEsecuzione.stato").value("COMPLETATA"))
                .andExpect(jsonPath("$[0].prossimaEsecuzione").exists());
    }

    @Test
    void operazioneSenzaEsecuzioni_haUltimaEsecuzioneNulla() throws Exception {
        mvc.perform(get("/operazioni").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ultimaEsecuzione").value((Object) null));
    }

    @Test
    void operazioneLocale_haUltimaEsecuzioneNulla() throws Exception {
        mvc.perform(get("/operazioni").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].ultimaEsecuzione").value((Object) null));
    }

    @Test
    void microservizioNonRaggiungibile_vieneMostratoDegradatoSenzaFarFallireIlCatalogo() throws Exception {
        when(client.info(EC_SYNC_URL)).thenThrow(new OperazioneTriggerNonRaggiungibileException("giu'", new RuntimeException()));

        mvc.perform(get("/operazioni").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("IBAN_CHECK"))
                .andExpect(jsonPath("$[2].id").value("EC_SYNC"))
                .andExpect(jsonPath("$[2].nome").value("EC_SYNC"))
                .andExpect(jsonPath("$[2].ultimaEsecuzione").value((Object) null));
    }
}
