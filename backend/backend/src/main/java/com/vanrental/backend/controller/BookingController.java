package com.vanrental.backend.controller;

import com.vanrental.backend.dto.AvailabilityResponse;
import com.vanrental.backend.dto.BookingRequest;
import com.vanrental.backend.dto.BookingResponse;
import com.vanrental.backend.dto.BookingStatusUpdate;
import com.vanrental.backend.dto.CustomerBookingRequest;
import com.vanrental.backend.model.BookingStatus;
import com.vanrental.backend.model.PaymentProof;
import com.vanrental.backend.model.PaymentStatus;
import com.vanrental.backend.security.GoogleIdentity;
import com.vanrental.backend.service.BookingService;
import com.vanrental.backend.service.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;
    private final PaymentService paymentService;

    public BookingController(BookingService bookingService, PaymentService paymentService) {
        this.bookingService = bookingService;
        this.paymentService = paymentService;
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

    /** Public: the customer's confirmation page, e.g. GET /api/bookings/ref/VR-7K3P9QXA */
    @GetMapping("/ref/{reference}")
    public BookingResponse getByReference(@PathVariable String reference) {
        return bookingService.findByReference(reference);
    }

    /** e.g. GET /api/bookings/availability?vehicleId=1&startDate=2026-11-01&endDate=2026-11-03 */
    @GetMapping("/availability")
    public AvailabilityResponse checkAvailability(
            @RequestParam Long vehicleId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return bookingService.checkAvailability(vehicleId, startDate, endDate);
    }

    /**
     * A signed-in customer books (SecurityConfig: role CUSTOMER).
     * {@code @AuthenticationPrincipal Jwt} is our verified login token; its subject is the Google user id.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BookingResponse> create(@Valid @RequestPart("booking") CustomerBookingRequest request,
                                                  @RequestPart("paymentProof") MultipartFile paymentProof,
                                                  @AuthenticationPrincipal Jwt jwt) {
        // multipart/form-data: one part is the booking as JSON, the other the payment screenshot
        BookingResponse created = bookingService.createForCustomer(request, identityOf(jwt), paymentProof);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/bookings/ref/{reference}")
                .buildAndExpand(created.reference())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    /** The signed-in customer's own bookings */
    @GetMapping("/mine")
    public List<BookingResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        return bookingService.findMine(jwt.getSubject());
    }

    /** Public: how much the down payment is, so the booking form shows the same amount the server charges */
    @GetMapping("/payment-info")
    public PaymentInfo paymentInfo() {
        return new PaymentInfo(bookingService.downPayment());
    }

    public record PaymentInfo(BigDecimal downPayment) {
    }

    /** Customer: upload a new proof (e.g. after the admin rejected the first one) */
    @PostMapping(value = "/ref/{reference}/payment-proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BookingResponse reuploadProof(@PathVariable String reference,
                                         @RequestPart("paymentProof") MultipartFile paymentProof,
                                         @AuthenticationPrincipal Jwt jwt) {
        return paymentService.reupload(reference, jwt.getSubject(), paymentProof);
    }

    /** Admin: the uploaded proof image itself */
    @GetMapping("/{id}/payment-proof")
    public ResponseEntity<byte[]> paymentProof(@PathVariable Long id) {
        PaymentProof proof = paymentService.getProof(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(proof.getContentType()))
                .cacheControl(CacheControl.noStore()) // private customer data: don't keep copies in caches
                .body(proof.getData());
    }

    /** Admin: VERIFIED (money arrived) or REJECTED. PATCH /api/bookings/5/payment {"status":"VERIFIED"} */
    @PatchMapping("/{id}/payment")
    public BookingResponse reviewPayment(@PathVariable Long id, @Valid @RequestBody PaymentReview body) {
        return paymentService.review(id, body.status());
    }

    public record PaymentReview(@NotNull PaymentStatus status) {
    }

    private static GoogleIdentity identityOf(Jwt jwt) {
        return new GoogleIdentity(jwt.getSubject(), jwt.getClaimAsString("email"), jwt.getClaimAsString("name"));
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
