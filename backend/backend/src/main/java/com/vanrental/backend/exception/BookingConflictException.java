package com.vanrental.backend.exception;

/** The request is valid, but clashes with the current state (e.g. dates already booked). → 409 */
public class BookingConflictException extends RuntimeException {

    public BookingConflictException(String message) {
        super(message);
    }
}
