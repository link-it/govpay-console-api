package it.govpay.console.web;

public class BadRequestException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String field;

    public BadRequestException(String message) {
        this(message, null);
    }

    /**
     * @param field nome del parametro/campo non valido, riportato in
     *              {@code errors[0].field} nella problem detail; {@code null}
     *              se l'errore non e' imputabile a un singolo campo.
     */
    public BadRequestException(String message, String field) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
