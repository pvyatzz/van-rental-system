package com.vanrental.backend.dto;

import com.vanrental.backend.model.Booking;
import com.vanrental.backend.model.BookingStatus;
import com.vanrental.backend.model.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** What the API returns for a booking: flat and safe to serialize (no lazy JPA proxies). */
public record BookingResponse(
        Long id,
        String reference,
        Long vehicleId,
        String vehicleModel,
        Long customerId,
        String customerName,
        String customerContactNumber,
        String customerEmail,
        LocalDate startDate,
        LocalDate endDate,
        String purpose,
        String pickupAddress,
        Double pickupLatitude,
        Double pickupLongitude,
        BookingStatus status,
        BigDecimal totalPrice,
        BigDecimal downPayment,   // null for bookings made before down payments existed
        BigDecimal balanceDue,    // totalPrice minus the down payment: what's left to pay at pickup
        PaymentStatus paymentStatus,
        Instant createdAt
) {
    public static BookingResponse from(Booking booking) {
        BigDecimal down = booking.getDownPayment();
        BigDecimal balance = down == null ? booking.getTotalPrice() : booking.getTotalPrice().subtract(down);
        return new BookingResponse(
                booking.getId(),
                booking.getReference(),
                booking.getVehicle().getId(),
                booking.getVehicle().getModel(),
                booking.getCustomer().getId(),
                booking.getCustomer().getFullName(),
                booking.getCustomer().getContactNumber(),
                booking.getCustomer().getEmail(),
                booking.getStartDate(),
                booking.getEndDate(),
                booking.getPurpose(),
                booking.getPickupAddress(),
                booking.getPickupLatitude(),
                booking.getPickupLongitude(),
                booking.getStatus(),
                booking.getTotalPrice(),
                down,
                balance,
                booking.getPaymentStatus(),
                booking.getCreatedAt()
        );
    }
}
