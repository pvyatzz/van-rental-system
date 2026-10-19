package com.vanrental.backend.service;

import com.vanrental.backend.dto.BookingRequest;
import com.vanrental.backend.dto.BookingResponse;
import com.vanrental.backend.exception.BookingConflictException;
import com.vanrental.backend.exception.InvalidRequestException;
import com.vanrental.backend.model.Booking;
import com.vanrental.backend.model.BookingStatus;
import com.vanrental.backend.model.Customer;
import com.vanrental.backend.model.Vehicle;
import com.vanrental.backend.model.VehicleStatus;
import com.vanrental.backend.repository.BookingRepository;
import com.vanrental.backend.repository.CustomerRepository;
import com.vanrental.backend.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the booking business rules. The repositories are Mockito mocks,
 * so no database is needed and each test controls exactly what the "database" returns.
 */
@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    BookingRepository bookingRepository;
    @Mock
    VehicleRepository vehicleRepository;
    @Mock
    CustomerRepository customerRepository;

    @InjectMocks
    BookingService bookingService;

    Vehicle van;
    Customer customer;
    LocalDate start;
    LocalDate end;

    @BeforeEach
    void setUp() {
        van = new Vehicle(1L, "Mitsubishi L300 FB", 16, new BigDecimal("3500.00"), VehicleStatus.AVAILABLE, null);
        customer = new Customer(10L, "Juan Dela Cruz", "09171234567", null);
        start = LocalDate.now().plusDays(7);
        end = start.plusDays(2); // 3 rental days
    }

    @Test
    void priceCountsBothStartAndEndDay() {
        BigDecimal rate = new BigDecimal("3500.00");
        assertThat(BookingService.calculatePrice(rate, start, start)).isEqualByComparingTo("3500.00");
        assertThat(BookingService.calculatePrice(rate, start, start.plusDays(2))).isEqualByComparingTo("10500.00");
    }

    @Test
    void createSavesPendingBookingWithPrice() {
        when(vehicleRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(van));
        when(customerRepository.findById(10L)).thenReturn(Optional.of(customer));
        when(bookingRepository.findOverlapping(1L, start, end)).thenReturn(List.of());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingResponse result = bookingService.create(new BookingRequest(1L, 10L, start, end, "Family outing"));

        assertThat(result.status()).isEqualTo(BookingStatus.PENDING);
        assertThat(result.totalPrice()).isEqualByComparingTo("10500.00");
        assertThat(result.customerName()).isEqualTo("Juan Dela Cruz");
    }

    @Test
    void createRejectsOverlappingDates() {
        Booking existing = booking(99L, start.plusDays(1), end.plusDays(3), BookingStatus.CONFIRMED);
        when(vehicleRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(van));
        when(customerRepository.findById(10L)).thenReturn(Optional.of(customer));
        when(bookingRepository.findOverlapping(1L, start, end)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> bookingService.create(new BookingRequest(1L, 10L, start, end, null)))
                .isInstanceOf(BookingConflictException.class)
                .hasMessageContaining("booking #99");
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createRejectsVehicleUnderMaintenance() {
        van.setStatus(VehicleStatus.UNDER_MAINTENANCE);
        when(vehicleRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(van));
        when(customerRepository.findById(10L)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> bookingService.create(new BookingRequest(1L, 10L, start, end, null)))
                .isInstanceOf(BookingConflictException.class)
                .hasMessageContaining("maintenance");
    }

    @Test
    void createRejectsEndDateBeforeStartDate() {
        assertThatThrownBy(() -> bookingService.create(new BookingRequest(1L, 10L, end, start, null)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void updateDoesNotConflictWithItsOwnOldDates() {
        Booking own = booking(5L, start, end, BookingStatus.PENDING);
        when(bookingRepository.findWithDetailsById(5L)).thenReturn(Optional.of(own));
        when(vehicleRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(van));
        when(customerRepository.findById(10L)).thenReturn(Optional.of(customer));
        LocalDate newEnd = end.plusDays(1);
        when(bookingRepository.findOverlapping(1L, start, newEnd)).thenReturn(List.of(own));

        BookingResponse result = bookingService.update(5L, new BookingRequest(1L, 10L, start, newEnd, null));

        assertThat(result.endDate()).isEqualTo(newEnd);
        assertThat(result.totalPrice()).isEqualByComparingTo("14000.00");
    }

    @Test
    void cancelledBookingCannotBeReconfirmed() {
        Booking cancelled = booking(5L, start, end, BookingStatus.CANCELLED);
        when(bookingRepository.findWithDetailsById(5L)).thenReturn(Optional.of(cancelled));

        assertThatThrownBy(() -> bookingService.changeStatus(5L, BookingStatus.CONFIRMED))
                .isInstanceOf(InvalidRequestException.class);
    }

    private Booking booking(Long id, LocalDate from, LocalDate to, BookingStatus status) {
        Booking b = new Booking();
        b.setId(id);
        b.setVehicle(van);
        b.setCustomer(customer);
        b.setStartDate(from);
        b.setEndDate(to);
        b.setStatus(status);
        b.setTotalPrice(BookingService.calculatePrice(van.getRatePerDay(), from, to));
        return b;
    }
}
