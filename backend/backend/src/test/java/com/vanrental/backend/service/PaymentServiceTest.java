package com.vanrental.backend.service;

import com.vanrental.backend.exception.InvalidRequestException;
import com.vanrental.backend.exception.ResourceNotFoundException;
import com.vanrental.backend.model.Booking;
import com.vanrental.backend.model.BookingStatus;
import com.vanrental.backend.model.Customer;
import com.vanrental.backend.model.PaymentProof;
import com.vanrental.backend.model.PaymentStatus;
import com.vanrental.backend.model.Vehicle;
import com.vanrental.backend.model.VehicleStatus;
import com.vanrental.backend.repository.BookingRepository;
import com.vanrental.backend.repository.PaymentProofRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentServiceTest {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13};
    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16};
    static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8'};

    PaymentProofRepository proofRepository = mock(PaymentProofRepository.class);
    BookingRepository bookingRepository = mock(BookingRepository.class);
    PaymentService paymentService = new PaymentService(proofRepository, bookingRepository);

    Booking booking;

    @BeforeEach
    void setUp() {
        booking = new Booking();
        booking.setId(5L);
        booking.setReference("VR-ABCD2345");
        booking.setVehicle(new Vehicle(1L, "L300", 16, new BigDecimal("3500"), VehicleStatus.AVAILABLE, null));
        booking.setCustomer(new Customer(10L, "Maria", "0917", "maria@gmail.com", "google-maria"));
        booking.setStartDate(LocalDate.now().plusDays(5));
        booking.setEndDate(LocalDate.now().plusDays(6));
        booking.setTotalPrice(new BigDecimal("7000"));
        booking.setDownPayment(new BigDecimal("500"));
        booking.setStatus(BookingStatus.PENDING);
        booking.setPaymentStatus(PaymentStatus.SUBMITTED);
    }

    // ---- validate: is it really an image? ----

    @Test
    void acceptsJpegPngAndWebpByTheirBytes() {
        assertThat(paymentService.validate(new MockMultipartFile("f", "a.jpg", "image/jpeg", JPEG)).type()).isEqualTo(ImageType.JPEG);
        assertThat(paymentService.validate(new MockMultipartFile("f", "a.png", "image/png", PNG)).type()).isEqualTo(ImageType.PNG);
        assertThat(paymentService.validate(new MockMultipartFile("f", "a.webp", "image/webp", WEBP)).type()).isEqualTo(ImageType.WEBP);
    }

    @Test
    void rejectsAFileThatOnlyPretendsToBeAnImage() {
        // Named .png and sent as image/png, but the bytes are a Windows program ("MZ...")
        MockMultipartFile fake = new MockMultipartFile("f", "proof.png", "image/png", new byte[]{'M', 'Z', 0, 0, 0, 0, 0, 0});
        assertThatThrownBy(() -> paymentService.validate(fake))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("JPG, PNG or WebP");
    }

    @Test
    void rejectsEmptyMissingAndTooLargeFiles() {
        assertThatThrownBy(() -> paymentService.validate(null)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> paymentService.validate(new MockMultipartFile("f", "a.png", "image/png", new byte[0])))
                .isInstanceOf(InvalidRequestException.class);
        byte[] big = new byte[(int) PaymentService.MAX_PROOF_BYTES + 1];
        System.arraycopy(PNG, 0, big, 0, PNG.length);
        assertThatThrownBy(() -> paymentService.validate(new MockMultipartFile("f", "a.png", "image/png", big)))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("5 MB");
    }

    @Test
    void keepsOnlyTheFileNameNotThePath() {
        assertThat(paymentService.validate(new MockMultipartFile("f", "C:\\fakepath\\gcash.png", "image/png", PNG)).fileName())
                .isEqualTo("gcash.png");
    }

    // ---- attach + review ----

    @Test
    void attachingAProofMarksThePaymentSubmitted() {
        booking.setPaymentStatus(null);
        when(proofRepository.findByBookingId(5L)).thenReturn(Optional.empty());

        paymentService.attachProof(booking, paymentService.validate(new MockMultipartFile("f", "gcash.png", "image/png", PNG)));

        ArgumentCaptor<PaymentProof> saved = ArgumentCaptor.forClass(PaymentProof.class);
        verify(proofRepository).save(saved.capture());
        assertThat(saved.getValue().getContentType()).isEqualTo("image/png"); // from the bytes
        assertThat(saved.getValue().getData()).isEqualTo(PNG);
        assertThat(booking.getPaymentStatus()).isEqualTo(PaymentStatus.SUBMITTED);
    }

    @Test
    void adminCanVerifyOrRejectASubmittedPayment() {
        when(bookingRepository.findWithDetailsById(5L)).thenReturn(Optional.of(booking));
        assertThat(paymentService.review(5L, PaymentStatus.VERIFIED).paymentStatus()).isEqualTo(PaymentStatus.VERIFIED);
    }

    @Test
    void reviewNeedsAPendingBookingWithAPayment() {
        when(bookingRepository.findWithDetailsById(5L)).thenReturn(Optional.of(booking));
        assertThatThrownBy(() -> paymentService.review(5L, PaymentStatus.SUBMITTED)).isInstanceOf(InvalidRequestException.class);

        booking.setStatus(BookingStatus.CANCELLED);
        assertThatThrownBy(() -> paymentService.review(5L, PaymentStatus.VERIFIED)).isInstanceOf(InvalidRequestException.class);

        booking.setStatus(BookingStatus.PENDING);
        booking.setPaymentStatus(null);
        assertThatThrownBy(() -> paymentService.review(5L, PaymentStatus.VERIFIED)).isInstanceOf(InvalidRequestException.class);
    }

    // ---- customer re-upload ----

    @Test
    void ownerCanReuploadAfterARejection() {
        booking.setPaymentStatus(PaymentStatus.REJECTED);
        when(bookingRepository.findWithDetailsByReference("VR-ABCD2345")).thenReturn(Optional.of(booking));
        when(proofRepository.findByBookingId(5L)).thenReturn(Optional.of(new PaymentProof()));

        var result = paymentService.reupload("vr-abcd2345", "google-maria",
                new MockMultipartFile("paymentProof", "new.jpg", "image/jpeg", JPEG));

        assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.SUBMITTED);
        verify(proofRepository).save(any());
    }

    @Test
    void someoneElseCannotReuploadOnMyBooking() {
        booking.setPaymentStatus(PaymentStatus.REJECTED);
        when(bookingRepository.findWithDetailsByReference("VR-ABCD2345")).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> paymentService.reupload("VR-ABCD2345", "google-someone-else",
                new MockMultipartFile("paymentProof", "x.png", "image/png", PNG)))
                .isInstanceOf(ResourceNotFoundException.class); // "not found", so references can't be probed
        verify(proofRepository, never()).save(any());
    }

    @Test
    void cannotReplaceAVerifiedPayment() {
        booking.setPaymentStatus(PaymentStatus.VERIFIED);
        when(bookingRepository.findWithDetailsByReference("VR-ABCD2345")).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> paymentService.reupload("VR-ABCD2345", "google-maria",
                new MockMultipartFile("paymentProof", "x.png", "image/png", PNG)))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("already verified");
    }
}
