package com.vanrental.backend.service;

import com.vanrental.backend.dto.AvailabilityResponse;
import com.vanrental.backend.dto.BookingRequest;
import com.vanrental.backend.dto.BookingResponse;
import com.vanrental.backend.exception.BookingConflictException;
import com.vanrental.backend.exception.InvalidRequestException;
import com.vanrental.backend.exception.ResourceNotFoundException;
import com.vanrental.backend.model.Booking;
import com.vanrental.backend.model.BookingStatus;
import com.vanrental.backend.model.Customer;
import com.vanrental.backend.model.Vehicle;
import com.vanrental.backend.model.VehicleStatus;
import com.vanrental.backend.repository.BookingRepository;
import com.vanrental.backend.repository.BookingSpecifications;
import com.vanrental.backend.repository.CustomerRepository;
import com.vanrental.backend.repository.VehicleRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public BookingService(BookingRepository bookingRepository,
                          VehicleRepository vehicleRepository,
                          CustomerRepository customerRepository) {
        this.bookingRepository = bookingRepository;
        this.vehicleRepository = vehicleRepository;
        this.customerRepository = customerRepository;
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

    @Transactional
    public BookingResponse create(BookingRequest request) {
        validateDates(request.startDate(), request.endDate());
        Vehicle vehicle = lockVehicle(request.vehicleId());
        Customer customer = getCustomer(request.customerId());
        ensureBookable(vehicle, request.startDate(), request.endDate(), null);

        Booking booking = new Booking();
        booking.setVehicle(vehicle);
        booking.setCustomer(customer);
        booking.setStartDate(request.startDate());
        booking.setEndDate(request.endDate());
        booking.setPurpose(request.purpose());
        booking.setStatus(BookingStatus.PENDING);
        booking.setTotalPrice(calculatePrice(vehicle.getRatePerDay(), request.startDate(), request.endDate()));
        return BookingResponse.from(bookingRepository.save(booking));
    }

    @Transactional
    public BookingResponse update(Long id, BookingRequest request) {
        Booking booking = getBooking(id);
        if (booking.getStatus() == BookingStatus.CANCELLED || booking.getStatus() == BookingStatus.COMPLETED) {
            throw new InvalidRequestException("A " + booking.getStatus() + " booking can no longer be changed");
        }
        validateDates(request.startDate(), request.endDate());
        Vehicle vehicle = lockVehicle(request.vehicleId());
        Customer customer = getCustomer(request.customerId());
        // exclude this booking itself, otherwise it would "overlap" with its own old dates
        ensureBookable(vehicle, request.startDate(), request.endDate(), booking.getId());

        booking.setVehicle(vehicle);
        booking.setCustomer(customer);
        booking.setStartDate(request.startDate());
        booking.setEndDate(request.endDate());
        booking.setPurpose(request.purpose());
        booking.setTotalPrice(calculatePrice(vehicle.getRatePerDay(), request.startDate(), request.endDate()));
        return BookingResponse.from(booking); // managed entity: changes are saved on commit
    }

    @Transactional
    public BookingResponse changeStatus(Long id, BookingStatus newStatus) {
        Booking booking = getBooking(id);
        if (!booking.getStatus().canChangeTo(newStatus)) {
            throw new InvalidRequestException(
                    "Cannot change booking status from " + booking.getStatus() + " to " + newStatus);
        }
        booking.setStatus(newStatus);
        return BookingResponse.from(booking);
    }

    @Transactional
    public void delete(Long id) {
        bookingRepository.delete(getBooking(id));
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
