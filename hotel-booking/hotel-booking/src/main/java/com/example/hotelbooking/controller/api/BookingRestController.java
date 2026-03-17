package com.example.hotelbooking.controller.api;

import com.example.hotelbooking.dto.AvailableRoomResponse;
import com.example.hotelbooking.dto.BookingRequest;
import com.example.hotelbooking.dto.BookingResponse;
import com.example.hotelbooking.dto.RoomSearchRequest;
import com.example.hotelbooking.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingRestController {
    
    private final BookingService bookingService;
    
    public BookingRestController(BookingService bookingService) {
        this.bookingService = bookingService;
    }
    
    @PostMapping("/search-rooms")
    public ResponseEntity<List<AvailableRoomResponse>> searchAvailableRooms(
            @Valid @RequestBody RoomSearchRequest request) {
        try {
            List<AvailableRoomResponse> availableRooms = bookingService.searchAvailableRooms(request);
            return ResponseEntity.ok(availableRooms);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }
    
    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(@Valid @RequestBody BookingRequest request) {
        try {
            BookingResponse booking = bookingService.createBooking(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(booking);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }
    
    @GetMapping("/{bookingId}")
    public ResponseEntity<BookingResponse> getBookingById(@PathVariable Long bookingId) {
        try {
            BookingResponse booking = bookingService.getBookingById(bookingId);
            return ResponseEntity.ok(booking);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PutMapping("/{bookingId}/cancel")
    public ResponseEntity<?> cancelBooking(@PathVariable Long bookingId,
                                           org.springframework.security.core.Authentication authentication) {
        try {
            String userId = authentication != null ? authentication.getName() : null;
            BookingResponse booking = bookingService.cancelBooking(bookingId, userId, null, null);
            return ResponseEntity.ok(booking);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(java.util.Map.of("error", e.getMessage()));
        }
    }
}
