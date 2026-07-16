package it.govpay.console.gde;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Bean {@code gdeExecutor} usato da {@link ConsoleGdeService} per l'invio
 * asincrono degli eventi GDE, alias anche {@code asyncHttpExecutor} perche'
 * lo stesso executor serve al costruttore di
 * {@code it.govpay.common.client.service.ConnettoreService} (qualifier
 * atteso da quella classe, vedi {@link GdeCommonBeansConfig}).
 * <p>
 * Se {@code app.gde.async=true} (default prod): {@link ThreadPoolTaskExecutor}
 * con pool dedicato {@code gde-*}. Se {@code app.gde.async=false} (default
 * profilo test): {@link SyncTaskExecutor}, cosi' i test che verificano
 * l'invio dell'evento lo trovano senza dover attendere il thread async.
 */
@Configuration
public class AsyncGdeConfig {

    @Bean(name = { "gdeExecutor", "asyncHttpExecutor" })
    public TaskExecutor gdeExecutor(@Value("${app.gde.async:true}") boolean async,
                                    @Value("${app.gde.pool.core-size:2}") int coreSize,
                                    @Value("${app.gde.pool.max-size:4}") int maxSize,
                                    @Value("${app.gde.pool.queue-capacity:100}") int queueCapacity) {
        if (!async) {
            return new SyncTaskExecutor();
        }
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(coreSize);
        exec.setMaxPoolSize(maxSize);
        exec.setQueueCapacity(queueCapacity);
        exec.setThreadNamePrefix("gde-");
        exec.setRejectedExecutionHandler((r, e) -> {
            // l'invio GDE non deve mai bloccare la response: log e drop
            org.slf4j.LoggerFactory.getLogger(AsyncGdeConfig.class)
                    .warn("Evento GDE rifiutato dall'executor (pool saturo): evento droppato.");
        });
        exec.initialize();
        return exec;
    }
}
