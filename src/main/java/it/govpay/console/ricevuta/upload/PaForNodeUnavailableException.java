package it.govpay.console.ricevuta.upload;

/**
 * Fallimento di trasporto verso {@code api-pagopa} (paForNode) durante il
 * caricamento di una RT, dopo che la rilettura ha confermato che
 * la RT <b>non</b> e' stata acquisita. Mappata a 502 problem+json.
 */
public class PaForNodeUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PaForNodeUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
