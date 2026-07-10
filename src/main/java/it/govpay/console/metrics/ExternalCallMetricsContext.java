package it.govpay.console.metrics;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * Accumulatore request-scoped dei tempi spesi in chiamate verso servizi esterni.
 */
@Component
@RequestScope
public class ExternalCallMetricsContext {

    private long externalNanos;

    public void record(long elapsedNanos) {
        externalNanos += elapsedNanos;
    }

    public long externalNanos() {
        return externalNanos;
    }

}
