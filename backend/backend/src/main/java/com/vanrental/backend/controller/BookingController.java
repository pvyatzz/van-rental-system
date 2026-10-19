package com.vanrental.backend.controller;

import com.vanrental.backend.dto.AvailabilityResponse;
import com.vanrental.backend.dto.BookingRequest;
import com.vanrental.backend.dto.BookingResponse;
import com.vanrental.backend.dto.BookingStatusUpdate;
import com.vanrental.backend.model.BookingStatus;
import com.vanrental.backend.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /** e.g. GET /api/bookings?vehicleId=1&status=PENDING&from=2026-11-01&to=2026-11-30 (all optional) */
    @GetMapping
    public List<BookingResponse> search(
            @RequestParam(required = false) Long vehicleId,
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return bookingService.search(vehicleId, status, from, to);
    }

    @GetMapping("/{id}")
    public BookingResponse getById(@PathVariable Long id) {
        return bookingService.findById(id);
    }

    /** e.g. GET /api/bookings/availability?vehicleId=1&startDate=2026-11-01&endDate=2026-11-03 */
    @GetMapping("/availability")
    public AvailabilityResponse checkAvailability(
            @RequestParam Long vehicleId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return bookingService.checkAvailability(vehicleId, startDate, endDate);
    }

    @PostMapping
    public ResponseEntity<BookingResponse> create(@Valid @RequestBody BookingRequest request) {
        BookingResponse created = bookingService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public BookingResponse update(@PathVariable Long id, @Valid @RequestBody BookingRequest request) {
        return bookingService.update(id, request);
    }

    /** Confirm, cancel or complete a booking: PATCH /api/bookings/5/status  {"status":"CONFIRMED"} */
    @PatchMapping("/{id}/status")
    public BookingResponse changeStatus(@PathVariable Long id, @Valid @RequestBody BookingStatusUpdate body) {
        return bookingService.changeStatus(id, body.status());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        bookingService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
