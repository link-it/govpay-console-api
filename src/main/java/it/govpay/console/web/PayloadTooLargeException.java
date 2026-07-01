package it.govpay.console.web;

public class PayloadTooLargeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PayloadTooLargeException(String message) {
        super(message);
    }
}
