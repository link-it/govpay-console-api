package it.govpay.console.eventi;

/**
 * Lanciata quando la chiamata al servizio GDE fallisce (timeout, errore HTTP,
 * errore IO, circuito aperto). Mappata a 502 problem+json.
 */
public class GdeNonRaggiungibileException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public GdeNonRaggiungibileException(String message, Throwable cause) {
        super(message, cause);
    }
}
