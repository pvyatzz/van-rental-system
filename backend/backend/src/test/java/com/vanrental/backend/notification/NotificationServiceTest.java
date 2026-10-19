package com.vanrental.backend.notification;

import com.vanrental.backend.model.Booking;
import com.vanrental.backend.model.BookingStatus;
import com.vanrental.backend.model.Customer;
import com.vanrental.backend.model.Vehicle;
import com.vanrental.backend.model.VehicleStatus;
import com.vanrental.backend.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class NotificationServiceTest {

    BookingRepository bookingRepository = mock(BookingRepository.class);
    JavaMailSender mailSender = mock(JavaMailSender.class);
    Booking booking;

    @BeforeEach
    void setUp() {
        Vehicle van = new Vehicle(1L, "Mitsubishi L300 FB", 16, new BigDecimal("3500.00"), VehicleStatus.AVAILABLE, null);
        Customer customer = new Customer(10L, "Maria Santos", "09171234567", "maria@example.com");
        booking = new Booking();
        booking.setId(5L);
        booking.setReference("VR-ABCD2345");
        booking.setVehicle(van);
        booking.setCustomer(customer);
        booking.setStartDate(LocalDate.of(2026, 11, 20));
        booking.setEndDate(LocalDate.of(2026, 11, 22));
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setTotalPrice(new BigDecimal("10500.00"));
    }

    private NotificationService service(Optional<JavaMailSender> sender) {
        return new NotificationService(bookingRepository, sender, "bookings@vanrental.test", "Van Rental");
    }

    @Test
    void sendsConfirmationWithTheBookingDetails() {
        service(Optional.of(mailSender)).sendConfirmation(booking);

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(sent.capture());
        SimpleMailMessage message = sent.getValue();
        assertThat(message.getTo()).containsExactly("maria@example.com");
        assertThat(message.getFrom()).isEqualTo("bookings@vanrental.test");
        assertThat(message.getSubject()).isEqualTo("Booking confirmed: VR-ABCD2345");
        assertThat(message.getText())
                .contains("Hi Maria Santos")
                .contains("VR-ABCD2345")
                .contains("Fri, 20 Nov 2026")
                .contains("Sun, 22 Nov 2026 (3 days)")
                .contains("PHP 10,500.00");
    }

    @Test
    void includesPickupAddressAndMapLink() {
        booking.setPickupAddress("SM Megamall, Ortigas");
        booking.setPickupLatitude(14.5849);
        booking.setPickupLongitude(121.0563);

        String text = service(Optional.of(mailSender)).buildConfirmation(booking).getText();

        assertThat(text).contains("Pickup:    SM Megamall, Ortigas")
                .contains("https://www.google.com/maps?q=14.5849,121.0563");
    }

    @Test
    void customerWithoutEmailGetsNothing() {
        booking.getCustomer().setEmail(null);
        service(Optional.of(mailSender)).sendConfirmation(booking);
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void withoutMailServerItOnlyLogs() {
        // No JavaMailSender bean (mail not configured): must not throw
        assertThatCode(() -> service(Optional.empty()).sendConfirmation(booking)).doesNotThrowAnyException();
    }

    @Test
    void aFailingMailServerDoesNotThrow() {
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));
        assertThatCode(() -> service(Optional.of(mailSender)).sendConfirmation(booking)).doesNotThrowAnyException();
    }
}
