package com.vanrental.backend.model;

import java.util.Set;

public enum BookingStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    COMPLETED;

    /** Allowed status changes. CANCELLED and COMPLETED are final. */
    public boolean canChangeTo(BookingStatus next) {
        return switch (this) {
            case PENDING -> Set.of(CONFIRMED, CANCELLED).contains(next);
            case CONFIRMED -> Set.of(COMPLETED, CANCELLED).contains(next);
            case CANCELLED, COMPLETED -> false;
        };
    }
}
