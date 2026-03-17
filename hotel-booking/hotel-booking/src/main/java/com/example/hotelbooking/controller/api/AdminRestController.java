package com.example.hotelbooking.controller.api;

import com.example.hotelbooking.dto.BookingRequest;
import com.example.hotelbooking.dto.BookingResponse;
import com.example.hotelbooking.dto.RoomRequest;
import com.example.hotelbooking.entity.Booking;
import com.example.hotelbooking.entity.Room;
import com.example.hotelbooking.entity.User;
import com.example.hotelbooking.enums.BookingStatus;
import com.example.hotelbooking.service.BookingService;
import com.example.hotelbooking.service.RoomService;
import com.example.hotelbooking.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin-only API endpoints
 * Only users with ADMIN role can access these endpoints
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRestController {

    private final RoomService roomService;
    private final UserService userService;
    private final BookingService bookingService;

    public AdminRestController(RoomService roomService, UserService userService, BookingService bookingService) {
        this.roomService = roomService;
        this.userService = userService;
        this.bookingService = bookingService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<?> getAdminDashboard(Authentication authentication) {
        return ResponseEntity.ok(Map.of(
                "message", "Welcome to Admin Dashboard",
                "user", authentication.getName(),
                "permissions", "FULL_ACCESS"
        ));
    }

    // ==================== USER MANAGEMENT ====================

    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<User> getUserById(@PathVariable String userId) {
        return ResponseEntity.ok(userService.getUserById(userId));
    }

    @GetMapping("/users/role/{role}")
    public ResponseEntity<List<User>> getUsersByRole(@PathVariable String role) {
        return ResponseEntity.ok(userService.getUsersByRole(role));
    }

    // Create manager
    @PostMapping("/users/manager")
    public ResponseEntity<User> createManager(@RequestBody Map<String, String> request) {
        String userId = request.get("userId");
        String password = request.get("password");
        String username = request.get("username");

        User manager = userService.createUser(userId, password, username, "manager");
        return ResponseEntity.status(HttpStatus.CREATED).body(manager);
    }

    // Update user status (for both room and manager)
    @PutMapping("/users/{userId}/status")
    public ResponseEntity<User> updateUserStatus(@PathVariable String userId, @RequestBody Map<String, String> request) {
        String status = request.get("status");
        User user = userService.updateUserStatus(userId, status);
        return ResponseEntity.ok(user);
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<?> deleteUser(@PathVariable String userId) {
        userService.deleteUser(userId);
        return ResponseEntity.ok(Map.of("message", "User deleted successfully", "userId", userId));
    }

    // ==================== ROOM MANAGEMENT ====================

    @GetMapping("/rooms")
    public ResponseEntity<List<Room>> getAllRooms() {
        return ResponseEntity.ok(roomService.getAllRooms());
    }

    @GetMapping("/rooms/{id}")
    public ResponseEntity<Room> getRoomById(@PathVariable Long id) {
        return ResponseEntity.ok(roomService.getRoomById(id));
    }

    @PostMapping("/rooms")
    public ResponseEntity<Room> createRoom(@Valid @RequestBody RoomRequest request) {
        Room room = roomService.createRoom(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(room);
    }

    @PutMapping("/rooms/{id}")
    public ResponseEntity<Room> updateRoom(@PathVariable Long id, @RequestBody RoomRequest request) {
        Room room = roomService.updateRoom(id, request);
        return ResponseEntity.ok(room);
    }

    @PutMapping("/rooms/{id}/status")
    public ResponseEntity<Room> updateRoomStatus(@PathVariable Long id, @RequestBody Map<String, Boolean> request) {
        Boolean available = request.get("available");
        Room room = roomService.updateRoomStatus(id, available);
        return ResponseEntity.ok(room);
    }

    @DeleteMapping("/rooms/{id}")
    public ResponseEntity<?> deleteRoom(@PathVariable Long id) {
        roomService.deleteRoom(id);
        return ResponseEntity.ok(Map.of("message", "Room deleted successfully", "roomId", id));
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

    @GetMapping("/reports")
    public ResponseEntity<?> getReports() {
        return ResponseEntity.ok(Map.of(
                "message", "System reports",
                "description", "This endpoint is only accessible by ADMIN"
        ));
    }
}

