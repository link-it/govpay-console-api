package it.govpay.console.pagination;

import it.govpay.console.web.BadRequestException;

/**
 * Cursor di paginazione non valido (formato/codifica errati). Mappato a 400
 * problem+json via il gestore di {@link BadRequestException}.
 */
public class BadCursorException extends BadRequestException {

    private static final long serialVersionUID = 1L;

    public BadCursorException(String message) {
        super(message);
    }

    public BadCursorException(String message, Throwable cause) {
        super(message);
        initCause(cause);
    }
}
