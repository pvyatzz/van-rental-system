package com.vanrental.backend.repository;

import com.vanrental.backend.model.Booking;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long>, JpaSpecificationExecutor<Booking> {

    /**
     * Bookings for a vehicle whose dates overlap [startDate, endDate] (both inclusive),
     * ignoring cancelled ones. Two ranges overlap when each one starts on or before the other ends.
     */
    @Query("""
            select b from Booking b
            where b.vehicle.id = :vehicleId
              and b.status <> com.vanrental.backend.model.BookingStatus.CANCELLED
              and b.startDate <= :endDate
              and b.endDate >= :startDate
            """)
    List<Booking> findOverlapping(@Param("vehicleId") Long vehicleId,
                                  @Param("startDate") LocalDate startDate,
                                  @Param("endDate") LocalDate endDate);

    // Search with optional filters (see BookingSpecifications). @EntityGraph loads vehicle + customer
    // in the same query, avoiding one extra query per row.
    @Override
    @EntityGraph(attributePaths = {"vehicle", "customer"})
    List<Booking> findAll(Specification<Booking> spec, Sort sort);

    @EntityGraph(attributePaths = {"vehicle", "customer"})
    Optional<Booking> findWithDetailsById(Long id);
}
