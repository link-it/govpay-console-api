package it.govpay.console.operazioni;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import it.govpay.common.auth.GovpayPasswordEncoder;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Utenza;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.UtenzaRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OperazioniControllerIntegrationTest {

    private static final String PRINCIPAL = "operatore1";
    private static final String PASSWORD = "secret";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private GovpayPasswordEncoder encoder;
    @Autowired
    private UtenzaRepository utenzaRepository;
    @Autowired
    private OperatoreRepository operatoreRepository;
    @Autowired
    private JobRepository jobRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setup() {
        // Le JobExecution seedate dai test sono scritte fuori dalla
        // transazione del test (vedi runOutsideTestTransaction) e quindi non
        // vengono rollbackate: senza pulizia, l'esito di
        // getLastJobInstance/getLastJobExecution su "ibanCheckJob" dipende
        // dall'ordine di esecuzione dei metodi @Test.
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

    /**
     * JobRepository rifiuta di operare dentro una transazione ambiente
     * (gestisce da solo il proprio commit, con PROPAGATION_REQUIRES_NEW):
     * la transazione del test (@Transactional di classe, per il rollback di
     * Utenza/Operatore) va sospesa esplicitamente. Il commit e' quindi reale
     * (non rollbackato a fine test): usato sia per seedare le esecuzioni sia
     * per pulire le tabelle BATCH_* in modo che sia visibile a qualunque
     * connessione, non solo a quella (sospesa) del test corrente.
     */
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

    private void seedCompletedExecution(String jobName, LocalDateTime start, LocalDateTime end) {
        runOutsideTestTransaction(() -> {
            JobParameters params = new JobParametersBuilder().addLong("run.id", System.nanoTime()).toJobParameters();
            JobInstance instance = jobRepository.createJobInstance(jobName, params);
            JobExecution execution = jobRepository.createJobExecution(instance, params, new ExecutionContext());
            execution.setStartTime(start);
            execution.setEndTime(end);
            execution.setStatus(BatchStatus.COMPLETED);
            jobRepository.update(execution);
        });
    }

    /**
     * STARTING (JobExecution creata ma non ancora avviata) ha startTime
     * null: dataInizio deve ripiegare su createTime (NOT NULL a schema)
     * anziche' violare il vincolo required di EsecuzioneSummary.
     */
    private void seedQueuedExecution(String jobName) {
        runOutsideTestTransaction(() -> {
            JobParameters params = new JobParametersBuilder().addLong("run.id", System.nanoTime()).toJobParameters();
            JobInstance instance = jobRepository.createJobInstance(jobName, params);
            jobRepository.createJobExecution(instance, params, new ExecutionContext());
        });
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
                .andExpect(jsonPath("$[1].id").value("RESET_CACHE"));
    }

    /**
     * frequenzaSchedulata (assente per RESET_CACHE, non schedulata) e
     * lockAttivo (sempre null, nessun ShedLock configurato) sono nullable nel
     * contratto: la risposta deve comunque includere la chiave con valore
     * null, non ometterla ne' violare un vincolo required-non-null.
     */
    @Test
    void frequenzaSchedulataELockAttivo_rispettanoLaNullabilitaDelContratto() throws Exception {
        mvc.perform(get("/operazioni").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].frequenzaSchedulata").value("PT2H"))
                .andExpect(jsonPath("$[1].frequenzaSchedulata").value((Object) null))
                .andExpect(jsonPath("$[0].lockAttivo").value((Object) null))
                .andExpect(jsonPath("$[1].lockAttivo").value((Object) null));
    }

    @Test
    void operazioneInCoda_haDataInizioDaCreateTimeEStatoInCoda() throws Exception {
        seedQueuedExecution("ibanCheckJob");

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
        seedCompletedExecution("ibanCheckJob", start, end);

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
    void operazioneSenzaJobName_haUltimaEsecuzioneNulla() throws Exception {
        mvc.perform(get("/operazioni").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].ultimaEsecuzione").value((Object) null));
    }
}
