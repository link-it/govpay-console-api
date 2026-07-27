package it.govpay.console.operazioni;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.function.Supplier;

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
class OperazioneEsecuzioniControllerIntegrationTest {

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

    private <T> T callOutsideTestTransaction(Supplier<T> action) {
        TransactionTemplate suspended = new TransactionTemplate(transactionManager);
        suspended.setPropagationBehavior(TransactionTemplate.PROPAGATION_NOT_SUPPORTED);
        return suspended.execute(status -> action.get());
    }

    private void cleanupBatchTables() {
        callOutsideTestTransaction(() -> {
            jdbcTemplate.update("DELETE FROM BATCH_STEP_EXECUTION_CONTEXT");
            jdbcTemplate.update("DELETE FROM BATCH_STEP_EXECUTION");
            jdbcTemplate.update("DELETE FROM BATCH_JOB_EXECUTION_CONTEXT");
            jdbcTemplate.update("DELETE FROM BATCH_JOB_EXECUTION_PARAMS");
            jdbcTemplate.update("DELETE FROM BATCH_JOB_EXECUTION");
            jdbcTemplate.update("DELETE FROM BATCH_JOB_INSTANCE");
            return null;
        });
    }

    private Long seedExecution(String jobName, BatchStatus status, LocalDateTime start, LocalDateTime end) {
        return callOutsideTestTransaction(() -> {
            JobParameters params = new JobParametersBuilder().addLong("run.id", System.nanoTime()).toJobParameters();
            JobInstance instance = jobRepository.createJobInstance(jobName, params);
            JobExecution execution = jobRepository.createJobExecution(instance, params, new ExecutionContext());
            execution.setStartTime(start);
            execution.setEndTime(end);
            execution.setStatus(status);
            jobRepository.update(execution);
            return execution.getId();
        });
    }

