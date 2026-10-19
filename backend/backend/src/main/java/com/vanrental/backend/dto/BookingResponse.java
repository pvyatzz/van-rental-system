package com.vanrental.backend.dto;

import com.vanrental.backend.model.Booking;
import com.vanrental.backend.model.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** What the API returns for a booking: flat and safe to serialize (no lazy JPA proxies). */
public record BookingResponse(
        Long id,
        Long vehicleId,
        String vehicleModel,
        Long customerId,
        String customerName,
        String customerContactNumber,
        LocalDate startDate,
        LocalDate endDate,
        String purpose,
        BookingStatus status,
        BigDecimal totalPrice,
        Instant createdAt
) {
    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getVehicle().getId(),
                booking.getVehicle().getModel(),
                booking.getCustomer().getId(),
                booking.getCustomer().getFullName(),
                booking.getCustomer().getContactNumber(),
                booking.getStartDate(),
                booking.getEndDate(),
                booking.getPurpose(),
                booking.getStatus(),
                booking.getTotalPrice(),
                booking.getCreatedAt()
        );
    }
}
