package it.govpay.console.metrics;

import it.govpay.common.metrics.ExternalCallMetricsRecorder;

/**
 * Fallback usato quando {@code govpay.metrics.enabled=false} (o assente,
 * default della libreria): {@code AvvisoService}/{@code RicevutaService}
 * dipendono da {@link ExternalCallMetricsRecorder} nel costruttore, ma quella
 * classe non e' registrata come bean se l'autoconfigurazione di
 * govpay-common non si attiva.
 * <p>
 * {@link #record} esegue solo la chiamata, senza toccare alcun
 * {@code MeterRegistry}: la chiamata esterna avviene esattamente come
 * prima, semplicemente non viene misurata.
 */
public class NoopExternalCallMetricsRecorder extends ExternalCallMetricsRecorder {

    public NoopExternalCallMetricsRecorder() {
        super(null, null);
    }

    @Override
    public void record(String client, String operation, ExternalCall call) {
        call.run();
    }
}
