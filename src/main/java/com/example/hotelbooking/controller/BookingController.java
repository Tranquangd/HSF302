package com.example.hotelbooking.controller;

import com.example.hotelbooking.dto.AvailableRoomResponse;
import com.example.hotelbooking.dto.BookingRequest;
import com.example.hotelbooking.dto.BookingResponse;
import com.example.hotelbooking.dto.CancelBookingRequest;
import com.example.hotelbooking.dto.RoomSearchRequest;
import com.example.hotelbooking.entity.Room;
import com.example.hotelbooking.entity.User;
import com.example.hotelbooking.repository.BookingRepository;
import com.example.hotelbooking.service.BookingService;
import com.example.hotelbooking.service.RoomService;
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
    private final RoomService roomService;
    private final BookingRepository bookingRepository;

    public BookingController(BookingService bookingService, RoomService roomService,
            BookingRepository bookingRepository) {
        this.bookingService = bookingService;
        this.roomService = roomService;
        this.bookingRepository = bookingRepository;
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
            if (availableRooms.isEmpty()) {
                model.addAttribute("noRoomsFound", true);
            }
            return "room-list";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return "search-rooms";
        }
    }

    @GetMapping("/create")
    public String showBookingForm(@RequestParam Long roomId,
            @RequestParam(required = false) String checkInDate,
            @RequestParam(required = false) String checkOutDate,
            @RequestParam(required = false) Integer numberOfGuests,
            HttpSession session,
            RedirectAttributes redirectAttributes,
            Model model) {
        // Check if user is logged in
        if (session.getAttribute("user") == null) {
            return "redirect:/login?returnUrl=/bookings/create?roomId=" + roomId;
        }

        Room room = roomService.getRoomById(roomId);
        model.addAttribute("room", room);

        BookingRequest bookingRequest = new BookingRequest();
        bookingRequest.setRoomId(roomId);

        java.time.LocalDate parsedCheckIn = null;
        java.time.LocalDate parsedCheckOut = null;

        if (checkInDate != null && !checkInDate.isEmpty()) {
            parsedCheckIn = java.time.LocalDate.parse(checkInDate);
            bookingRequest.setCheckInDate(parsedCheckIn);
        }
        if (checkOutDate != null && !checkOutDate.isEmpty()) {
            parsedCheckOut = java.time.LocalDate.parse(checkOutDate);
            bookingRequest.setCheckOutDate(parsedCheckOut);
        }
        if (numberOfGuests != null) {
            bookingRequest.setNumberOfGuests(numberOfGuests);
        }

        // Nếu đã có ngày, kiểm tra xung đột ngay lập tức
        if (parsedCheckIn != null && parsedCheckOut != null) {
            java.util.List<com.example.hotelbooking.entity.Booking> conflicts = bookingRepository
                    .findConflictingBookings(roomId, parsedCheckIn, parsedCheckOut);
            if (!conflicts.isEmpty()) {
                model.addAttribute("roomFull", true);
                model.addAttribute("error",
                        "Phòng " + room.getRoomNumber() + " đã được đặt đầy trong khoảng " +
                                checkInDate + " → " + checkOutDate + ". Vui lòng chọn ngày khác hoặc phòng khác.");
            }
        }

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
            redirectAttributes.addFlashAttribute("success", "Đặt phòng thành công!");
            return "redirect:/bookings/" + booking.getBookingId();
        } catch (com.example.hotelbooking.exception.InvalidBookingException e) {
            // Lỗi phòng đã đặt hoặc không hợp lệ
            String msg = e.getMessage();
            if (msg != null && msg.contains("already booked")) {
                model.addAttribute("roomFull", true);
                model.addAttribute("error",
                        "🚫 Phòng đã được đặt đầy trong khoảng ngày này. Vui lòng chọn ngày khác.");
            } else {
                model.addAttribute("error", msg);
            }
            // Thêm lại thông tin phòng để hiển thị banner
            try {
                Room room = roomService.getRoomById(request.getRoomId());
                model.addAttribute("room", room);
            } catch (Exception ignored) {
            }
            return "create-booking";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            try {
                Room room = roomService.getRoomById(request.getRoomId());
                model.addAttribute("room", room);
            } catch (Exception ignored) {
            }
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

    /**
     * Hiển thị form hủy đặt phòng
     */
    @GetMapping("/{bookingId}/cancel")
    public String showCancelForm(@PathVariable Long bookingId,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng đăng nhập để hủy đặt phòng");
            return "redirect:/login";
        }

        try {
            BookingResponse booking = bookingService.getBookingById(bookingId);
            // Ownership verification is handled by the service method
            model.addAttribute("booking", booking);
            model.addAttribute("cancelRequest", new CancelBookingRequest());
            return "cancel-booking";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi: " + e.getMessage());
            return "redirect:/bookings/my-bookings";
        }
    }

    /**
     * Khách hàng hủy đặt phòng của mình với lý do.
     * Chỉ cho phép hủy khi trạng thái là PENDING_PAYMENT hoặc CONFIRMED.
     * Kiểm tra quyền sở hữu: chỉ chủ booking mới được hủy.
     */
    @PostMapping("/{bookingId}/cancel")
    public String cancelBooking(@PathVariable Long bookingId,
            @ModelAttribute("cancelRequest") CancelBookingRequest cancelRequest,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        // Kiểm tra đăng nhập
        User user = (User) session.getAttribute("user");
        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng đăng nhập để hủy đặt phòng");
            return "redirect:/login";
        }

        try {
            // Hủy booking với lý do - truyền userId để kiểm tra quyền sở hữu
            bookingService.cancelBooking(bookingId, user.getUserId(), 
                    cancelRequest.getCancellationReason(), cancelRequest.getCancellationNote());
            redirectAttributes.addFlashAttribute("success", "Đã hủy đặt phòng #" + bookingId + " thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi khi hủy đặt phòng: " + e.getMessage());
        }

        return "redirect:/bookings/my-bookings";
    }

    /**
     * Hiển thị lịch sử đặt phòng của khách hàng đang đăng nhập.
     */
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
