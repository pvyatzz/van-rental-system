package com.vanrental.backend.dto;

import com.vanrental.backend.model.BookingStatus;
import jakarta.validation.constraints.NotNull;

public record BookingStatusUpdate(@NotNull BookingStatus status) {
}
