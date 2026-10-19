package com.vanrental.backend.repository;

import com.vanrental.backend.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByGoogleId(String googleId);

    /** An older customer record (made before Google sign-in) with the same email, not yet linked */
    Optional<Customer> findFirstByEmailIgnoreCaseAndGoogleIdIsNull(String email);
}
