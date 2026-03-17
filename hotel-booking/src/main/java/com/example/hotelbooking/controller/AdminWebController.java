package com.example.hotelbooking.controller;

import com.example.hotelbooking.dto.RoomRequest;
import com.example.hotelbooking.entity.Booking;
import com.example.hotelbooking.entity.Customer;
import com.example.hotelbooking.entity.Payment;
import com.example.hotelbooking.entity.Room;
import com.example.hotelbooking.entity.User;
import com.example.hotelbooking.enums.BookingStatus;
import com.example.hotelbooking.service.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Controller
@RequestMapping("/admin")
public class AdminWebController {

    private final BookingService bookingService;
    private final RoomService roomService;
    private final UserService userService;
    private final CustomerService customerService;
    private final PaymentService paymentService;

    public AdminWebController(BookingService bookingService, RoomService roomService,
                              UserService userService, CustomerService customerService,
                              PaymentService paymentService) {
        this.bookingService = bookingService;
        this.roomService = roomService;
        this.userService = userService;
        this.customerService = customerService;
        this.paymentService = paymentService;
    }

    // Check if user is admin, manager, or staff (can access admin panel)
    private boolean isAuthorized(HttpSession session) {
        String role = (String) session.getAttribute("role");
        return role != null && (role.equalsIgnoreCase("admin") || role.equalsIgnoreCase("manager") || role.equalsIgnoreCase("staff"));
    }

    private boolean isAdmin(HttpSession session) {
        String role = (String) session.getAttribute("role");
        return role != null && role.equalsIgnoreCase("admin");
    }

    // Check if user is admin or manager (can manage bookings, customers)
    private boolean isAdminOrManager(HttpSession session) {
        String role = (String) session.getAttribute("role");
        return role != null && (role.equalsIgnoreCase("admin") || role.equalsIgnoreCase("manager"));
    }

    // Check if user is staff (read-only access to bookings)
    private boolean isStaff(HttpSession session) {
        String role = (String) session.getAttribute("role");
        return role != null && role.equalsIgnoreCase("staff");
    }