    @Test
    void listaVuotaSenzaEsecuzioni() throws Exception {
        mvc.perform(get("/operazioni/IBAN_CHECK/esecuzioni").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-cache"))
                .andExpect(jsonPath("$.results").isArray())
                .andExpect(jsonPath("$.results").isEmpty())
                .andExpect(jsonPath("$.pagination.hasNextPage").value(false));
    }

    @Test
    void listaVuotaPerOperazioneNonBatchBacked() throws Exception {
        mvc.perform(get("/operazioni/RESET_CACHE/esecuzioni").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results").isEmpty());
    }

    @Test
    void unknownOperazioneReturns404() throws Exception {
        mvc.perform(get("/operazioni/BOGUS/esecuzioni").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void paginazioneSenzaTotal() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        seedExecution("ibanCheckJob", BatchStatus.COMPLETED, now.minusHours(3), now.minusHours(2));
        seedExecution("ibanCheckJob", BatchStatus.COMPLETED, now.minusHours(2), now.minusHours(1));
        seedExecution("ibanCheckJob", BatchStatus.COMPLETED, now.minusHours(1), now);

        mvc.perform(get("/operazioni/IBAN_CHECK/esecuzioni").param("limit", "2")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.pagination.hasNextPage").value(true))
                .andExpect(jsonPath("$.pagination.totalResults").doesNotExist());
    }

    @Test
    void totalTrueValorizzaTotali() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        seedExecution("ibanCheckJob", BatchStatus.COMPLETED, now.minusHours(2), now.minusHours(1));
        seedExecution("ibanCheckJob", BatchStatus.COMPLETED, now.minusHours(1), now);

        mvc.perform(get("/operazioni/IBAN_CHECK/esecuzioni").param("limit", "1").param("total", "true")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalResults").value(2))
                .andExpect(jsonPath("$.pagination.totalPages").value(2));
    }

    @Test
    void ordinePerDataInizioDesc() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        Long first = seedExecution("ibanCheckJob", BatchStatus.COMPLETED, now.minusHours(3), now.minusHours(2));
        Long second = seedExecution("ibanCheckJob", BatchStatus.COMPLETED, now.minusHours(2), now.minusHours(1));
        Long third = seedExecution("ibanCheckJob", BatchStatus.COMPLETED, now.minusHours(1), now);

        mvc.perform(get("/operazioni/IBAN_CHECK/esecuzioni").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].idEsecuzione").value(String.valueOf(third)))
                .andExpect(jsonPath("$.results[1].idEsecuzione").value(String.valueOf(second)))
                .andExpect(jsonPath("$.results[2].idEsecuzione").value(String.valueOf(first)));
    }

    /**
     * id di inserimento e dataInizio possono divergere (es. un'esecuzione
     * resta in coda a lungo prima di partire, una creata dopo la sorpassa):
     * l'ordine deve seguire dataInizio, non l'id grezzo.
     */
    @Test
    void ordinePerDataInizioDescNonPerIdInserimento() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        // Creata per prima (id piu' basso) ma partita molto piu' tardi.
        Long inseritaPrimaPartitaDopo = seedExecution("ibanCheckJob", BatchStatus.COMPLETED,
                now.minusHours(1), now);
        // Creata dopo (id piu' alto) ma partita molto prima.
        Long inseritaDopoPartitaPrima = seedExecution("ibanCheckJob", BatchStatus.COMPLETED,
                now.minusDays(5), now.minusDays(5).plusMinutes(5));

        mvc.perform(get("/operazioni/IBAN_CHECK/esecuzioni").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].idEsecuzione").value(String.valueOf(inseritaPrimaPartitaDopo)))
                .andExpect(jsonPath("$.results[1].idEsecuzione").value(String.valueOf(inseritaDopoPartitaPrima)));
    }

    @Test
    void filtroStato() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        seedExecution("ibanCheckJob", BatchStatus.COMPLETED, now.minusHours(2), now.minusHours(1));
        Long failed = seedExecution("ibanCheckJob", BatchStatus.FAILED, now.minusHours(1), now);

        mvc.perform(get("/operazioni/IBAN_CHECK/esecuzioni").param("stato", "FALLITA")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.results[0].idEsecuzione").value(String.valueOf(failed)));
    }

    @Test
    void filtroDataInizioMinMax() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        seedExecution("ibanCheckJob", BatchStatus.COMPLETED, now.minusDays(2), now.minusDays(2).plusMinutes(5));
        Long dentro = seedExecution("ibanCheckJob", BatchStatus.COMPLETED, now.minusHours(1), now);
        seedExecution("ibanCheckJob", BatchStatus.COMPLETED, now.plusDays(2), now.plusDays(2).plusMinutes(5));

        // Il servizio confronta i filtri nel timezone applicativo (Clock, default
        // di sistema): serve la stessa zona qui, non UTC, per non introdurre
        // uno scarto rispetto agli orari seedati con LocalDateTime.now().
        String min = now.minusHours(2).atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime().toString();
        String max = now.plusHours(1).atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime().toString();

        mvc.perform(get("/operazioni/IBAN_CHECK/esecuzioni")
                        .param("dataInizioMin", min).param("dataInizioMax", max)
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.results[0].idEsecuzione").value(String.valueOf(dentro)));
    }

    @Test
    void dettaglioEsecuzioneEsistente() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        Long id = seedExecution("ibanCheckJob", BatchStatus.COMPLETED, now.minusHours(1), now);

        mvc.perform(get("/operazioni/IBAN_CHECK/esecuzioni/" + id).with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-cache"))
                .andExpect(jsonPath("$.idEsecuzione").value(String.valueOf(id)))
                .andExpect(jsonPath("$.idOperazione").value("IBAN_CHECK"))
                .andExpect(jsonPath("$.stato").value("COMPLETATA"))
                .andExpect(jsonPath("$.forzata").value((Object) null));
    }

    @Test
    void dettaglioIdInesistenteReturns404() throws Exception {
        mvc.perform(get("/operazioni/IBAN_CHECK/esecuzioni/999999").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void dettaglioIdDiAltraOperazioneReturns404() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        Long id = seedExecution("ecSyncJob", BatchStatus.COMPLETED, now.minusHours(1), now);

        mvc.perform(get("/operazioni/IBAN_CHECK/esecuzioni/" + id).with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void dettaglioIdNonNumericoReturns400() throws Exception {
        mvc.perform(get("/operazioni/IBAN_CHECK/esecuzioni/not-a-number").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/operazioni/IBAN_CHECK/esecuzioni"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/operazioni/IBAN_CHECK/esecuzioni/1"))
                .andExpect(status().isUnauthorized());
    }
}
