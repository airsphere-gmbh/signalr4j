package com.github.signalr4j.client;

public class MissingExceptionException extends RuntimeException {
    private static final long serialVersionUID = 2754013197945989794L;

    public MissingExceptionException(String message) {
        super("The exception for the error callback was missing: '" + message);
    }
}
