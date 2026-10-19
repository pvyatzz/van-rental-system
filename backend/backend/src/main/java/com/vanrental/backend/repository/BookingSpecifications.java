package com.vanrental.backend.repository;

import com.vanrental.backend.model.Booking;
import com.vanrental.backend.model.BookingStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

/**
 * Reusable WHERE-clause pieces for searching bookings. Each method returns one condition;
 * the service combines only the ones the user actually filled in.
 */
public final class BookingSpecifications {

    private BookingSpecifications() {
    }

    public static Specification<Booking> hasVehicle(Long vehicleId) {
        return (root, query, cb) -> cb.equal(root.get("vehicle").get("id"), vehicleId);
    }

    public static Specification<Booking> hasStatus(BookingStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    /** Booking is still running on or after this date (it hasn't ended before it). */
    public static Specification<Booking> endsOnOrAfter(LocalDate from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("endDate"), from);
    }

    /** Booking has started on or before this date. */
    public static Specification<Booking> startsOnOrBefore(LocalDate to) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("startDate"), to);
    }
}
