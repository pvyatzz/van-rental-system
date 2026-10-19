package com.vanrental.backend.service;

import com.vanrental.backend.exception.ResourceNotFoundException;
import com.vanrental.backend.model.Customer;
import com.vanrental.backend.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public List<Customer> findAll() {
        return customerRepository.findAll();
    }

    public Customer findById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", id));
    }

    public Customer create(Customer customer) {
        customer.setId(null);
        return customerRepository.save(customer);
    }

    @Transactional
    public Customer update(Long id, Customer changes) {
        Customer existing = findById(id);
        existing.setFullName(changes.getFullName());
        existing.setContactNumber(changes.getContactNumber());
        existing.setEmail(changes.getEmail());
        return customerRepository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        if (!customerRepository.existsById(id)) {
            throw new ResourceNotFoundException("Customer", id);
        }
        // flush sends the DELETE now, so a foreign-key error (customer has bookings) surfaces here → 409
        customerRepository.deleteById(id);
        customerRepository.flush();
    }
}
