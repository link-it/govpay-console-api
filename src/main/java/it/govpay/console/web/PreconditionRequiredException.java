package it.govpay.console.web;

public class PreconditionRequiredException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PreconditionRequiredException(String message) {
        super(message);
    }

    public PreconditionRequiredException(String message, Throwable cause) {
        super(message, cause);
    }
}
