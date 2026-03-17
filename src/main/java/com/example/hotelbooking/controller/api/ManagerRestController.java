package com.example.hotelbooking.controller.api;

import com.example.hotelbooking.dto.BookingRequest;
import com.example.hotelbooking.dto.BookingResponse;
import com.example.hotelbooking.entity.Booking;
import com.example.hotelbooking.entity.Customer;
import com.example.hotelbooking.enums.BookingStatus;
import com.example.hotelbooking.service.BookingService;
import com.example.hotelbooking.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Manager API endpoints
 * Accessible by ADMIN and MANAGER roles
 */
@RestController
@RequestMapping("/api/manager")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
public class ManagerRestController {

    private final BookingService bookingService;
    private final CustomerService customerService;

    public ManagerRestController(BookingService bookingService, CustomerService customerService) {
        this.bookingService = bookingService;
        this.customerService = customerService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<?> getManagerDashboard(Authentication authentication) {
        return ResponseEntity.ok(Map.of(
                "message", "Welcome to Manager Dashboard",
                "user", authentication.getName(),
                "permissions", "MANAGER_ACCESS"
        ));
    }

    // ==================== BOOKING MANAGEMENT ====================

    @GetMapping("/bookings")
    public ResponseEntity<List<Booking>> getAllBookings() {
        return ResponseEntity.ok(bookingService.getAllBookings());
    }

    @GetMapping("/bookings/{id}")
    public ResponseEntity<BookingResponse> getBookingById(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.getBookingById(id));
    }

    @PostMapping("/bookings")
    public ResponseEntity<BookingResponse> createBooking(@Valid @RequestBody BookingRequest request) {
        BookingResponse booking = bookingService.createBooking(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(booking);
    }

    @PutMapping("/bookings/{id}")
    public ResponseEntity<BookingResponse> updateBooking(@PathVariable Long id, @RequestBody BookingRequest request) {
        BookingResponse booking = bookingService.updateBooking(id, request);
        return ResponseEntity.ok(booking);
    }

    @PutMapping("/bookings/{id}/status")
    public ResponseEntity<BookingResponse> updateBookingStatus(@PathVariable Long id, @RequestBody Map<String, String> request) {
        String status = request.get("status");
        BookingResponse booking = bookingService.updateBookingStatus(id, BookingStatus.valueOf(status));
        return ResponseEntity.ok(booking);
    }

    @DeleteMapping("/bookings/{id}")
    public ResponseEntity<?> deleteBooking(@PathVariable Long id) {
        bookingService.deleteBooking(id);
        return ResponseEntity.ok(Map.of("message", "Booking deleted successfully", "bookingId", id));
    }

    // ==================== CUSTOMER MANAGEMENT ====================

    @GetMapping("/customers")
    public ResponseEntity<List<Customer>> getAllCustomers() {
        return ResponseEntity.ok(customerService.getAllCustomers());
    }

    @GetMapping("/customers/{id}")
    public ResponseEntity<Customer> getCustomerById(@PathVariable Long id) {
        return ResponseEntity.ok(customerService.getCustomerById(id));
    }

    @PutMapping("/customers/{id}")
    public ResponseEntity<Customer> updateCustomer(@PathVariable Long id, @RequestBody Map<String, String> request) {
        String fullName = request.get("fullName");
        String email = request.get("email");
        String phoneNumber = request.get("phoneNumber");
        Customer customer = customerService.updateCustomer(id, fullName, email, phoneNumber);
        return ResponseEntity.ok(customer);
    }

    @PutMapping("/customers/{id}/status")
    public ResponseEntity<Customer> updateCustomerStatus(@PathVariable Long id, @RequestBody Map<String, String> request) {
        String status = request.get("status");
        Customer customer = customerService.updateCustomerStatus(id, status);
        return ResponseEntity.ok(customer);
    }

    @GetMapping("/statistics")
    public ResponseEntity<?> getStatistics() {
        return ResponseEntity.ok(Map.of(
                "message", "Hotel statistics",
                "description", "This endpoint is accessible by ADMIN and MANAGER"
        ));
    }
}

