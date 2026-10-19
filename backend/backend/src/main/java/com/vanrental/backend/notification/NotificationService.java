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

import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;

/**
 * Emails the customer when their booking is confirmed.
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

    public NotificationService(BookingRepository bookingRepository,
                               Optional<JavaMailSender> mailSender,
                               @Value("${app.mail.from}") String from,
                               @Value("${app.business.name}") String businessName) {
        this.bookingRepository = bookingRepository;
        this.mailSender = mailSender;
        this.from = from;
        this.businessName = businessName;
    }

    /**
     * AFTER_COMMIT: only runs once the status change is really saved. If the transaction rolls back,
     * no email goes out for a confirmation that never happened.
     * Async: runs on a background thread, so a slow mail server never slows down the admin's click.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        bookingRepository.findWithDetailsById(event.bookingId()).ifPresent(this::sendConfirmation);
    }

    void sendConfirmation(Booking booking) {
        String to = booking.getCustomer().getEmail();
        if (to == null || to.isBlank()) {
            log.info("Booking {} confirmed, but the customer has no email address. Nothing sent.", booking.getReference());
            return;
        }
        SimpleMailMessage message = buildConfirmation(booking);

        if (mailSender.isEmpty()) {
            log.info("Mail is not configured, so this email was NOT sent:\nTo: {}\nSubject: {}\n\n{}",
                    to, message.getSubject(), message.getText());
            return;
        }
        try {
            mailSender.get().send(message);
            log.info("Confirmation email for booking {} sent to {}", booking.getReference(), to);
        } catch (MailException e) {
            // The booking is still confirmed; only the email failed. Log it so the admin can follow up.
            log.error("Could not send confirmation email for booking {} to {}", booking.getReference(), to, e);
        }
    }

    SimpleMailMessage buildConfirmation(Booking booking) {
        long days = ChronoUnit.DAYS.between(booking.getStartDate(), booking.getEndDate()) + 1;
        StringBuilder body = new StringBuilder()
                .append("Hi ").append(booking.getCustomer().getFullName()).append(",\n\n")
                .append("Good news: your booking is confirmed.\n\n")
                .append("Reference: ").append(booking.getReference()).append('\n')
                .append("Van:       ").append(booking.getVehicle().getModel()).append('\n')
                .append("Pick-up:   ").append(DATE.format(booking.getStartDate())).append('\n')
                .append("Return:    ").append(DATE.format(booking.getEndDate()))
                .append(" (").append(days).append(days == 1 ? " day" : " days").append(")\n")
                .append("Total:     PHP ").append(String.format(Locale.ENGLISH, "%,.2f", booking.getTotalPrice())).append('\n');
        body.append("\nIf you need to change anything, just reply to this email.\n\n")
                .append("Thank you,\n").append(businessName).append('\n');

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(booking.getCustomer().getEmail());
        message.setSubject("Booking confirmed: " + booking.getReference());
        message.setText(body.toString());
        return message;
    }
}
