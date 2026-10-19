package com.vanrental.backend.service;

import com.vanrental.backend.dto.AvailabilityResponse;
import com.vanrental.backend.dto.BookingRequest;
import com.vanrental.backend.dto.BookingResponse;
import com.vanrental.backend.dto.CustomerBookingRequest;
import com.vanrental.backend.security.GoogleIdentity;
import com.vanrental.backend.exception.BookingConflictException;
import com.vanrental.backend.exception.InvalidRequestException;
import com.vanrental.backend.exception.ResourceNotFoundException;
import com.vanrental.backend.model.Booking;
import com.vanrental.backend.model.BookingStatus;
import com.vanrental.backend.model.Customer;
import com.vanrental.backend.model.PaymentStatus;
import com.vanrental.backend.model.Vehicle;
import com.vanrental.backend.model.VehicleStatus;
import com.vanrental.backend.notification.BookingConfirmedEvent;
import com.vanrental.backend.notification.BookingCreatedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import com.vanrental.backend.repository.BookingRepository;
import com.vanrental.backend.repository.BookingSpecifications;
import com.vanrental.backend.repository.CustomerRepository;
import com.vanrental.backend.repository.VehicleRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final VehicleRepository vehicleRepository;
    private final CustomerRepository customerRepository;
    private final CustomerService customerService;
    private final PaymentService paymentService;
    private final ApplicationEventPublisher eventPublisher;
    private final BigDecimal downPayment;

    public BookingService(BookingRepository bookingRepository,
                          VehicleRepository vehicleRepository,
                          CustomerRepository customerRepository,
                          CustomerService customerService,
                          PaymentService paymentService,
                          ApplicationEventPublisher eventPublisher,
                          @Value("${app.booking.down-payment}") BigDecimal downPayment) {
        this.bookingRepository = bookingRepository;
        this.vehicleRepository = vehicleRepository;
        this.customerRepository = customerRepository;
        this.customerService = customerService;
        this.paymentService = paymentService;
        this.eventPublisher = eventPublisher;
        this.downPayment = downPayment;
    }

    public BigDecimal downPayment() {
        return downPayment;
    }

    /**
     * A signed-in customer books and attaches proof of the down payment.
     * Everything happens in ONE transaction: if anything fails (bad image, dates taken),
     * no customer, booking or image is left behind.
     */
    @Transactional
    public BookingResponse createForCustomer(CustomerBookingRequest request, GoogleIdentity identity,
                                             MultipartFile paymentProof) {
        validateDates(request.startDate(), request.endDate());
        PaymentService.ValidatedImage proof = paymentService.validate(paymentProof); // before saving anything
        Customer customer = customerService.findOrCreateFromGoogle(identity, request.contactNumber());
        Booking booking = createBooking(request.withCustomer(customer.getId()));
        booking.setDownPayment(downPayment.min(booking.getTotalPrice())); // never more than the whole price
        paymentService.attachProof(booking, proof);
        eventPublisher.publishEvent(new BookingCreatedEvent(booking.getId())); // "request received" email
        return BookingResponse.from(booking);
    }

    /** The signed-in customer's own bookings, newest first */
    @Transactional(readOnly = true)
    public List<BookingResponse> findMine(String googleId) {
        return bookingRepository.findByCustomerGoogleIdOrderByStartDateDesc(googleId).stream()
                .map(BookingResponse::from)
                .toList();
    }

    /** All filters are optional (null = don't filter on it). from/to keep bookings that overlap that window. */
    @Transactional(readOnly = true)
    public List<BookingResponse> search(Long vehicleId, BookingStatus status, LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new InvalidRequestException("'to' date must be on or after 'from' date");
        }
        List<Specification<Booking>> filters = new ArrayList<>();
        if (vehicleId != null) {
            filters.add(BookingSpecifications.hasVehicle(vehicleId));
        }
        if (status != null) {
            filters.add(BookingSpecifications.hasStatus(status));
        }
        if (from != null) {
            filters.add(BookingSpecifications.endsOnOrAfter(from));
        }
        if (to != null) {
            filters.add(BookingSpecifications.startsOnOrBefore(to));
        }
        return bookingRepository.findAll(Specification.allOf(filters), Sort.by(Sort.Direction.DESC, "startDate"))
                .stream()
                .map(BookingResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public BookingResponse findById(Long id) {
        return BookingResponse.from(getBooking(id));
    }

    /** Public lookup for the customer confirmation page. Matches the code case-insensitively. */
    @Transactional(readOnly = true)
    public BookingResponse findByReference(String reference) {
        return bookingRepository.findWithDetailsByReference(reference.trim().toUpperCase())
                .map(BookingResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Booking with reference " + reference + " not found"));
    }

    @Transactional
    public BookingResponse create(BookingRequest request) {
        return BookingResponse.from(createBooking(request));
    }

    private Booking createBooking(BookingRequest request) {
        validateDates(request.startDate(), request.endDate());
        validatePickup(request);
        Vehicle vehicle = lockVehicle(request.vehicleId());
        Customer customer = getCustomer(request.customerId());
        ensureBookable(vehicle, request.startDate(), request.endDate(), null);

        Booking booking = new Booking();
        applyRequest(booking, request, vehicle, customer);
        booking.setStatus(BookingStatus.PENDING);
        return bookingRepository.save(booking);
    }

    @Transactional
    public BookingResponse update(Long id, BookingRequest request) {
        Booking booking = getBooking(id);
        if (booking.getStatus() == BookingStatus.CANCELLED || booking.getStatus() == BookingStatus.COMPLETED) {
            throw new InvalidRequestException("A " + booking.getStatus() + " booking can no longer be changed");
        }
        validateDates(request.startDate(), request.endDate());
        validatePickup(request);
        Vehicle vehicle = lockVehicle(request.vehicleId());
        Customer customer = getCustomer(request.customerId());
        // exclude this booking itself, otherwise it would "overlap" with its own old dates
        ensureBookable(vehicle, request.startDate(), request.endDate(), booking.getId());

        applyRequest(booking, request, vehicle, customer);
        return BookingResponse.from(booking); // managed entity: changes are saved on commit
    }

    /** Copies the client's fields onto the booking and recalculates the price (shared by create and update). */
    private void applyRequest(Booking booking, BookingRequest request, Vehicle vehicle, Customer customer) {
        booking.setVehicle(vehicle);
        booking.setCustomer(customer);
        booking.setStartDate(request.startDate());
        booking.setEndDate(request.endDate());
        booking.setPurpose(request.purpose());
        booking.setPickupAddress(request.pickupAddress() == null || request.pickupAddress().isBlank()
                ? null : request.pickupAddress().trim());
        booking.setPickupLatitude(request.pickupLatitude());
        booking.setPickupLongitude(request.pickupLongitude());
        booking.setTotalPrice(calculatePrice(vehicle.getRatePerDay(), request.startDate(), request.endDate()));
    }

    /** A map pin needs both numbers: latitude without longitude (or the other way round) is not a place. */
    private void validatePickup(BookingRequest request) {
        if ((request.pickupLatitude() == null) != (request.pickupLongitude() == null)) {
            throw new InvalidRequestException("Pickup latitude and longitude must be given together");
        }
    }

    @Transactional
    public BookingResponse changeStatus(Long id, BookingStatus newStatus) {
        Booking booking = getBooking(id);
        if (!booking.getStatus().canChangeTo(newStatus)) {
            throw new InvalidRequestException(
                    "Cannot change booking status from " + booking.getStatus() + " to " + newStatus);
        }
        // Accepting a booking means the down payment arrived: the admin must verify it first
        if (newStatus == BookingStatus.CONFIRMED && booking.getPaymentStatus() != null
                && booking.getPaymentStatus() != PaymentStatus.VERIFIED) {
            throw new InvalidRequestException("Verify the down payment before confirming this booking");
        }
        booking.setStatus(newStatus);
        if (newStatus == BookingStatus.CONFIRMED) {
            // Listeners (the email) run after this transaction commits — see NotificationService
            eventPublisher.publishEvent(new BookingConfirmedEvent(booking.getId()));
        }
        return BookingResponse.from(booking);
    }

    @Transactional
    public void delete(Long id) {
        Booking booking = getBooking(id);
        paymentService.deleteProofOf(id); // the proof points at the booking, so it goes first
        bookingRepository.delete(booking);
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse checkAvailability(Long vehicleId, LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new InvalidRequestException("End date must be on or after start date");
        }
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle", vehicleId));
        boolean available = vehicle.getStatus() != VehicleStatus.UNDER_MAINTENANCE
                && bookingRepository.findOverlapping(vehicleId, startDate, endDate).isEmpty();
        return new AvailabilityResponse(vehicleId, startDate, endDate, available);
    }

    /** Rental days are inclusive: 20–22 Oct is 3 days; a same-day rental is 1 day. */
    static BigDecimal calculatePrice(BigDecimal ratePerDay, LocalDate startDate, LocalDate endDate) {
        long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        return ratePerDay.multiply(BigDecimal.valueOf(days));
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new InvalidRequestException("End date must be on or after start date");
        }
    }

    private void ensureBookable(Vehicle vehicle, LocalDate startDate, LocalDate endDate, Long excludeBookingId) {
        if (vehicle.getStatus() == VehicleStatus.UNDER_MAINTENANCE) {
            throw new BookingConflictException(vehicle.getModel() + " is under maintenance and cannot be booked");
        }
        List<Booking> clashes = bookingRepository.findOverlapping(vehicle.getId(), startDate, endDate).stream()
                .filter(b -> !Objects.equals(b.getId(), excludeBookingId))
                .toList();
        if (!clashes.isEmpty()) {
            Booking first = clashes.getFirst();
            throw new BookingConflictException("Vehicle is already booked from " + first.getStartDate()
                    + " to " + first.getEndDate() + " (booking #" + first.getId() + ")");
        }
    }

    private Vehicle lockVehicle(Long vehicleId) {
        return vehicleRepository.findByIdForUpdate(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle", vehicleId));
    }

    private Customer getCustomer(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));
    }

    private Booking getBooking(Long id) {
        return bookingRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", id));
    }
}
