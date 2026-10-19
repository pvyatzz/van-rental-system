package com.vanrental.backend.service;

import com.vanrental.backend.dto.BookingResponse;
import com.vanrental.backend.exception.InvalidRequestException;
import com.vanrental.backend.exception.ResourceNotFoundException;
import com.vanrental.backend.model.Booking;
import com.vanrental.backend.model.BookingStatus;
import com.vanrental.backend.model.PaymentProof;
import com.vanrental.backend.model.PaymentStatus;
import com.vanrental.backend.notification.PaymentRejectedEvent;
import com.vanrental.backend.repository.BookingRepository;
import com.vanrental.backend.repository.PaymentProofRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

/** The ₱500 down payment: storing the customer's proof image and the admin's verify/reject decision. */
@Service
public class PaymentService {

    public static final long MAX_PROOF_BYTES = 5L * 1024 * 1024; // 5 MB

    private final PaymentProofRepository proofRepository;
    private final BookingRepository bookingRepository;
    private final ApplicationEventPublisher eventPublisher;

    public PaymentService(PaymentProofRepository proofRepository, BookingRepository bookingRepository,
                          ApplicationEventPublisher eventPublisher) {
        this.proofRepository = proofRepository;
        this.bookingRepository = bookingRepository;
        this.eventPublisher = eventPublisher;
    }

    /** An uploaded file that passed the checks */
    public record ValidatedImage(ImageType type, byte[] data, String fileName) {
    }

    /** Is it really an image (checked by its bytes) and not too big? */
    public ValidatedImage validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("Please attach a screenshot or photo of your down payment");
        }
        if (file.getSize() > MAX_PROOF_BYTES) {
            throw new InvalidRequestException("The image is too large (max 5 MB)");
        }
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        ImageType type = ImageType.detect(data)
                .orElseThrow(() -> new InvalidRequestException("The proof of payment must be a JPG, PNG or WebP image"));
        return new ValidatedImage(type, data, cleanFileName(file.getOriginalFilename()));
    }

    /** Saves (or replaces) the booking's proof and marks the payment as waiting for review */
    @Transactional
    public void attachProof(Booking booking, ValidatedImage image) {
        PaymentProof proof = proofRepository.findByBookingId(booking.getId()).orElseGet(PaymentProof::new);
        proof.setBooking(booking);
        proof.setContentType(image.type().contentType);
        proof.setFileName(image.fileName());
        proof.setSizeBytes(image.data().length);
        proof.setData(image.data());
        proof.setUploadedAt(Instant.now());
        proofRepository.save(proof);
        booking.setPaymentStatus(PaymentStatus.SUBMITTED);
    }

    @Transactional(readOnly = true)
    public PaymentProof getProof(Long bookingId) {
        return proofRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("No proof of payment for booking " + bookingId));
    }

    /** Admin: the down payment is (not) in the account */
    @Transactional
    public BookingResponse review(Long bookingId, PaymentStatus decision) {
        if (decision != PaymentStatus.VERIFIED && decision != PaymentStatus.REJECTED) {
            throw new InvalidRequestException("A payment can only be VERIFIED or REJECTED");
        }
        Booking booking = bookingRepository.findWithDetailsById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
        if (booking.getPaymentStatus() == null) {
            throw new InvalidRequestException("This booking has no down payment to review");
        }
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new InvalidRequestException("Only the payment of a PENDING booking can be reviewed");
        }
        booking.setPaymentStatus(decision);
        if (decision == PaymentStatus.REJECTED) {
            eventPublisher.publishEvent(new PaymentRejectedEvent(bookingId)); // "please upload a new proof" email
        }
        return BookingResponse.from(booking);
    }

    /**
     * Customer: upload a new proof after a rejection (or to replace one not yet reviewed).
     * Someone else's booking answers "not found", so references can't be probed.
     */
    @Transactional
    public BookingResponse reupload(String reference, String googleId, MultipartFile file) {
        Booking booking = bookingRepository.findWithDetailsByReference(reference.trim().toUpperCase())
                .filter(b -> Objects.equals(b.getCustomer().getGoogleId(), googleId))
                .orElseThrow(() -> new ResourceNotFoundException("Booking with reference " + reference + " not found"));
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new InvalidRequestException("This booking is " + booking.getStatus() + "; its payment can't be changed");
        }
        if (booking.getPaymentStatus() == PaymentStatus.VERIFIED) {
            throw new InvalidRequestException("Your down payment is already verified");
        }
        attachProof(booking, validate(file));
        return BookingResponse.from(booking);
    }

    @Transactional
    public void deleteProofOf(Long bookingId) {
        proofRepository.findByBookingId(bookingId).ifPresent(proofRepository::delete);
        proofRepository.flush();
    }

    /** Only the name part ("C:\\fakepath\\gcash.png" → "gcash.png"), at most 255 characters */
    private static String cleanFileName(String original) {
        if (original == null || original.isBlank()) {
            return null;
        }
        String name = Path.of(original.replace('\\', '/')).getFileName().toString();
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }
}
