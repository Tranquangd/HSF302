package com.example.hotelbooking.service;

import com.example.hotelbooking.dto.*;
import com.example.hotelbooking.entity.Booking;
import com.example.hotelbooking.entity.Customer;
import com.example.hotelbooking.entity.Payment;
import com.example.hotelbooking.entity.Refund;
import com.example.hotelbooking.entity.TransactionLog;
import com.example.hotelbooking.entity.Room;
import com.example.hotelbooking.enums.BookingStatus;
import com.example.hotelbooking.enums.PaymentStatus;
import com.example.hotelbooking.enums.TransactionType;
import com.example.hotelbooking.exception.InvalidBookingException;
import com.example.hotelbooking.exception.ResourceNotFoundException;
import com.example.hotelbooking.repository.BookingRepository;
import com.example.hotelbooking.repository.CustomerRepository;
import com.example.hotelbooking.repository.PaymentRepository;
import com.example.hotelbooking.repository.RoomRepository;
import com.example.hotelbooking.repository.TransactionLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class BookingService {

    private static final Logger logger = LoggerFactory.getLogger(BookingService.class);

    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;
    private final CustomerRepository customerRepository;
    private final PaymentRepository paymentRepository;
    private final RefundService refundService;
    private final TransactionLogRepository transactionLogRepository;

    public BookingService(RoomRepository roomRepository,
            BookingRepository bookingRepository,
            CustomerRepository customerRepository,
            PaymentRepository paymentRepository,
            RefundService refundService,
            TransactionLogRepository transactionLogRepository) {
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
        this.customerRepository = customerRepository;
        this.paymentRepository = paymentRepository;
        this.refundService = refundService;
        this.transactionLogRepository = transactionLogRepository;
    }

    public List<AvailableRoomResponse> searchAvailableRooms(RoomSearchRequest request) {
        logger.info("=== ROOM SEARCH DEBUG ===");
        logger.info("Check-in: {}", request.getCheckInDate());
        logger.info("Check-out: {}", request.getCheckOutDate());
        logger.info("Guests: {}", request.getNumberOfGuests());
        logger.info("Room Type: {}", request.getRoomType());
        logger.info("Room Number (filter): {}", request.getRoomNumber());

        // Chuẩn hoá dữ liệu ngày tháng để linh hoạt hơn:
        // - Nếu chỉ nhập check-in, tự coi như đặt 1 đêm (check-out = check-in + 1).
        // - Nếu chỉ nhập check-out, tự coi như đặt 1 đêm (check-in = check-out - 1).
        java.time.LocalDate checkIn = request.getCheckInDate();
        java.time.LocalDate checkOut = request.getCheckOutDate();

        if (checkIn != null && checkOut == null) {
            checkOut = checkIn.plusDays(1);
        } else if (checkIn == null && checkOut != null) {
            checkIn = checkOut.minusDays(1);
        }

        List<Room> availableRooms;

        // Nếu đã có đủ check-in, check-out và số khách -> dùng query kiểm tra trùng booking
        if (checkIn != null && checkOut != null && request.getNumberOfGuests() != null) {
            validateDates(checkIn, checkOut);

            availableRooms = roomRepository.findAvailableRooms(
                    checkIn,
                    checkOut,
                    request.getNumberOfGuests(),
                    request.getRoomType());
        } else {
            // Nếu thiếu ngày hoặc thiếu số khách -> chỉ lọc trên danh sách tất cả phòng đang available
            availableRooms = roomRepository.findAllAvailableRooms();

            // Lọc theo số khách nếu có
            if (request.getNumberOfGuests() != null) {
                Integer guests = request.getNumberOfGuests();
                availableRooms = availableRooms.stream()
                        .filter(room -> room.getCapacity() != null && room.getCapacity() >= guests)
                        .collect(Collectors.toList());
            }

            // Lọc theo loại phòng nếu có
            if (request.getRoomType() != null && !request.getRoomType().trim().isEmpty()) {
                String type = request.getRoomType().trim();
                availableRooms = availableRooms.stream()
                        .filter(room -> type.equalsIgnoreCase(room.getRoomType()))
                        .collect(Collectors.toList());
            }
        }

        // Nếu người dùng nhập thêm filter số phòng -> lọc tiếp danh sách phòng trống theo roomNumber
        if (request.getRoomNumber() != null && !request.getRoomNumber().trim().isEmpty()) {
            String roomNumberKeyword = request.getRoomNumber().trim().toLowerCase();
            availableRooms = availableRooms.stream()
                    .filter(room -> room.getRoomNumber() != null &&
                            room.getRoomNumber().toLowerCase().contains(roomNumberKeyword))
                    .collect(Collectors.toList());
        }

        logger.info("Query returned {} rooms", availableRooms.size());
        if (!availableRooms.isEmpty()) {
            availableRooms.forEach(room -> logger.info("Room found: {} - {} - ${}", room.getRoomNumber(),
                    room.getRoomType(), room.getPricePerNight()));
        }

        if (availableRooms.isEmpty()) {
            logger.warn("NO ROOMS FOUND for search criteria. checkIn={}, checkOut={}, guests={}, type={}, roomNumber={}",
                    checkIn, checkOut, request.getNumberOfGuests(), request.getRoomType(), request.getRoomNumber());
            return java.util.Collections.emptyList();
        }

        long numberOfNights = 1;
        if (checkIn != null && checkOut != null) {
            numberOfNights = ChronoUnit.DAYS.between(checkIn, checkOut);
            if (numberOfNights <= 0) {
                numberOfNights = 1;
            }
        }
        final long nights = numberOfNights;

        return availableRooms.stream()
                .map(room -> {
                    BigDecimal totalPrice = room.getPricePerNight()
                            .multiply(BigDecimal.valueOf(nights));

                    return new AvailableRoomResponse(
                            room.getId(),
                            room.getRoomNumber(),
                            room.getRoomType(),
                            room.getPricePerNight(),
                            room.getCapacity(),
                            room.getDescription(),
                            totalPrice,
                            (int) nights,
                            room.getImageUrl());
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public BookingResponse createBooking(BookingRequest request) {
        validateDates(request.getCheckInDate(), request.getCheckOutDate());

        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with id: " + request.getRoomId()));

        if (!room.getAvailable()) {
            throw new InvalidBookingException("Room is not available");
        }

        if (room.getCapacity() < request.getNumberOfGuests()) {
            throw new InvalidBookingException("Room capacity is insufficient for the number of guests");
        }

        List<Booking> conflictingBookings = bookingRepository.findConflictingBookings(
                room.getId(),
                request.getCheckInDate(),
                request.getCheckOutDate());

        if (!conflictingBookings.isEmpty()) {
            throw new InvalidBookingException("Room is already booked for the selected dates");
        }

        // ƯU TIÊN: nếu user đang đăng nhập, luôn dùng 1 Customer cho mỗi userId
        Customer customer = null;
        if (request.getUserId() != null) {
            customer = customerRepository.findByUserId(request.getUserId()).orElse(null);
        }

        if (customer == null) {
            // Nếu chưa có theo userId, fallback tìm theo email
            customer = customerRepository.findByEmail(request.getCustomerInfo().getEmail())
                    .orElseGet(() -> {
                        Customer newCustomer = new Customer();
                        newCustomer.setFullName(request.getCustomerInfo().getFullName());
                        newCustomer.setEmail(request.getCustomerInfo().getEmail());
                        newCustomer.setPhoneNumber(request.getCustomerInfo().getPhoneNumber());
                        if (request.getUserId() != null) {
                            newCustomer.setUserId(request.getUserId());
                        }
                        return customerRepository.save(newCustomer);
                    });
        } else {
            // Đã có Customer cho userId này: cập nhật thông tin từ form đặt phòng (không tạo bản ghi mới)
            customer.setFullName(request.getCustomerInfo().getFullName());
            customer.setEmail(request.getCustomerInfo().getEmail());
            customer.setPhoneNumber(request.getCustomerInfo().getPhoneNumber());
            customer = customerRepository.save(customer);
        }

        // Luôn đồng bộ customer.userId theo user đang đăng nhập (theo email / userId khách hàng)
        // Tránh trường hợp customer.userId bị lệch (ví không hiển thị đúng số dư theo userId login)
        if (request.getUserId() != null && (customer.getUserId() == null || !customer.getUserId().equals(request.getUserId()))) {
            customer.setUserId(request.getUserId());
            customer = customerRepository.save(customer);
        }

        long numberOfNights = ChronoUnit.DAYS.between(
                request.getCheckInDate(),
                request.getCheckOutDate());
        BigDecimal totalAmount = room.getPricePerNight()
                .multiply(BigDecimal.valueOf(numberOfNights));

        Booking booking = new Booking();
        booking.setRoom(room);
        booking.setCustomer(customer);
        booking.setCheckInDate(request.getCheckInDate());
        booking.setCheckOutDate(request.getCheckOutDate());
        booking.setNumberOfGuests(request.getNumberOfGuests());
        booking.setTotalAmount(totalAmount);
        booking.setStatus(BookingStatus.PENDING_PAYMENT);

        Booking savedBooking = bookingRepository.save(booking);

        return new BookingResponse(
                savedBooking.getId(),
                room.getId(),
                room.getRoomNumber(),
                room.getRoomType(),
                savedBooking.getCheckInDate(),
                savedBooking.getCheckOutDate(),
                savedBooking.getNumberOfGuests(),
                savedBooking.getTotalAmount(),
                savedBooking.getStatus(),
                customer.getFullName(),
                customer.getEmail(),
                savedBooking.getCreatedAt());
    }

    public BookingResponse getBookingById(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));

        return new BookingResponse(
                booking.getId(),
                booking.getRoom().getId(),
                booking.getRoom().getRoomNumber(),
                booking.getRoom().getRoomType(),
                booking.getCheckInDate(),
                booking.getCheckOutDate(),
                booking.getNumberOfGuests(),
                booking.getTotalAmount(),
                booking.getStatus(),
                booking.getCustomer().getFullName(),
                booking.getCustomer().getEmail(),
                booking.getCreatedAt());
    }

    public List<Booking> getAllBookings() {
        return bookingRepository.findAll();
    }

    public List<BookingResponse> getBookingHistoryByCustomerEmail(String email) {
        List<Booking> bookings = bookingRepository.findByCustomerEmailOrderByCreatedAtDesc(email);
        return bookings.stream()
                .map(booking -> new BookingResponse(
                        booking.getId(),
                        booking.getRoom().getId(),
                        booking.getRoom().getRoomNumber(),
                        booking.getRoom().getRoomType(),
                        booking.getCheckInDate(),
                        booking.getCheckOutDate(),
                        booking.getNumberOfGuests(),
                        booking.getTotalAmount(),
                        booking.getStatus(),
                        booking.getCustomer().getFullName(),
                        booking.getCustomer().getEmail(),
                        booking.getCreatedAt()))
                .collect(Collectors.toList());
    }

    public List<BookingResponse> getBookingHistoryByUserId(String userId) {
        List<Booking> bookings = bookingRepository.findByCustomerUserIdOrderByCreatedAtDesc(userId);
        return bookings.stream()
                .map(booking -> new BookingResponse(
                        booking.getId(),
                        booking.getRoom().getId(),
                        booking.getRoom().getRoomNumber(),
                        booking.getRoom().getRoomType(),
                        booking.getCheckInDate(),
                        booking.getCheckOutDate(),
                        booking.getNumberOfGuests(),
                        booking.getTotalAmount(),
                        booking.getStatus(),
                        booking.getCustomer().getFullName(),
                        booking.getCustomer().getEmail(),
                        booking.getCreatedAt()))
                .collect(Collectors.toList());
    }

    @Transactional
    public BookingResponse updateBooking(Long bookingId, BookingRequest request) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));

        if (request.getCheckInDate() != null && request.getCheckOutDate() != null) {
            validateDates(request.getCheckInDate(), request.getCheckOutDate());
            booking.setCheckInDate(request.getCheckInDate());
            booking.setCheckOutDate(request.getCheckOutDate());

            // Recalculate total amount
            long numberOfNights = ChronoUnit.DAYS.between(
                    request.getCheckInDate(),
                    request.getCheckOutDate());
            BigDecimal totalAmount = booking.getRoom().getPricePerNight()
                    .multiply(BigDecimal.valueOf(numberOfNights));
            booking.setTotalAmount(totalAmount);
        }

        if (request.getNumberOfGuests() != null) {
            if (booking.getRoom().getCapacity() < request.getNumberOfGuests()) {
                throw new InvalidBookingException("Room capacity is insufficient for the number of guests");
            }
            booking.setNumberOfGuests(request.getNumberOfGuests());
        }

        if (request.getRoomId() != null && !request.getRoomId().equals(booking.getRoom().getId())) {
            Room newRoom = roomRepository.findById(request.getRoomId())
                    .orElseThrow(() -> new ResourceNotFoundException("Room not found with id: " + request.getRoomId()));
            booking.setRoom(newRoom);
        }

        Booking savedBooking = bookingRepository.save(booking);

        return new BookingResponse(
                savedBooking.getId(),
                savedBooking.getRoom().getId(),
                savedBooking.getRoom().getRoomNumber(),
                savedBooking.getRoom().getRoomType(),
                savedBooking.getCheckInDate(),
                savedBooking.getCheckOutDate(),
                savedBooking.getNumberOfGuests(),
                savedBooking.getTotalAmount(),
                savedBooking.getStatus(),
                savedBooking.getCustomer().getFullName(),
                savedBooking.getCustomer().getEmail(),
                savedBooking.getCreatedAt());
    }

    @Transactional
    public BookingResponse updateBookingStatus(Long bookingId, BookingStatus status) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));

        booking.setStatus(status);
        Booking savedBooking = bookingRepository.save(booking);

        return new BookingResponse(
                savedBooking.getId(),
                savedBooking.getRoom().getId(),
                savedBooking.getRoom().getRoomNumber(),
                savedBooking.getRoom().getRoomType(),
                savedBooking.getCheckInDate(),
                savedBooking.getCheckOutDate(),
                savedBooking.getNumberOfGuests(),
                savedBooking.getTotalAmount(),
                savedBooking.getStatus(),
                savedBooking.getCustomer().getFullName(),
                savedBooking.getCustomer().getEmail(),
                savedBooking.getCreatedAt());
    }

    @Transactional
    public BookingResponse cancelBooking(Long bookingId, String userId, String cancellationReason, String cancellationNote) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));

        // Verify the booking belongs to this user
        if (userId != null && booking.getCustomer().getUserId() != null
                && !booking.getCustomer().getUserId().equals(userId)) {
            throw new InvalidBookingException("You are not authorized to cancel this booking");
        }

        // Only allow cancellation if booking is PENDING_PAYMENT or CONFIRMED
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new InvalidBookingException("Cannot cancel booking with status: " + booking.getStatus());
        }

        LocalDateTime cancelledAt = LocalDateTime.now();

        Payment payment = paymentRepository.findByBookingId(bookingId).orElse(null);
        
        // Khi customer cancel, chỉ đổi booking status, KHÔNG tự động refund
        // Admin sẽ quyết định có refund hay không từ trang Payments
        // (Trước đây tự động refund, nhưng giờ để admin kiểm soát)

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancellationReason(cancellationReason);
        booking.setCancellationNote(cancellationNote);
        booking.setCancelledAt(cancelledAt);
        Booking savedBooking = bookingRepository.save(booking);

        TransactionLog cancelLog = new TransactionLog();
        cancelLog.setType(TransactionType.CANCELLATION);
        cancelLog.setBookingId(savedBooking.getId());
        cancelLog.setPaymentId(payment != null ? payment.getId() : null);
        cancelLog.setRefundId(null); // Chưa refund, admin sẽ refund sau
        cancelLog.setAmount(BigDecimal.ZERO); // Chưa refund
        cancelLog.setStatus(savedBooking.getStatus().name());
        
        String logMessage = "Booking cancelled by customer";
        if (cancellationReason != null) {
            logMessage += ". Reason: " + cancellationReason;
        }
        if (cancellationNote != null && !cancellationNote.trim().isEmpty()) {
            logMessage += ". Note: " + cancellationNote;
        }
        logMessage += ". Admin can refund payment separately.";
        
        cancelLog.setMessage(logMessage);
        transactionLogRepository.save(cancelLog);

        return new BookingResponse(
                savedBooking.getId(),
                savedBooking.getRoom().getId(),
                savedBooking.getRoom().getRoomNumber(),
                savedBooking.getRoom().getRoomType(),
                savedBooking.getCheckInDate(),
                savedBooking.getCheckOutDate(),
                savedBooking.getNumberOfGuests(),
                savedBooking.getTotalAmount(),
                savedBooking.getStatus(),
                savedBooking.getCustomer().getFullName(),
                savedBooking.getCustomer().getEmail(),
                savedBooking.getCreatedAt());
    }

    @Transactional
    public void deleteBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));
        bookingRepository.delete(booking);
    }

    private void validateDates(java.time.LocalDate checkInDate, java.time.LocalDate checkOutDate) {
        if (checkInDate.isAfter(checkOutDate) || checkInDate.isEqual(checkOutDate)) {
            throw new InvalidBookingException("Check-out date must be after check-in date");
        }

        // Allow today as check-in date (use isBefore instead of checking past)
        if (checkInDate.isBefore(java.time.LocalDate.now())) {
            throw new InvalidBookingException(
                    "Check-in date cannot be in the past. Today is " + java.time.LocalDate.now());
        }
    }
}
