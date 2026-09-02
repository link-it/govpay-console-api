package it.govpay.console.sla;

/**
 * Lanciata quando la chiamata all'HTTP Query API di Prometheus fallisce
 * (timeout, errore HTTP, errore IO, circuito aperto, risposta non
 * {@code status: success}). Mappata a 502 problem+json.
 */
public class PrometheusNonRaggiungibileException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PrometheusNonRaggiungibileException(String message, Throwable cause) {
        super(message, cause);
    }
}
