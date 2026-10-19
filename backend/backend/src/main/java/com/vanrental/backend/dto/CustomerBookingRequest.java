package com.vanrental.backend.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * What a signed-in customer sends to book. There is no customerId: the server takes the customer from the
 * Google sign-in token, so nobody can book in someone else's name. Name and email come from Google too.
 */
public record CustomerBookingRequest(
        @NotNull Long vehicleId,
        @NotBlank @Size(max = 20) String contactNumber,
        @NotNull @FutureOrPresent LocalDate startDate,
        @NotNull LocalDate endDate,
        @Size(max = 255) String purpose,
        @Size(max = 255) String pickupAddress,
        @DecimalMin("-90") @DecimalMax("90") Double pickupLatitude,
        @DecimalMin("-180") @DecimalMax("180") Double pickupLongitude
) {
    /** The same booking in the admin's request shape, for the shared booking logic */
    public BookingRequest withCustomer(Long customerId) {
        return new BookingRequest(vehicleId, customerId, startDate, endDate, purpose,
                pickupAddress, pickupLatitude, pickupLongitude);
    }
}
