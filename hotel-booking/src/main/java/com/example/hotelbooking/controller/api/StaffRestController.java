package com.example.hotelbooking.controller.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Staff API endpoints
 * Accessible by ADMIN, MANAGER, and STAFF roles
 */
@RestController
@RequestMapping("/api/staff")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
public class StaffRestController {

    @GetMapping("/dashboard")
    public ResponseEntity<?> getStaffDashboard(Authentication authentication) {
        return ResponseEntity.ok(Map.of(
                "message", "Welcome to Staff Dashboard",
                "user", authentication.getName(),
                "permissions", "STAFF_ACCESS"
        ));
    }

    @GetMapping("/check-in/{bookingId}")
    public ResponseEntity<?> checkInGuest(@PathVariable Long bookingId) {
        return ResponseEntity.ok(Map.of(
                "message", "Guest check-in endpoint",
                "bookingId", bookingId,
                "description", "This endpoint is accessible by ADMIN, MANAGER, and STAFF"
        ));
    }

    @GetMapping("/check-out/{bookingId}")
    public ResponseEntity<?> checkOutGuest(@PathVariable Long bookingId) {
        return ResponseEntity.ok(Map.of(
                "message", "Guest check-out endpoint",
                "bookingId", bookingId,
                "description", "This endpoint is accessible by ADMIN, MANAGER, and STAFF"
        ));
    }

    @GetMapping("/today-bookings")
    public ResponseEntity<?> getTodayBookings() {
        return ResponseEntity.ok(Map.of(
                "message", "Today's bookings",
                "description", "This endpoint is accessible by ADMIN, MANAGER, and STAFF"
        ));
    }

    @GetMapping("/room-status")
    public ResponseEntity<?> getRoomStatus() {
        return ResponseEntity.ok(Map.of(
                "message", "Room status overview",
                "description", "This endpoint is accessible by ADMIN, MANAGER, and STAFF"
        ));
    }
}

