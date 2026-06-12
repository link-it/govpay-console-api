package it.govpay.console.avviso;

/**
 * Lanciata quando l'header {@code Accept} del client non e' compatibile con
 * nessuno dei content type supportati. Mappata a 406 problem+json.
 */
public class AvvisoNonAccettabileException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AvvisoNonAccettabileException(String message) {
        super(message);
    }
}
