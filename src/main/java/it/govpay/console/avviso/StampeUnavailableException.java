package it.govpay.console.avviso;

/**
 * Lanciata quando la chiamata al microservizio {@code govpay-stampe} fallisce
 * (timeout, errore HTTP, errore IO). Mappata a 502 problem+json.
 */
public class StampeUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public StampeUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
