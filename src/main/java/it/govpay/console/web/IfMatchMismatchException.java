package it.govpay.console.web;

public class IfMatchMismatchException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public IfMatchMismatchException(String message) {
        super(message);
    }

    public IfMatchMismatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
