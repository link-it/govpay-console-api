package it.govpay.console.web;

/**
 * Lanciata quando l'header {@code Accept} del client non e' compatibile con
 * nessuno dei content type supportati dall'endpoint. Mappata a 406 problem+json
 * dal {@link ProblemExceptionHandler}.
 *
 * <p>Generica: usata da tutti gli endpoint che fanno content negotiation
 * (es. {@code /avviso}, {@code /ricevuta}).
 */
public class NotAcceptableMediaTypeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NotAcceptableMediaTypeException(String message) {
        super(message);
    }
}
