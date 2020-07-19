package com.remicartier.newisland.exception;

/**
 * Created by remicartier on 2020-07-18 12:13 p.m.
 */
public class ValidationException extends RuntimeException{
    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
