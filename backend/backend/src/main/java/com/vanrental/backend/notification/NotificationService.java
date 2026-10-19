package com.vanrental.backend.notification;

import com.vanrental.backend.model.Booking;
import com.vanrental.backend.repository.BookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;

/**
 * Emails the customer at three moments:
 *  1. booking request received (they just booked and uploaded the down payment)
 *  2. booking confirmed (the admin verified the payment and accepted the booking)
 *  3. down payment not confirmed (the admin rejected the proof: please upload a new one)
 * If no mail server is configured (no SPRING_MAIL_HOST), Spring creates no JavaMailSender,
 * so the email is written to the log instead — handy for development.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH);

    private final BookingRepository bookingRepository;
    private final Optional<JavaMailSender> mailSender; // empty when mail isn't configured
    private final String from;
    private final String businessName;
    private final String frontendUrl;

    public NotificationService(BookingRepository bookingRepository,
                               Optional<JavaMailSender> mailSender,
                               @Value("${app.mail.from}") String from,
                               @Value("${app.business.name}") String businessName,
                               @Value("${app.frontend-url}") String frontendUrl) {
        this.bookingRepository = bookingRepository;
        this.mailSender = mailSender;
        this.from = from;
        this.businessName = businessName;
        this.frontendUrl = frontendUrl;
    }

    // ---- Listeners ----
    // AFTER_COMMIT: only runs once the change is really saved (no email for something that rolled back).
    // Async: runs on a background thread, so a slow mail server never slows down the click.

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingCreated(BookingCreatedEvent event) {
        bookingRepository.findWithDetailsById(event.bookingId()).ifPresent(b -> send(b, buildReceived(b)));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        bookingRepository.findWithDetailsById(event.bookingId()).ifPresent(b -> send(b, buildConfirmation(b)));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentRejected(PaymentRejectedEvent event) {
        bookingRepository.findWithDetailsById(event.bookingId()).ifPresent(b -> send(b, buildPaymentRejected(b)));
    }

    // ---- Sending ----

    void send(Booking booking, SimpleMailMessage message) {
        String to = booking.getCustomer().getEmail();
        if (to == null || to.isBlank()) {
            log.info("No email address for booking {}. Nothing sent: {}", booking.getReference(), message.getSubject());
            return;
        }
        if (mailSender.isEmpty()) {
            log.info("Mail is not configured, so this email was NOT sent:\nTo: {}\nSubject: {}\n\n{}",
                    to, message.getSubject(), message.getText());
            return;
        }
        try {
            mailSender.get().send(message);
            log.info("Email \"{}\" sent to {}", message.getSubject(), to);
        } catch (MailException e) {
            // The booking change still stands; only the email failed. Log it so the admin can follow up.
            log.error("Could not send \"{}\" to {}", message.getSubject(), to, e);
        }
    }

    // ---- The three emails ----

    SimpleMailMessage buildReceived(Booking booking) {
        String body = greeting(booking)
                + "Thank you! We received your booking request and your down payment screenshot.\n"
                + "We'll check the payment and email you again once your booking is confirmed.\n\n"
                + details(booking)
                + "\nYou can see your booking anytime here:\n" + bookingLink(booking) + "\n"
                + signature();
        return message(booking, "Booking request received: " + booking.getReference(), body);
    }

    SimpleMailMessage buildConfirmation(Booking booking) {
        String body = greeting(booking)
                + "Good news: your booking is confirmed.\n\n"
                + details(booking)
                + "\nIf you need to change anything, just reply to this email.\n"
                + signature();
        return message(booking, "Booking confirmed: " + booking.getReference(), body);
    }

    SimpleMailMessage buildPaymentRejected(Booking booking) {
        String body = greeting(booking)
                + "We couldn't confirm your down payment of " + peso(booking.getDownPayment())
                + " from the screenshot you sent.\n"
                + "Please check that the transfer went through, then upload a new screenshot here:\n"
                + bookingLink(booking) + "\n\n"
                + "Your dates stay reserved while we sort this out. Reply to this email if you have questions.\n\n"
                + details(booking)
                + signature();
        return message(booking, "Please check your down payment: " + booking.getReference(), body);
    }

    // ---- Shared pieces ----

    private String greeting(Booking booking) {
        return "Hi " + booking.getCustomer().getFullName() + ",\n\n";
    }

    private String details(Booking booking) {
        long days = ChronoUnit.DAYS.between(booking.getStartDate(), booking.getEndDate()) + 1;
        StringBuilder text = new StringBuilder()
                .append("Reference: ").append(booking.getReference()).append('\n')
                .append("Van:       ").append(booking.getVehicle().getModel()).append('\n')
                .append("Pick-up:   ").append(DATE.format(booking.getStartDate())).append('\n')
                .append("Return:    ").append(DATE.format(booking.getEndDate()))
                .append(" (").append(days).append(days == 1 ? " day" : " days").append(")\n")
                .append("Total:     ").append(peso(booking.getTotalPrice())).append('\n');
        if (booking.getDownPayment() != null) {
            String state = switch (booking.getPaymentStatus()) {
                case VERIFIED -> "received";
                case REJECTED -> "not confirmed";
                case SUBMITTED -> "being checked";
                case null -> "";
            };
            text.append("Down payment: ").append(peso(booking.getDownPayment())).append(" (").append(state).append(")\n")
                    .append("Balance at pickup: ")
                    .append(peso(booking.getTotalPrice().subtract(booking.getDownPayment()))).append('\n');
        }
        if (booking.getPickupAddress() != null) {
            text.append("Pickup:    ").append(booking.getPickupAddress()).append('\n');
        }
        if (booking.getPickupLatitude() != null && booking.getPickupLongitude() != null) {
            // A plain Google Maps link: works on any phone, no API key needed
            text.append("Map:       https://www.google.com/maps?q=")
                    .append(booking.getPickupLatitude()).append(',').append(booking.getPickupLongitude()).append('\n');
        }
        return text.toString();
    }

    private String bookingLink(Booking booking) {
        return frontendUrl + "/bookings/" + booking.getReference();
    }

    private String signature() {
        return "\nThank you,\n" + businessName + "\n";
    }

    private static String peso(BigDecimal amount) {
        return "PHP " + String.format(Locale.ENGLISH, "%,.2f", amount);
    }

    private SimpleMailMessage message(Booking booking, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(booking.getCustomer().getEmail());
        message.setSubject(subject);
        message.setText(body);
        return message;
    }
}
