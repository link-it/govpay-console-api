package it.govpay.console.audit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Abilita l'esecuzione asincrona di {@link AuditWriter}.
 *
 * <p>Bean {@code auditExecutor}:
 * <ul>
 *   <li>se {@code app.audit.async=true} (default prod): {@link ThreadPoolTaskExecutor}
 *       con pool dedicato {@code audit-*};</li>
 *   <li>se {@code app.audit.async=false} (default profilo test): {@link SyncTaskExecutor}
 *       per garantire che i test che verificano la riga di audit la trovino
 *       senza dover attendere il thread async.</li>
 * </ul>
 */
@Configuration
@EnableAsync
public class AsyncAuditConfig {

    @Bean(name = "auditExecutor")
    public TaskExecutor auditExecutor(@Value("${app.audit.async:true}") boolean async,
                                      @Value("${app.audit.pool.core-size:2}") int coreSize,
                                      @Value("${app.audit.pool.max-size:4}") int maxSize,
                                      @Value("${app.audit.pool.queue-capacity:100}") int queueCapacity) {
        if (!async) {
            return new SyncTaskExecutor();
        }
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(coreSize);
        exec.setMaxPoolSize(maxSize);
        exec.setQueueCapacity(queueCapacity);
        exec.setThreadNamePrefix("audit-");
        exec.setRejectedExecutionHandler((r, e) -> {
            // l'audit non deve mai bloccare la response: log e drop
            org.slf4j.LoggerFactory.getLogger(AsyncAuditConfig.class)
                    .warn("Audit task rifiutato dall'executor (pool saturo): row droppata.");
        });
        exec.initialize();
        return exec;
    }
}
