package it.govpay.console.avviso;

/**
 * Pendenza il cui debitore non ha anagrafica: il microservizio govpay-stampe richiede il
 * nome del debitore, quindi l'avviso PDF viene rifiutato prima della chiamata.
 * Mappata a 422 problem+json.
 */
public class AvvisoAnagraficaAssenteException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AvvisoAnagraficaAssenteException(String message) {
        super(message);
    }
}
