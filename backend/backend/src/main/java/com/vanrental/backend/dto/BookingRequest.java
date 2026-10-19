package com.vanrental.backend.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** What the client sends to create or change a booking. Only ids, not whole objects. */
public record BookingRequest(
        @NotNull Long vehicleId,
        @NotNull Long customerId,
        @NotNull @FutureOrPresent LocalDate startDate,
        @NotNull LocalDate endDate,
        @Size(max = 255) String purpose
) {
}
