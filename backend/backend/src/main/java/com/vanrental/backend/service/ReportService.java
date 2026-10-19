package com.vanrental.backend.service;

import com.vanrental.backend.dto.ReportResponse;
import com.vanrental.backend.dto.ReportResponse.MonthStats;
import com.vanrental.backend.dto.ReportResponse.Totals;
import com.vanrental.backend.dto.ReportResponse.VehicleStats;
import com.vanrental.backend.model.Booking;
import com.vanrental.backend.model.BookingStatus;
import com.vanrental.backend.model.Vehicle;
import com.vanrental.backend.repository.BookingRepository;
import com.vanrental.backend.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.IntStream;

/**
 * Builds the yearly report in Java with streams. For a small business (hundreds of bookings a year)
 * loading one year of bookings and grouping in memory is simple and fast enough.
 * With much more data you would let the database do it (SQL GROUP BY).
 */
@Service
public class ReportService {

    private static final Predicate<Booking> NOT_CANCELLED = b -> b.getStatus() != BookingStatus.CANCELLED;
    private static final Predicate<Booking> EARNING =
            b -> b.getStatus() == BookingStatus.CONFIRMED || b.getStatus() == BookingStatus.COMPLETED;
    private static final Predicate<Booking> PENDING = b -> b.getStatus() == BookingStatus.PENDING;

    private final BookingRepository bookingRepository;
    private final VehicleRepository vehicleRepository;

    public ReportService(BookingRepository bookingRepository, VehicleRepository vehicleRepository) {
        this.bookingRepository = bookingRepository;
        this.vehicleRepository = vehicleRepository;
    }

    @Transactional(readOnly = true)
    public ReportResponse yearly(int year) {
        List<Booking> bookings = bookingRepository.findByStartDateBetween(
                LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));

        // One entry per month, including empty months, so the chart always has 12 bars
        List<MonthStats> months = IntStream.rangeClosed(1, 12)
                .mapToObj(month -> {
                    List<Booking> inMonth = bookings.stream()
                            .filter(b -> b.getStartDate().getMonthValue() == month)
                            .toList();
                    return new MonthStats(month, count(inMonth, NOT_CANCELLED),
                            sum(inMonth, EARNING), sum(inMonth, PENDING));
                })
                .toList();

        // One entry per van (including vans with no bookings), most booked first
        List<VehicleStats> vehicles = vehicleRepository.findAll().stream()
                .map(vehicle -> vehicleStats(vehicle, bookings))
                .sorted(Comparator.comparingLong(VehicleStats::bookings).reversed()
                        .thenComparing(Comparator.comparingLong(VehicleStats::rentalDays).reversed()))
                .toList();

        String mostBooked = vehicles.isEmpty() || vehicles.getFirst().bookings() == 0
                ? null
                : vehicles.getFirst().model() + " #" + vehicles.getFirst().vehicleId();

        Totals totals = new Totals(count(bookings, NOT_CANCELLED), count(bookings, NOT_CANCELLED.negate()),
                sum(bookings, EARNING), sum(bookings, PENDING), mostBooked);
        return new ReportResponse(year, totals, months, vehicles);
    }

    private VehicleStats vehicleStats(Vehicle vehicle, List<Booking> bookings) {
        List<Booking> forVehicle = bookings.stream()
                .filter(b -> b.getVehicle().getId().equals(vehicle.getId()))
                .toList();
        long rentalDays = forVehicle.stream()
                .filter(NOT_CANCELLED)
                .mapToLong(b -> ChronoUnit.DAYS.between(b.getStartDate(), b.getEndDate()) + 1)
                .sum();
        return new VehicleStats(vehicle.getId(), vehicle.getModel(),
                count(forVehicle, NOT_CANCELLED), rentalDays, sum(forVehicle, EARNING));
    }

    private static long count(List<Booking> bookings, Predicate<Booking> filter) {
        return bookings.stream().filter(filter).count();
    }

    private static BigDecimal sum(List<Booking> bookings, Predicate<Booking> filter) {
        return bookings.stream()
                .filter(filter)
                .map(Booking::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add); // start at 0, add each price
    }
}
