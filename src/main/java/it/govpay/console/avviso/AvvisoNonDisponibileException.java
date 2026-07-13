package it.govpay.console.avviso;

/**
 * Pendenza priva dei dati necessari a comporre l'avviso PDF (es. codice QR
 * non derivabile per IUV assente): il payload violerebbe il contratto del
 * microservizio govpay-stampe, quindi la richiesta viene rifiutata prima
 * della chiamata. Mappata a 422 problem+json.
 */
public class AvvisoNonDisponibileException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AvvisoNonDisponibileException(String message) {
        super(message);
    }
}
