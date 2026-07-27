package it.govpay.console.operazioni;

/**
 * Lanciata quando la chiamata al microservizio proprietario del job fallisce
 * (timeout, errore HTTP, errore IO, circuito aperto). Mappata a 502 problem+json.
 */
public class OperazioneTriggerNonRaggiungibileException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public OperazioneTriggerNonRaggiungibileException(String message, Throwable cause) {
        super(message, cause);
    }
}
