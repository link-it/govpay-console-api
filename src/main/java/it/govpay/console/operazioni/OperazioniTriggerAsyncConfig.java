package it.govpay.console.operazioni;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Executor per il trigger asincrono post-commit dei batch (es. hook
 * riconciliazioni verso {@link OperazioneBatchClient}): un task per
 * chiamata, I/O-bound puro (HTTP bloccante con circuit breaker/retry sopra),
 * volume basso — caso d'uso diretto per i virtual thread, nessun
 * dimensionamento di pool da gestire.
 *
 * <p>Bean {@code operazioniTriggerExecutor}:
 * <ul>
 *   <li>se {@code app.operazioni.trigger.async=true} (default prod):
 *       {@link Executors#newVirtualThreadPerTaskExecutor()};</li>
 *   <li>se {@code app.operazioni.trigger.async=false} (default profilo
 *       test): esecuzione sincrona sul thread chiamante, per test
 *       deterministici senza attese sul thread async.</li>
 * </ul>
 */
@Configuration
public class OperazioniTriggerAsyncConfig {

    @Bean(name = "operazioniTriggerExecutor")
    public Executor operazioniTriggerExecutor(@Value("${app.operazioni.trigger.async:true}") boolean async) {
        return async ? Executors.newVirtualThreadPerTaskExecutor() : Runnable::run;
    }
}
