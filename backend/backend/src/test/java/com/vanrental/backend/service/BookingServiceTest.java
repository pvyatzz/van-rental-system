package com.vanrental.backend.service;

import com.vanrental.backend.dto.BookingRequest;
import com.vanrental.backend.dto.BookingResponse;
import com.vanrental.backend.dto.CustomerBookingRequest;
import com.vanrental.backend.security.GoogleIdentity;
import com.vanrental.backend.exception.BookingConflictException;
import com.vanrental.backend.exception.InvalidRequestException;
import com.vanrental.backend.model.Booking;
import com.vanrental.backend.model.BookingStatus;
import com.vanrental.backend.model.Customer;
import com.vanrental.backend.model.PaymentStatus;
import com.vanrental.backend.model.Vehicle;
import com.vanrental.backend.model.VehicleStatus;
import com.vanrental.backend.notification.BookingConfirmedEvent;
import com.vanrental.backend.notification.BookingCreatedEvent;
import com.vanrental.backend.repository.BookingRepository;
import com.vanrental.backend.repository.CustomerRepository;
import com.vanrental.backend.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

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
    @Mock
    CustomerService customerService;
    @Mock
    PaymentService paymentService;
    @Mock
    ApplicationEventPublisher eventPublisher;

    BookingService bookingService;

    Vehicle van;
    Customer customer;
    LocalDate start;
    LocalDate end;

    @BeforeEach
    void setUp() {
        // Built by hand (not @InjectMocks) because the ₱500 down payment is a plain value, not a mock
        bookingService = new BookingService(bookingRepository, vehicleRepository, customerRepository,
                customerService, paymentService, eventPublisher, new BigDecimal("500"));
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

        BookingResponse result = bookingService.create(new BookingRequest(1L, 10L, start, end, "Family outing", null, null, null));

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

        assertThatThrownBy(() -> bookingService.create(new BookingRequest(1L, 10L, start, end, null, null, null, null)))
                .isInstanceOf(BookingConflictException.class)
                .hasMessageContaining("booking #99");
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createRejectsVehicleUnderMaintenance() {
        van.setStatus(VehicleStatus.UNDER_MAINTENANCE);
        when(vehicleRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(van));
        when(customerRepository.findById(10L)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> bookingService.create(new BookingRequest(1L, 10L, start, end, null, null, null, null)))
                .isInstanceOf(BookingConflictException.class)
                .hasMessageContaining("maintenance");
    }

    @Test
    void createRejectsEndDateBeforeStartDate() {
        assertThatThrownBy(() -> bookingService.create(new BookingRequest(1L, 10L, end, start, null, null, null, null)))
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

        BookingResponse result = bookingService.update(5L, new BookingRequest(1L, 10L, start, newEnd, null, null, null, null));

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

    @Test
    void signedInCustomerBooksUnderTheirOwnGoogleAccount() {
        GoogleIdentity maria = new GoogleIdentity("google-123", "maria@gmail.com", "Maria Santos");
        Customer linked = new Customer(10L, "Maria Santos", "09171234567", "maria@gmail.com", "google-123");
        when(customerService.findOrCreateFromGoogle(maria, "09171234567")).thenReturn(linked);
        when(vehicleRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(van));
        when(customerRepository.findById(10L)).thenReturn(Optional.of(linked));
        when(bookingRepository.findOverlapping(1L, start, end)).thenReturn(List.of());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        MultipartFile screenshot = new MockMultipartFile("paymentProof", "gcash.png", "image/png", new byte[]{1});
        PaymentService.ValidatedImage image = new PaymentService.ValidatedImage(ImageType.PNG, new byte[]{1}, "gcash.png");
        when(paymentService.validate(screenshot)).thenReturn(image);

        BookingResponse result = bookingService.createForCustomer(
                new CustomerBookingRequest(1L, "09171234567", start, end, null, null, null, null), maria, screenshot);

        assertThat(result.customerId()).isEqualTo(10L);
        assertThat(result.customerName()).isEqualTo("Maria Santos");
        assertThat(result.downPayment()).isEqualByComparingTo("500");
        assertThat(result.balanceDue()).isEqualByComparingTo("10000"); // 10,500 − 500
        verify(paymentService).attachProof(any(Booking.class), org.mockito.ArgumentMatchers.eq(image));
        verify(eventPublisher).publishEvent(any(BookingCreatedEvent.class)); // "request received" email
    }

    @Test
    void badDatesAreRejectedBeforeAnyCustomerIsCreated() {
        GoogleIdentity maria = new GoogleIdentity("google-123", "maria@gmail.com", "Maria Santos");
        assertThatThrownBy(() -> bookingService.createForCustomer(
                new CustomerBookingRequest(1L, "0917", end, start, null, null, null, null), maria, null))
                .isInstanceOf(InvalidRequestException.class);
        verify(customerService, never()).findOrCreateFromGoogle(any(), any());
    }

    @Test
    void anInvalidProofStopsTheBookingBeforeAnythingIsSaved() {
        GoogleIdentity maria = new GoogleIdentity("google-123", "maria@gmail.com", "Maria Santos");
        when(paymentService.validate(null)).thenThrow(new InvalidRequestException("Please attach a screenshot"));
        assertThatThrownBy(() -> bookingService.createForCustomer(
                new CustomerBookingRequest(1L, "0917", start, end, null, null, null, null), maria, null))
                .isInstanceOf(InvalidRequestException.class);
        verify(customerService, never()).findOrCreateFromGoogle(any(), any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void cannotConfirmBeforeTheDownPaymentIsVerified() {
        Booking pending = booking(5L, start, end, BookingStatus.PENDING);
        pending.setPaymentStatus(PaymentStatus.SUBMITTED);
        when(bookingRepository.findWithDetailsById(5L)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> bookingService.changeStatus(5L, BookingStatus.CONFIRMED))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Verify the down payment");
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void canConfirmOnceTheDownPaymentIsVerified() {
        Booking pending = booking(5L, start, end, BookingStatus.PENDING);
        pending.setPaymentStatus(PaymentStatus.VERIFIED);
        when(bookingRepository.findWithDetailsById(5L)).thenReturn(Optional.of(pending));

        assertThat(bookingService.changeStatus(5L, BookingStatus.CONFIRMED).status()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void createStoresThePickupLocation() {
        when(vehicleRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(van));
        when(customerRepository.findById(10L)).thenReturn(Optional.of(customer));
        when(bookingRepository.findOverlapping(1L, start, end)).thenReturn(List.of());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingResponse result = bookingService.create(
                new BookingRequest(1L, 10L, start, end, null, "  SM Megamall, Ortigas  ", 14.5849, 121.0563));

        assertThat(result.pickupAddress()).isEqualTo("SM Megamall, Ortigas"); // trimmed
        assertThat(result.pickupLatitude()).isEqualTo(14.5849);
        assertThat(result.pickupLongitude()).isEqualTo(121.0563);
    }

    @Test
    void pickupLatitudeWithoutLongitudeIsRejected() {
        assertThatThrownBy(() -> bookingService.create(
                new BookingRequest(1L, 10L, start, end, null, null, 14.5849, null)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("together");
    }

    @Test
    void confirmingPublishesTheEmailEvent() {
        Booking pending = booking(5L, start, end, BookingStatus.PENDING);
        when(bookingRepository.findWithDetailsById(5L)).thenReturn(Optional.of(pending));

        bookingService.changeStatus(5L, BookingStatus.CONFIRMED);

        verify(eventPublisher).publishEvent(new BookingConfirmedEvent(5L));
    }

    @Test
    void cancellingSendsNoConfirmationEmail() {
        Booking pending = booking(5L, start, end, BookingStatus.PENDING);
        when(bookingRepository.findWithDetailsById(5L)).thenReturn(Optional.of(pending));

        bookingService.changeStatus(5L, BookingStatus.CANCELLED);

        verify(eventPublisher, never()).publishEvent(any(Object.class));
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
