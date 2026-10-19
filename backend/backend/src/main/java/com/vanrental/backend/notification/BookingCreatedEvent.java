package com.vanrental.backend.notification;

/** Published when a customer books (after the transaction commits, NotificationService emails "request received"). */
public record BookingCreatedEvent(Long bookingId) {
}
