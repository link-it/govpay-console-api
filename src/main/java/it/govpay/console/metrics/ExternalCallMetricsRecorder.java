package it.govpay.console.metrics;

import java.net.http.HttpTimeoutException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.ScopeNotActiveException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

/**
 * Misura operazioni logiche verso servizi esterni e le registra nel context
 * della request corrente. Il timer va usato fuori dai metodi annotati
 * {@code @Retry}, cosi' la durata include tutti i tentativi.
 */
@Component
public class ExternalCallMetricsRecorder {

    private final ObjectProvider<ExternalCallMetricsContext> contextProvider;
    private final MeterRegistry meterRegistry;

    public ExternalCallMetricsRecorder(ObjectProvider<ExternalCallMetricsContext> contextProvider,
                                       MeterRegistry meterRegistry) {
        this.contextProvider = contextProvider;
        this.meterRegistry = meterRegistry;
    }

    public void record(String client, String operation, ExternalCall call) {
        long start = System.nanoTime();
        String outcome = "success";
        try {
            call.run();
        } catch (RuntimeException | Error e) {
            outcome = classify(e);
            throw e;
        } finally {
            long elapsed = System.nanoTime() - start;
            recordDuration(client, operation, outcome, elapsed);
        }
    }

    public void recordDuration(String client, String operation, Throwable error, long elapsed) {
        recordDuration(client, operation, classify(error), elapsed);
    }

    public void recordDuration(String client, String operation, String outcome, long elapsed) {
        recordInRequestContext(elapsed);
        Tags tags = Tags.of("client", client, "operation", operation, "outcome", outcome);
        meterRegistry.timer("govpay.external.service.duration", tags)
                .record(elapsed, TimeUnit.NANOSECONDS);
    }

    private void recordInRequestContext(long elapsed) {
        try {
            ExternalCallMetricsContext context = contextProvider.getIfAvailable();
            if (context != null) {
                context.record(elapsed);
            }
        } catch (ScopeNotActiveException e) {
            // Chiamata misurata fuori da una request HTTP: resta la metrica del client esterno,
            // ma non c'e' un breakdown API a cui sommare il tempo.
        }
    }

    private static String classify(Throwable e) {
        if (containsCause(e, CallNotPermittedException.class)) {
            return "circuit_open";
        }
        if (containsCause(e, SocketTimeoutException.class)
                || containsCause(e, HttpTimeoutException.class)) {
            return "timeout";
        }
        if (containsCause(e, ResourceAccessException.class)) {
            return "io_error";
        }
        return "error";
    }

    private static boolean containsCause(Throwable e, Class<? extends Throwable> type) {
        Throwable current = e;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    @FunctionalInterface
    public interface ExternalCall {
        void run();
    }
}