    // ==================== DASHBOARD ====================
    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) {
        if (!isAuthorized(session)) {
            return "redirect:/login";
        }

        try {
            List<Booking> bookings = bookingService.getAllBookings();
            List<Room> rooms = roomService.getAllRooms();
            List<Customer> customers = customerService.getAllCustomers();

            model.addAttribute("totalBookings", bookings != null ? bookings.size() : 0);
            model.addAttribute("totalRooms", rooms != null ? rooms.size() : 0);
            model.addAttribute("totalCustomers", customers != null ? customers.size() : 0);

            if (isAdmin(session)) {
                List<User> users = userService.getAllUsers();
                model.addAttribute("totalUsers", users != null ? users.size() : 0);
            } else {
                model.addAttribute("totalUsers", 0);
            }

            // Recent bookings (last 5)
            if (bookings != null && !bookings.isEmpty()) {
                List<Booking> recentBookings = bookings.stream()
                        .filter(b -> b.getCreatedAt() != null)
                        .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                        .limit(5)
                        .toList();
                model.addAttribute("recentBookings", recentBookings);
            } else {
                model.addAttribute("recentBookings", List.of());
            }
        } catch (Exception e) {
            model.addAttribute("totalBookings", 0);
            model.addAttribute("totalRooms", 0);
            model.addAttribute("totalCustomers", 0);
            model.addAttribute("totalUsers", 0);
            model.addAttribute("recentBookings", List.of());
            model.addAttribute("error", "Error loading data: " + e.getMessage());
        }

        return "admin/dashboard";
    }

    // ==================== BOOKING MANAGEMENT ====================
    @GetMapping("/bookings")
    public String listBookings(@RequestParam(required = false) String status,
                               HttpSession session, Model model) {
        if (!isAuthorized(session)) {
            return "redirect:/login";
        }

        try {
            List<Booking> bookings = bookingService.getAllBookings();

            if (status != null && !status.isEmpty()) {
                BookingStatus filterStatus = BookingStatus.valueOf(status);
                bookings = bookings.stream()
                        .filter(b -> b.getStatus() == filterStatus)
                        .toList();
            }

            model.addAttribute("bookings", bookings);
        } catch (Exception e) {
            model.addAttribute("bookings", List.of());
            model.addAttribute("error", "Error loading bookings: " + e.getMessage());
        }
        return "admin/bookings";
    }

    @GetMapping("/bookings/{id}")
    public String viewBookingDetails(@PathVariable Long id, HttpSession session, Model model) {
        if (!isAuthorized(session)) {
            return "redirect:/login";
        }

        try {
            Booking booking = bookingService.getAllBookings().stream()
                    .filter(b -> b.getId().equals(id))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Booking not found"));

            model.addAttribute("booking", booking);
            return "admin/booking-details";
        } catch (Exception e) {
            return "redirect:/admin/bookings";
        }
    }

    @GetMapping("/bookings/create")
    public String showCreateBookingForm(HttpSession session, Model model) {
        if (!isAdminOrManager(session)) {
            return "redirect:/admin/bookings";
        }

        model.addAttribute("rooms", roomService.getAllRooms());
        model.addAttribute("booking", null);
        return "admin/booking-form";
    }

    @PostMapping("/bookings/create")
    public String createBooking(@RequestParam Long roomId,
                                @RequestParam String checkInDate,
                                @RequestParam String checkOutDate,
                                @RequestParam Integer numberOfGuests,
                                @RequestParam String customerName,
                                @RequestParam String customerEmail,
                                @RequestParam String customerPhone,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {
        if (!isAdminOrManager(session)) {
            return "redirect:/admin/bookings";
        }

        try {
            Room room = roomService.getRoomById(roomId);
            LocalDate checkIn = LocalDate.parse(checkInDate);
            LocalDate checkOut = LocalDate.parse(checkOutDate);

            // Create or find customer
            Customer customer;
            try {
                customer = customerService.getCustomerByEmail(customerEmail);
                customer.setFullName(customerName);
                customer.setPhoneNumber(customerPhone);
            } catch (Exception e) {
                customer = new Customer();
                customer.setFullName(customerName);
                customer.setEmail(customerEmail);
                customer.setPhoneNumber(customerPhone);
            }

            // Calculate total
            long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
            BigDecimal total = room.getPricePerNight().multiply(BigDecimal.valueOf(nights));

            // Create booking
            Booking booking = new Booking();
            booking.setRoom(room);
            booking.setCustomer(customer);
            booking.setCheckInDate(checkIn);
            booking.setCheckOutDate(checkOut);
            booking.setNumberOfGuests(numberOfGuests);
            booking.setTotalAmount(total);
            booking.setStatus(BookingStatus.CONFIRMED);

            // Save via service (need to add method)
            com.example.hotelbooking.dto.BookingRequest request = new com.example.hotelbooking.dto.BookingRequest();
            request.setRoomId(roomId);
            request.setCheckInDate(checkIn);
            request.setCheckOutDate(checkOut);
            request.setNumberOfGuests(numberOfGuests);

            com.example.hotelbooking.dto.CustomerInfoRequest customerInfo = new com.example.hotelbooking.dto.CustomerInfoRequest();
            customerInfo.setFullName(customerName);
            customerInfo.setEmail(customerEmail);
            customerInfo.setPhoneNumber(customerPhone);
            request.setCustomerInfo(customerInfo);

            bookingService.createBooking(request);

            redirectAttributes.addFlashAttribute("success", "Booking created successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/admin/bookings";
    }

    @GetMapping("/bookings/edit/{id}")
    public String showEditBookingForm(@PathVariable Long id, HttpSession session, Model model) {
        if (!isAdminOrManager(session)) {
            return "redirect:/admin/bookings";
        }

        try {
            Booking booking = bookingService.getAllBookings().stream()
                    .filter(b -> b.getId().equals(id))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Booking not found"));

            model.addAttribute("booking", booking);
            model.addAttribute("rooms", roomService.getAllRooms());
            return "admin/booking-form";
        } catch (Exception e) {
            return "redirect:/admin/bookings";
        }
    }

    @PostMapping("/bookings/{id}/update")
    public String updateBooking(@PathVariable Long id,
                                @RequestParam Long roomId,
                                @RequestParam String checkInDate,
                                @RequestParam String checkOutDate,
                                @RequestParam Integer numberOfGuests,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {
        if (!isAdminOrManager(session)) {
            return "redirect:/admin/bookings";
        }

        try {
            com.example.hotelbooking.dto.BookingRequest request = new com.example.hotelbooking.dto.BookingRequest();
            request.setRoomId(roomId);
            request.setCheckInDate(LocalDate.parse(checkInDate));
            request.setCheckOutDate(LocalDate.parse(checkOutDate));
            request.setNumberOfGuests(numberOfGuests);

            bookingService.updateBooking(id, request);
            redirectAttributes.addFlashAttribute("success", "Booking updated successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/admin/bookings";
    }

    @PostMapping("/bookings/{id}/status")
    public String updateBookingStatus(@PathVariable Long id,
                                      @RequestParam String status,
                                      HttpSession session,
                                      RedirectAttributes redirectAttributes) {
        if (!isAdminOrManager(session)) {
            return "redirect:/admin/bookings";
        }

        try {
            bookingService.updateBookingStatus(id, BookingStatus.valueOf(status));
            redirectAttributes.addFlashAttribute("success", "Booking status updated!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/admin/bookings";
    }

    @PostMapping("/bookings/{id}/delete")
    @ResponseBody
    public String deleteBooking(@PathVariable Long id, HttpSession session) {
        if (!isAdmin(session)) {
            return "unauthorized";
        }

        try {
            bookingService.deleteBooking(id);
            return "success";
        } catch (Exception e) {
            return "error";
        }
    }

    // ==================== ROOM MANAGEMENT ====================
    @GetMapping("/rooms")
    public String listRooms(HttpSession session, Model model) {
        if (!isAuthorized(session)) {
            return "redirect:/login";
        }

        model.addAttribute("rooms", roomService.getAllRooms());
        return "admin/rooms";
    }

    @GetMapping("/rooms/{id}/view")
    public String viewRoomDetails(@PathVariable Long id, HttpSession session, Model model) {
        if (!isAuthorized(session)) {
            return "redirect:/login";
        }

        try {
            Room room = roomService.getRoomById(id);
            model.addAttribute("room", room);
            return "admin/room-details";
        } catch (Exception e) {
            return "redirect:/admin/rooms";
        }
    }

    @GetMapping("/rooms/create")
    public String showCreateRoomForm(HttpSession session, Model model) {
        if (!isAdmin(session)) {
            return "redirect:/admin/rooms";
        }

        model.addAttribute("room", null);
        return "admin/room-form";
    }

    @PostMapping("/rooms/create")
    public String createRoom(@RequestParam String roomNumber,
                             @RequestParam String roomType,
                             @RequestParam BigDecimal pricePerNight,
                             @RequestParam Integer capacity,
                             @RequestParam(required = false) String description,
                             @RequestParam(required = false) String imageUrl,
                             @RequestParam(required = false, defaultValue = "true") Boolean available,
                             HttpSession session,
                             RedirectAttributes redirectAttributes) {
        if (!isAdmin(session)) {
            return "redirect:/admin/rooms";
        }

        try {
            RoomRequest request = new RoomRequest();
            request.setRoomNumber(roomNumber);
            request.setRoomType(roomType);
            request.setPricePerNight(pricePerNight);
            request.setCapacity(capacity);
            request.setDescription(description);
            request.setImageUrl(imageUrl);
            request.setAvailable(available);

            roomService.createRoom(request);
            redirectAttributes.addFlashAttribute("success", "Room created successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/admin/rooms";
    }

    @GetMapping("/rooms/edit/{id}")
    public String showEditRoomForm(@PathVariable Long id, HttpSession session, Model model) {
        if (!isAdmin(session)) {
            return "redirect:/admin/rooms";
        }

        model.addAttribute("room", roomService.getRoomById(id));
        return "admin/room-form";
    }

    @PostMapping("/rooms/{id}/update")
    public String updateRoom(@PathVariable Long id,
                             @RequestParam String roomNumber,
                             @RequestParam String roomType,
                             @RequestParam BigDecimal pricePerNight,
                             @RequestParam Integer capacity,
                             @RequestParam(required = false) String description,
                             @RequestParam(required = false) String imageUrl,
                             @RequestParam(required = false) Boolean available,
                             HttpSession session,
                             RedirectAttributes redirectAttributes) {
        if (!isAdmin(session)) {
            return "redirect:/admin/rooms";
        }

        try {
            RoomRequest request = new RoomRequest();
            request.setRoomNumber(roomNumber);
            request.setRoomType(roomType);
            request.setPricePerNight(pricePerNight);
            request.setCapacity(capacity);
            request.setDescription(description);
            request.setImageUrl(imageUrl);
            request.setAvailable(available != null ? available : false);

            roomService.updateRoom(id, request);
            redirectAttributes.addFlashAttribute("success", "Room updated successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/admin/rooms";
    }

    @PostMapping("/rooms/{id}/status")
    public String updateRoomStatus(@PathVariable Long id,
                                   @RequestParam Boolean available,
                                   HttpSession session,
                                   RedirectAttributes redirectAttributes) {
        if (!isAdmin(session)) {
            return "redirect:/admin/rooms";
        }

        try {
            roomService.updateRoomStatus(id, available);
            redirectAttributes.addFlashAttribute("success", "Room status updated!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/admin/rooms";
    }

    @PostMapping("/rooms/{id}/delete")
    @ResponseBody
    public String deleteRoom(@PathVariable Long id, HttpSession session) {
        if (!isAdmin(session)) {
            return "unauthorized";
        }

        try {
            roomService.deleteRoom(id);
            return "success";
        } catch (Exception e) {
            return "error";
        }
    }

    // ==================== USER MANAGEMENT ====================
    @GetMapping("/users")
    public String listUsers(HttpSession session, Model model) {
        if (!isAdmin(session)) {
            return "redirect:/admin/dashboard";
        }

        try {
            model.addAttribute("users", userService.getAllUsers());
        } catch (Exception e) {
            model.addAttribute("users", List.of());
            model.addAttribute("error", "Error loading users: " + e.getMessage());
        }
        return "admin/users";
    }

    @GetMapping("/users/create")
    public String showCreateUserForm(HttpSession session, Model model) {
        if (!isAdmin(session)) {
            return "redirect:/admin/users";
        }
        model.addAttribute("roles", List.of("manager", "staff", "customer"));
        return "admin/user-form";
    }

    @PostMapping("/users/create")
    public String createUser(@RequestParam String userId,
                             @RequestParam String username,
                             @RequestParam String password,
                             @RequestParam String role,
                             HttpSession session,
                             RedirectAttributes redirectAttributes) {
        if (!isAdmin(session)) {
            return "redirect:/admin/users";
        }

        try {
            userService.createUser(userId, password, username, role);
            redirectAttributes.addFlashAttribute("success", "User created successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/admin/users";
    }

    @GetMapping("/users/create-manager")
    public String showCreateManagerForm(HttpSession session, Model model) {
        return showCreateUserForm(session, model);
    }

    @PostMapping("/users/create-manager")
    public String createManager(@RequestParam String userId,
                                @RequestParam String username,
                                @RequestParam String password,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {
        return createUser(userId, username, password, "manager", session, redirectAttributes);
    }

    @PostMapping("/users/{userId}/status")
    public String updateUserStatus(@PathVariable String userId,
                                   @RequestParam String status,
                                   HttpSession session,
                                   RedirectAttributes redirectAttributes) {
        if (!isAdmin(session)) {
            return "redirect:/admin/users";
        }

        try {
            userService.updateUserStatus(userId, status);
            redirectAttributes.addFlashAttribute("success", "User status updated!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/admin/users";
    }

    @PostMapping("/users/{userId}/delete")
    @ResponseBody
    public String deleteUser(@PathVariable String userId, HttpSession session) {
        if (!isAdmin(session)) {
            return "unauthorized";
        }

        try {
            userService.deleteUser(userId);
            return "success";
        } catch (Exception e) {
            return "error";
        }
    }

    // ==================== PAYMENT MANAGEMENT ====================
    @GetMapping("/payments")
    public String listPayments(HttpSession session, Model model) {
        // Only Admin can view payments
        if (!isAdmin(session)) {
            return "redirect:/admin/dashboard";
        }

        try {
            List<Payment> payments = paymentService.getAllPayments();
            model.addAttribute("payments", payments);

            // Calculate statistics
            model.addAttribute("totalPayments", payments != null ? payments.size() : 0);
            model.addAttribute("successCount", payments != null ?
                payments.stream().filter(p -> p.getStatus().name().equals("SUCCESS")).count() : 0);
            model.addAttribute("pendingCount", payments != null ?
                payments.stream().filter(p -> p.getStatus().name().equals("PENDING")).count() : 0);
            model.addAttribute("failedCount", payments != null ?
                payments.stream().filter(p -> p.getStatus().name().equals("FAILED")).count() : 0);
        } catch (Exception e) {
            model.addAttribute("payments", List.of());
            model.addAttribute("totalPayments", 0);
            model.addAttribute("successCount", 0);
            model.addAttribute("pendingCount", 0);
            model.addAttribute("failedCount", 0);
            model.addAttribute("error", "Error loading payments: " + e.getMessage());
        }
        return "admin/payments";
    }

    @GetMapping("/payments/{id}")
    public String viewPaymentDetails(@PathVariable Long id, HttpSession session, Model model) {
        if (!isAdmin(session)) {
            return "redirect:/admin/payments";
        }

        try {
            Payment payment = paymentService.getPaymentById(id);
            model.addAttribute("payment", payment);
            return "admin/payment-details";
        } catch (Exception e) {
            return "redirect:/admin/payments";
        }
    }

    // ==================== CUSTOMER MANAGEMENT ====================
    @GetMapping("/customers")
    public String listCustomers(HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        if (!isAdminOrManager(session)) {
            redirectAttributes.addFlashAttribute("error", "Only admin or manager can view customers.");
            return "redirect:/admin/dashboard";
        }

        try {
            model.addAttribute("customers", customerService.getAllCustomers());
        } catch (Exception e) {
            model.addAttribute("customers", List.of());
            model.addAttribute("error", "Error loading customers: " + e.getMessage());
        }
        return "admin/customers";
    }

    @PostMapping("/customers/{id}/status")
    public String updateCustomerStatus(@PathVariable Long id,
                                       @RequestParam String status,
                                       HttpSession session,
                                       RedirectAttributes redirectAttributes) {
        if (!isAdminOrManager(session)) {
            return "redirect:/admin/customers";
        }

        try {
            customerService.updateCustomerStatus(id, status);
            redirectAttributes.addFlashAttribute("success", "Customer status updated!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/admin/customers";
    }

    @PostMapping("/customers/{id}/update")
    public String updateCustomer(@PathVariable Long id,
                                 @RequestParam String fullName,
                                 @RequestParam String email,
                                 @RequestParam String phoneNumber,
                                 HttpSession session,
                                 RedirectAttributes redirectAttributes) {
        if (!isAdminOrManager(session)) {
            return "redirect:/admin/customers";
        }

        try {
            customerService.updateCustomer(id, fullName, email, phoneNumber);
            redirectAttributes.addFlashAttribute("success", "Customer updated!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/admin/customers";
    }

    @PostMapping("/customers/{id}/delete")
    @ResponseBody
    public String deleteCustomer(@PathVariable Long id, HttpSession session) {
        if (!isAdmin(session)) {
            return "unauthorized";
        }

        try {
            customerService.deleteCustomer(id);
            return "success";
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }
}

