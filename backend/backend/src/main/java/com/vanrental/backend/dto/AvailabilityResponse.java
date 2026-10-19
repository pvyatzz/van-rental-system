package com.vanrental.backend.dto;

import java.time.LocalDate;

public record AvailabilityResponse(Long vehicleId, LocalDate startDate, LocalDate endDate, boolean available) {
}
