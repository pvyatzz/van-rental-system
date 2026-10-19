package com.vanrental.backend.service;

import com.vanrental.backend.exception.ResourceNotFoundException;
import com.vanrental.backend.model.Vehicle;
import com.vanrental.backend.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class VehicleService {

    private final VehicleRepository vehicleRepository;

    public VehicleService(VehicleRepository vehicleRepository) {
        this.vehicleRepository = vehicleRepository;
    }

    public List<Vehicle> findAll() {
        return vehicleRepository.findAll();
    }

    public Vehicle findById(Long id) {
        return vehicleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle", id));
    }

    public Vehicle create(Vehicle vehicle) {
        vehicle.setId(null); // let the database assign the id
        return vehicleRepository.save(vehicle);
    }

    @Transactional
    public Vehicle update(Long id, Vehicle changes) {
        Vehicle existing = findById(id);
        existing.setModel(changes.getModel());
        existing.setCapacity(changes.getCapacity());
        existing.setRatePerDay(changes.getRatePerDay());
        existing.setStatus(changes.getStatus());
        existing.setPhotoUrl(changes.getPhotoUrl());
        return vehicleRepository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        if (!vehicleRepository.existsById(id)) {
            throw new ResourceNotFoundException("Vehicle", id);
        }
        // flush sends the DELETE now, so a foreign-key error (vehicle has bookings) surfaces here → 409
        vehicleRepository.deleteById(id);
        vehicleRepository.flush();
    }
}
