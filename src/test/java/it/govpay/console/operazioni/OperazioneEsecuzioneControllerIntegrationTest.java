package it.govpay.console.operazioni;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
class OperazioneEsecuzioneControllerIntegrationTest {

    private static final String PRINCIPAL = "operatore1";
    private static final String PASSWORD = "secret";
    private static final String SERVIZIO = "Configurazione e manutenzione";

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
    @Autowired
    private JobRepository jobRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private OperazioneEsecuzioneClient client;

    @BeforeEach
    void setup() {
        cleanupBatchTables();

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

    private void runOutsideTestTransaction(Runnable action) {
        TransactionTemplate suspended = new TransactionTemplate(transactionManager);
        suspended.setPropagationBehavior(TransactionTemplate.PROPAGATION_NOT_SUPPORTED);
        suspended.executeWithoutResult(status -> action.run());
    }

    private void cleanupBatchTables() {
        runOutsideTestTransaction(() -> {
            jdbcTemplate.update("DELETE FROM BATCH_STEP_EXECUTION_CONTEXT");
            jdbcTemplate.update("DELETE FROM BATCH_STEP_EXECUTION");
            jdbcTemplate.update("DELETE FROM BATCH_JOB_EXECUTION_CONTEXT");
            jdbcTemplate.update("DELETE FROM BATCH_JOB_EXECUTION_PARAMS");
            jdbcTemplate.update("DELETE FROM BATCH_JOB_EXECUTION");
            jdbcTemplate.update("DELETE FROM BATCH_JOB_INSTANCE");
        });
    }

    private void seedExecution(String jobName, BatchStatus status) {
        runOutsideTestTransaction(() -> {
            JobParameters params = new JobParametersBuilder().addLong("run.id", System.nanoTime()).toJobParameters();
            JobInstance instance = jobRepository.createJobInstance(jobName, params);
            JobExecution execution = jobRepository.createJobExecution(instance, params, new ExecutionContext());
            execution.setStatus(status);
            if (status != BatchStatus.STARTING) {
                execution.setStartTime(LocalDateTime.now());
            }
            jobRepository.update(execution);
        });
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
    void nonBatchBackedReturns422() throws Exception {
        grantScrittura();
        mvc.perform(post("/operazioni/RESET_CACHE/esecuzioni").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void triggerNonConfiguratoReturns503() throws Exception {
        grantScrittura();
        mvc.perform(post("/operazioni/EC_SYNC/esecuzioni").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void esecuzioneGiaInCorsoSenzaForceReturns409() throws Exception {
        grantScrittura();
        seedExecution("ibanCheckJob", BatchStatus.STARTED);

        mvc.perform(post("/operazioni/IBAN_CHECK/esecuzioni").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void esecuzioneGiaInCorsoConForceReturns202() throws Exception {
        grantScrittura();
        seedExecution("ibanCheckJob", BatchStatus.STARTED);

        mvc.perform(post("/operazioni/IBAN_CHECK/esecuzioni").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"force\":true}"))
                .andExpect(status().isAccepted());

        verify(client).avviaJob(eq("http://iban-batch:8080/api/batch"), eq(true));
    }

    @Test
    void accettataConLocationEBodyQuandoLEsecuzioneDiventaVisibile() throws Exception {
        grantScrittura();
        doAnswer(invocation -> {
            seedExecution("ibanCheckJob", BatchStatus.STARTED);
            return null;
        }).when(client).avviaJob(anyString(), anyBoolean());

        mvc.perform(post("/operazioni/IBAN_CHECK/esecuzioni").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.containsString("/operazioni/IBAN_CHECK/esecuzioni/")))
                .andExpect(jsonPath("$.idOperazione").value("IBAN_CHECK"))
                .andExpect(jsonPath("$.stato").value("IN_CORSO"))
                .andExpect(jsonPath("$.forzata").value(false))
                .andExpect(jsonPath("$.idEsecuzione").exists());
    }

    @Test
    void accettataNudaQuandoLEsecuzioneNonDiventaVisibileInTempo() throws Exception {
        grantScrittura();
        // client mockato non seeda nulla: il polling scade senza trovare una nuova esecuzione.

        mvc.perform(post("/operazioni/IBAN_CHECK/esecuzioni").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isAccepted())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(content().string(""));
    }

    @Test
    void scriveRigaDiAudit() throws Exception {
        grantScrittura();
        doAnswer(invocation -> {
            seedExecution("ibanCheckJob", BatchStatus.STARTED);
            return null;
        }).when(client).avviaJob(anyString(), anyBoolean());

        mvc.perform(post("/operazioni/IBAN_CHECK/esecuzioni").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isAccepted());

        boolean auditPresente = gpAuditRepository.findAll().stream()
                .anyMatch(row -> "OPERAZIONE_AVVIA_ESECUZIONE".equals(row.getTipoOggetto()));
        assertThat(auditPresente).isTrue();
    }
}
