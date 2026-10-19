package com.vanrental.backend.exception;

/** A business rule the client broke that annotations can't express (e.g. end before start). → 400 */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
