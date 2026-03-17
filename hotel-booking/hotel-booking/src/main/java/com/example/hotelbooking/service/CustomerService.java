package com.example.hotelbooking.service;

import com.example.hotelbooking.entity.Customer;
import com.example.hotelbooking.exception.ResourceNotFoundException;
import com.example.hotelbooking.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public List<Customer> getAllCustomers() {
        return customerRepository.findAll();
    }

    public Customer getCustomerById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + id));
    }

    public Customer getCustomerByEmail(String email) {
        return customerRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with email: " + email));
    }

    @Transactional
    public Customer updateCustomer(Long id, String fullName, String email, String phoneNumber) {
        Customer customer = getCustomerById(id);

        if (fullName != null) {
            customer.setFullName(fullName);
        }
        if (email != null) {
            customer.setEmail(email);
        }
        if (phoneNumber != null) {
            customer.setPhoneNumber(phoneNumber);
        }

        return customerRepository.save(customer);
    }

    @Transactional
    public Customer updateCustomerStatus(Long id, String status) {
        Customer customer = getCustomerById(id);
        customer.setStatus(status);
        return customerRepository.save(customer);
    }

    @Transactional
    public void deleteCustomer(Long id) {
        Customer customer = getCustomerById(id);
        customerRepository.delete(customer);
    }
}

