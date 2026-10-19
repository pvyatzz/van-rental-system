package com.vanrental.backend.notification;

/**
 * Published by BookingService when a booking becomes CONFIRMED.
 * BookingService doesn't know (or care) who reacts to it — NotificationService listens and sends the email.
 */
public record BookingConfirmedEvent(Long bookingId) {
}
