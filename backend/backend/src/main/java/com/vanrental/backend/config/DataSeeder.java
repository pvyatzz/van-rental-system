package com.vanrental.backend.config;

import com.vanrental.backend.model.Vehicle;
import com.vanrental.backend.model.VehicleStatus;
import com.vanrental.backend.repository.VehicleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Inserts the starting fleet on first run (only when the vehicles table is empty).
 * Rates are placeholders — edit them via PUT /api/vehicles/{id}.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final VehicleRepository vehicleRepository;

    public DataSeeder(VehicleRepository vehicleRepository) {
        this.vehicleRepository = vehicleRepository;
    }

    @Override
    public void run(String... args) {
        if (vehicleRepository.count() > 0) {
            return;
        }
        vehicleRepository.saveAll(List.of(
                new Vehicle(null, "Mitsubishi L300 FB", 16, new BigDecimal("3500.00"),
                        VehicleStatus.AVAILABLE, "/images/l300-fb.png"),
                new Vehicle(null, "Mitsubishi L300 FB", 16, new BigDecimal("3500.00"),
                        VehicleStatus.AVAILABLE, "/images/l300-fb.png"),
                new Vehicle(null, "Hyundai H100 (Cargo)", 3, new BigDecimal("3000.00"),
                        VehicleStatus.AVAILABLE, "/images/h100.png")
        ));
    }
}
