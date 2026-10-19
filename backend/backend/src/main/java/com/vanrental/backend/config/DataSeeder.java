package com.vanrental.backend.config;

import com.vanrental.backend.model.Booking;
import com.vanrental.backend.model.BookingReference;
import com.vanrental.backend.model.Vehicle;
import com.vanrental.backend.model.VehicleStatus;
import com.vanrental.backend.repository.BookingRepository;
import com.vanrental.backend.repository.VehicleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Runs once at startup:
 * 1. Inserts the starting fleet when the vehicles table is empty.
 *    Rates are placeholders — edit them in Admin → Vans.
 * 2. Gives a reference code to bookings created before references existed (Phase 6).
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final VehicleRepository vehicleRepository;
    private final BookingRepository bookingRepository;

    public DataSeeder(VehicleRepository vehicleRepository, BookingRepository bookingRepository) {
        this.vehicleRepository = vehicleRepository;
        this.bookingRepository = bookingRepository;
    }

    @Override
    public void run(String... args) {
        seedVehicles();
        backfillBookingReferences();
    }

    private void seedVehicles() {
        if (vehicleRepository.count() > 0) {
            return;
        }
        vehicleRepository.saveAll(List.of(
                new Vehicle(null, "Mitsubishi L300 FB", 16, new BigDecimal("3500.00"),
                        VehicleStatus.AVAILABLE, "/images/l300-fb.png"),
                new Vehicle(null, "Mitsubishi L300 FB", 16, new BigDecimal("3500.00"),
                        VehicleStatus.AVAILABLE, "/images/l300-fb.png"),
                new Vehicle(null, "Hyundai H100 (Cargo)", 3, new BigDecimal("3000.00"),
                        VehicleStatus.AVAILABLE, "/images/h100.png")
        ));
    }

    private void backfillBookingReferences() {
        List<Booking> withoutReference = bookingRepository.findByReferenceIsNull();
        withoutReference.forEach(booking -> booking.setReference(BookingReference.generate()));
        bookingRepository.saveAll(withoutReference);
    }
}
