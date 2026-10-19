package com.vanrental.backend.notification;

/** Published when the admin rejects a down-payment proof, so the customer is asked to upload a new one. */
public record PaymentRejectedEvent(Long bookingId) {
}
