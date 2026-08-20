package it.govpay.console.eventi;

/**
 * Lanciata quando il connettore {@code servizioGDE} non e' configurato/abilitato
 * (vedi {@code /impostazioni/servizioGDE}): la consultazione del giornale
 * eventi non e' disponibile finche' non viene configurato. Mappata a 503
 * problem+json.
 */
public class GdeNonConfiguratoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public GdeNonConfiguratoException() {
        super("Il servizio GDE (Giornale degli Eventi) non e' configurato o non e' abilitato.");
    }
}
