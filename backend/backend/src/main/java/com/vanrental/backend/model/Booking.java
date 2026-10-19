package com.vanrental.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "bookings", indexes = {
        // speeds up the availability check, which always filters by vehicle + dates
        @Index(name = "idx_bookings_vehicle_dates", columnList = "vehicle_id, start_date, end_date")
})
@Getter
@Setter
@NoArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Public code shown to the customer (the id stays internal). See BookingReference.
    @Column(unique = true, length = 20)
    private String reference;

    // Many bookings → one vehicle. Only Booking holds the link (unidirectional),
    // so Vehicle stays simple and JSON never loops Vehicle → bookings → vehicle …
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    // Both dates are inclusive: start 20 Oct, end 22 Oct = 3 rental days
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    private String purpose;

    // Optional pickup location: an address the customer types, and/or a pin on the map
    @Column(name = "pickup_address")
    private String pickupAddress;

    @Column(name = "pickup_latitude")
    private Double pickupLatitude;

    @Column(name = "pickup_longitude")
    private Double pickupLongitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    // ₱500 paid up front (part of totalPrice). Null for bookings made before down payments existed.
    @Column(name = "down_payment", precision = 10, scale = 2)
    private BigDecimal downPayment;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", length = 20)
    private PaymentStatus paymentStatus;

    // Snapshot of rate × days at booking time, so later rate changes don't alter old bookings
    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        if (reference == null) {
            reference = BookingReference.generate();
        }
    }
}
