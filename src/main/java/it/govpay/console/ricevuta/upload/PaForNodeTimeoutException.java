package it.govpay.console.ricevuta.upload;

/**
 * Timeout verso {@code api-pagopa} (paForNode) durante il caricamento di una
 * RT, dopo che la rilettura ha confermato che la RT
 * <b>non</b> e' stata acquisita. Mappata a 504 problem+json.
 */
public class PaForNodeTimeoutException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PaForNodeTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
