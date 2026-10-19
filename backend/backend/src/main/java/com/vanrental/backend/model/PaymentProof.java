package com.vanrental.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * The screenshot/photo a customer uploads as proof of the down payment.
 * Kept in its own table (not a column on Booking) so loading a list of bookings never loads the images.
 */
@Entity
@Table(name = "payment_proofs")
@Getter
@Setter
@NoArgsConstructor
public class PaymentProof {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // One proof per booking (a re-upload replaces it). Only this side knows about the link.
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    /** Detected from the file's bytes (image/jpeg, image/png, image/webp), not from what the browser claimed */
    @Column(name = "content_type", nullable = false, length = 30)
    private String contentType;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    // byte[] is stored as PostgreSQL "bytea" (binary data)
    @Column(nullable = false)
    private byte[] data;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;
}
