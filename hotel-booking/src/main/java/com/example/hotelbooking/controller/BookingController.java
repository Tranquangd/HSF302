package com.example.hotelbooking.controller;

import com.example.hotelbooking.dto.AvailableRoomResponse;
import com.example.hotelbooking.dto.BookingRequest;
import com.example.hotelbooking.dto.BookingResponse;
import com.example.hotelbooking.dto.RoomSearchRequest;
import com.example.hotelbooking.entity.User;
import com.example.hotelbooking.service.BookingService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.List;

@Controller
@RequestMapping("/bookings")
public class BookingController {
    
    private final BookingService bookingService;
    
    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }
    
    @GetMapping("/search")
    public String showSearchForm(Model model) {
        model.addAttribute("searchRequest", new RoomSearchRequest());
        return "search-rooms";
    }
    
    @PostMapping("/search")
    public String searchAvailableRooms(@Valid @ModelAttribute("searchRequest") RoomSearchRequest request,
                                      BindingResult result,
                                      Model model) {
        if (result.hasErrors()) {
            return "search-rooms";
        }
        
        try {
            List<AvailableRoomResponse> availableRooms = bookingService.searchAvailableRooms(request);
            model.addAttribute("rooms", availableRooms);
            model.addAttribute("searchRequest", request);
            return "room-list";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return "search-rooms";
        }
    }
    
    @GetMapping("/create")
    public String showBookingForm(@RequestParam Long roomId,
                                 @RequestParam String checkInDate,
                                 @RequestParam String checkOutDate,
                                 @RequestParam Integer numberOfGuests,
                                 HttpSession session,
                                 RedirectAttributes redirectAttributes,
                                 Model model) {
        // Check if user is logged in
        if (session.getAttribute("user") == null) {
            redirectAttributes.addFlashAttribute("error", "Please login to book a room");
            redirectAttributes.addFlashAttribute("returnUrl", "/bookings/create?roomId=" + roomId + 
                "&checkInDate=" + checkInDate + "&checkOutDate=" + checkOutDate + "&numberOfGuests=" + numberOfGuests);
            return "redirect:/login";
        }
        
        BookingRequest bookingRequest = new BookingRequest();
        bookingRequest.setRoomId(roomId);
        bookingRequest.setCheckInDate(java.time.LocalDate.parse(checkInDate));
        bookingRequest.setCheckOutDate(java.time.LocalDate.parse(checkOutDate));
        bookingRequest.setNumberOfGuests(numberOfGuests);
        
        model.addAttribute("bookingRequest", bookingRequest);
        return "create-booking";
    }
    
    @PostMapping("/create")
    public String createBooking(@Valid @ModelAttribute("bookingRequest") BookingRequest request,
                               BindingResult result,
                               HttpSession session,
                               RedirectAttributes redirectAttributes,
                               Model model) {
        // Check if user is logged in
        User user = (User) session.getAttribute("user");
        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "Please login to book a room");
            return "redirect:/login";
        }
        
        if (result.hasErrors()) {
            return "create-booking";
        }
        
        try {
            // Set userId from logged in user
            request.setUserId(user.getUserId());
            BookingResponse booking = bookingService.createBooking(request);
            redirectAttributes.addFlashAttribute("success", "Booking created successfully!");
            return "redirect:/bookings/" + booking.getBookingId();
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return "create-booking";
        }
    }
    
    @GetMapping("/{bookingId}")
    public String getBookingById(@PathVariable Long bookingId, Model model) {
        try {
            BookingResponse booking = bookingService.getBookingById(bookingId);
            model.addAttribute("booking", booking);
            return "booking-details";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return "error";
        }
    }

    @GetMapping("/my-bookings")
    public String getMyBookings(HttpSession session,
                                RedirectAttributes redirectAttributes,
                                Model model) {
        // Check if user is logged in
        User user = (User) session.getAttribute("user");
        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "Please login to view your booking history");
            redirectAttributes.addFlashAttribute("returnUrl", "/bookings/my-bookings");
            return "redirect:/login";
        }

        // Get user's userId
        String userId = user.getUserId();

        try {
            List<BookingResponse> bookings = bookingService.getBookingHistoryByUserId(userId);
            model.addAttribute("bookings", bookings);
            if (bookings.isEmpty()) {
                model.addAttribute("info", "You haven't made any bookings yet.");
            }
            return "my-bookings";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("bookings", List.of());
            return "my-bookings";
        }
    }
}
