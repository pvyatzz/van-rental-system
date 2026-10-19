package com.vanrental.backend.service;

import com.vanrental.backend.dto.ReportResponse;
import com.vanrental.backend.model.Booking;
import com.vanrental.backend.model.BookingStatus;
import com.vanrental.backend.model.Vehicle;
import com.vanrental.backend.model.VehicleStatus;
import com.vanrental.backend.repository.BookingRepository;
import com.vanrental.backend.repository.VehicleRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportServiceTest {

    BookingRepository bookingRepository = mock(BookingRepository.class);
    VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    ReportService reportService = new ReportService(bookingRepository, vehicleRepository);

    Vehicle l300 = new Vehicle(1L, "Mitsubishi L300 FB", 16, new BigDecimal("3500"), VehicleStatus.AVAILABLE, null);
    Vehicle h100 = new Vehicle(3L, "Hyundai H100 (Cargo)", 3, new BigDecimal("3000"), VehicleStatus.AVAILABLE, null);

    @Test
    void sumsRevenueByMonthAndVehicle() {
        when(vehicleRepository.findAll()).thenReturn(List.of(h100, l300));
        when(bookingRepository.findByStartDateBetween(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
                .thenReturn(List.of(
                        booking(l300, "2026-03-10", "2026-03-12", BookingStatus.COMPLETED, "10500"), // 3 days
                        booking(l300, "2026-03-20", "2026-03-20", BookingStatus.CONFIRMED, "3500"),  // 1 day
                        booking(l300, "2026-11-01", "2026-11-02", BookingStatus.PENDING, "7000"),    // 2 days
                        booking(h100, "2026-03-05", "2026-03-05", BookingStatus.CONFIRMED, "3000"),  // 1 day
                        booking(h100, "2026-03-07", "2026-03-09", BookingStatus.CANCELLED, "9000")   // ignored
                ));

        ReportResponse report = reportService.yearly(2026);

        // Totals: revenue = confirmed + completed only; pending separate; cancelled only counted
        assertThat(report.totals().bookings()).isEqualTo(4);
        assertThat(report.totals().cancelled()).isEqualTo(1);
        assertThat(report.totals().revenue()).isEqualByComparingTo("17000");      // 10500 + 3500 + 3000
        assertThat(report.totals().pendingValue()).isEqualByComparingTo("7000");
        assertThat(report.totals().mostBookedVehicle()).isEqualTo("Mitsubishi L300 FB #1");

        // Months: always 12 entries; March has 3 bookings worth 17000; November is pending only
        assertThat(report.months()).hasSize(12);
        ReportResponse.MonthStats march = report.months().get(2);
        assertThat(march.bookings()).isEqualTo(3);
        assertThat(march.revenue()).isEqualByComparingTo("17000");
        ReportResponse.MonthStats november = report.months().get(10);
        assertThat(november.revenue()).isEqualByComparingTo("0");
        assertThat(november.pendingValue()).isEqualByComparingTo("7000");
        assertThat(report.months().get(0).bookings()).isZero();

        // Vehicles: most booked first; rental days skip cancelled bookings
        assertThat(report.vehicles()).extracting(ReportResponse.VehicleStats::vehicleId).containsExactly(1L, 3L);
        assertThat(report.vehicles().get(0).rentalDays()).isEqualTo(6); // 3 + 1 + 2
        assertThat(report.vehicles().get(0).revenue()).isEqualByComparingTo("14000");
        assertThat(report.vehicles().get(1).rentalDays()).isEqualTo(1);
    }

    @Test
    void emptyYearHasZeroesAndNoMostBooked() {
        when(vehicleRepository.findAll()).thenReturn(List.of(l300));
        when(bookingRepository.findByStartDateBetween(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)))
                .thenReturn(List.of());

        ReportResponse report = reportService.yearly(2025);

        assertThat(report.totals().revenue()).isEqualByComparingTo("0");
        assertThat(report.totals().mostBookedVehicle()).isNull();
        assertThat(report.months()).hasSize(12).allMatch(m -> m.bookings() == 0);
        assertThat(report.vehicles()).hasSize(1);
    }

    private Booking booking(Vehicle vehicle, String start, String end, BookingStatus status, String total) {
        Booking b = new Booking();
        b.setVehicle(vehicle);
        b.setStartDate(LocalDate.parse(start));
        b.setEndDate(LocalDate.parse(end));
        b.setStatus(status);
        b.setTotalPrice(new BigDecimal(total));
        return b;
    }
}
