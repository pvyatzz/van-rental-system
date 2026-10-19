package com.vanrental.backend.repository;

import com.vanrental.backend.model.PaymentProof;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentProofRepository extends JpaRepository<PaymentProof, Long> {

    Optional<PaymentProof> findByBookingId(Long bookingId);
}
