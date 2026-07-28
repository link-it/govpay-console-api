package it.govpay.console.operazioni;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.common.auth.GovpayPasswordEncoder;
import it.govpay.common.batch.dto.BatchStatusInfo;
import it.govpay.common.batch.dto.LastExecutionInfo;
import it.govpay.console.entity.Acl;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Utenza;
import it.govpay.console.repository.AclRepository;
import it.govpay.console.repository.GpAuditRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.UtenzaRepository;

/**
 * Il client verso il microservizio batch (govpay-common {@code AbstractBatchController})
 * e' mockato a livello di bean: il comportamento HTTP/deserializzazione e'
 * gia' verificato in {@link OperazioneBatchClientTest}, qui si verifica solo
 * il cablaggio controller/sicurezza/audit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
// Ridefinisce l'intero catalogo (0..2 invariati, 3 nuovo): il binder relaxed
// di Spring Boot non fonde correttamente indici aggiunti da una sola
// property source con indici definiti nell'application.properties principale.
@TestPropertySource(properties = {
        "govpay.operazioni.catalogo[0].id=IBAN_CHECK",
        "govpay.operazioni.catalogo[0].url=http://iban-batch:8080/api/batch",
        "govpay.operazioni.catalogo[0].abilitata=true",
        "govpay.operazioni.catalogo[1].id=RESET_CACHE",
        "govpay.operazioni.catalogo[1].abilitata=true",
        "govpay.operazioni.catalogo[2].id=EC_SYNC",
        "govpay.operazioni.catalogo[2].url=http://ec-sync-batch:8080/api/batch",
        "govpay.operazioni.catalogo[2].abilitata=true",
        "govpay.operazioni.catalogo[3].id=MISCONFIGURATA",
        "govpay.operazioni.catalogo[3].abilitata=true"
})
@Transactional
class OperazioneEsecuzioneControllerIntegrationTest {

    private static final String PRINCIPAL = "operatore1";
    private static final String PASSWORD = "secret";
    private static final String SERVIZIO = "Configurazione e manutenzione";
    private static final String IBAN_URL = "http://iban-batch:8080/api/batch";

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
    }

    private void grantScrittura() {
        Utenza utenza = utenzaRepository.findByPrincipal(PRINCIPAL).orElseThrow();
        Acl acl = new Acl();
        acl.setIdUtenza(utenza.getId());
        acl.setServizio(SERVIZIO);
        acl.setDiritti("RW");
        aclRepository.save(acl);
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(post("/operazioni/IBAN_CHECK/esecuzioni"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void withoutDirittoScritturaReturns403() throws Exception {
        mvc.perform(post("/operazioni/IBAN_CHECK/esecuzioni").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString(SERVIZIO)));
    }

    @Test
    void unknownOperazioneReturns404() throws Exception {
        grantScrittura();
        mvc.perform(post("/operazioni/BOGUS/esecuzioni").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void localeSenzaHandlerRegistratoReturns503() throws Exception {
        grantScrittura();
        mvc.perform(post("/operazioni/MISCONFIGURATA/esecuzioni").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void localeConHandlerRegistratoReturns202() throws Exception {
        grantScrittura();
        mvc.perform(post("/operazioni/RESET_CACHE/esecuzioni").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.idOperazione").value("RESET_CACHE"))
                .andExpect(jsonPath("$.stato").value("COMPLETATA"))
                .andExpect(jsonPath("$.forzata").value(true));
    }

    @Test
    void esecuzioneGiaInCorsoSenzaForceReturns409() throws Exception {
        grantScrittura();
        when(client.status(IBAN_URL)).thenReturn(BatchStatusInfo.builder().running(true).executionId(42L).build());

        mvc.perform(post("/operazioni/IBAN_CHECK/esecuzioni").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void esecuzioneGiaInCorsoConForceReturns202() throws Exception {
        grantScrittura();
        when(client.status(IBAN_URL)).thenReturn(BatchStatusInfo.builder().running(true).executionId(42L).build());

        mvc.perform(post("/operazioni/IBAN_CHECK/esecuzioni").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"force\":true}"))
                .andExpect(status().isAccepted());

        verify(client).run(eq(IBAN_URL), eq(true));
    }

    @Test
    void accettataConLocationEBodyQuandoLEsecuzioneDiventaVisibile() throws Exception {
        grantScrittura();
        // Prima chiamata (pre-check): niente in corso. Chiamate successive
        // (poll dopo il trigger): la nuova esecuzione e' visibile.
        when(client.status(IBAN_URL))
                .thenReturn(BatchStatusInfo.builder().running(false).build())
                .thenReturn(BatchStatusInfo.builder().running(true).executionId(99L).build());
        when(client.getExecution(IBAN_URL, 99L)).thenReturn(LastExecutionInfo.builder()
                .executionId(99L).status("STARTED").triggerType("MANUAL").build());

        mvc.perform(post("/operazioni/IBAN_CHECK/esecuzioni").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.containsString("/operazioni/IBAN_CHECK/esecuzioni/99")))
                .andExpect(jsonPath("$.idOperazione").value("IBAN_CHECK"))
                .andExpect(jsonPath("$.stato").value("IN_CORSO"))
                // Avviata via POST: govpay-common marca sempre TriggerType.MANUAL
                // (indipendentemente dal flag 'force', che e' un concetto diverso:
                // abbandonare un'esecuzione precedente, non la provenienza).
                .andExpect(jsonPath("$.forzata").value(true))
                .andExpect(jsonPath("$.idEsecuzione").value("99"));
    }

    @Test
    void accettataNudaQuandoLEsecuzioneNonDiventaVisibileInTempo() throws Exception {
        grantScrittura();
        // /status resta sempre "non in esecuzione": il polling scade senza trovare la nuova esecuzione.
        when(client.status(IBAN_URL)).thenReturn(BatchStatusInfo.builder().running(false).build());

        mvc.perform(post("/operazioni/IBAN_CHECK/esecuzioni").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isAccepted())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(content().string(""));
    }

    @Test
    void scriveRigaDiAudit() throws Exception {
        grantScrittura();
        when(client.status(IBAN_URL))
                .thenReturn(BatchStatusInfo.builder().running(false).build())
                .thenReturn(BatchStatusInfo.builder().running(true).executionId(99L).build());
        when(client.getExecution(IBAN_URL, 99L)).thenReturn(LastExecutionInfo.builder()
                .executionId(99L).status("STARTED").triggerType("MANUAL").build());

        mvc.perform(post("/operazioni/IBAN_CHECK/esecuzioni").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isAccepted());

        boolean auditPresente = gpAuditRepository.findAll().stream()
                .anyMatch(row -> "OPERAZIONE_AVVIA_ESECUZIONE".equals(row.getTipoOggetto()));
        assertThat(auditPresente).isTrue();
    }
}
