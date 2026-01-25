package com.example.hotelbooking.service;

import com.example.hotelbooking.dto.*;
import com.example.hotelbooking.entity.Booking;
import com.example.hotelbooking.entity.Customer;
import com.example.hotelbooking.entity.Room;
import com.example.hotelbooking.enums.BookingStatus;
import com.example.hotelbooking.exception.InvalidBookingException;
import com.example.hotelbooking.exception.ResourceNotFoundException;
import com.example.hotelbooking.repository.BookingRepository;
import com.example.hotelbooking.repository.CustomerRepository;
import com.example.hotelbooking.repository.RoomRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class BookingService {
    
    private static final Logger logger = LoggerFactory.getLogger(BookingService.class);
    
    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;
    private final CustomerRepository customerRepository;
    
    public BookingService(RoomRepository roomRepository, 
                         BookingRepository bookingRepository,
                         CustomerRepository customerRepository) {
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
        this.customerRepository = customerRepository;
    }
    
    public List<AvailableRoomResponse> searchAvailableRooms(RoomSearchRequest request) {
        logger.info("=== ROOM SEARCH DEBUG ===");
        logger.info("Check-in: {}", request.getCheckInDate());
        logger.info("Check-out: {}", request.getCheckOutDate());
        logger.info("Guests: {}", request.getNumberOfGuests());
        logger.info("Room Type: {}", request.getRoomType());
        
        // Test: Check total available rooms in database
        List<Room> allAvailableRooms = roomRepository.findAllAvailableRooms();
        logger.info("Total available rooms in database: {}", allAvailableRooms.size());
        
        validateDates(request.getCheckInDate(), request.getCheckOutDate());
        
        List<Room> availableRooms = roomRepository.findAvailableRooms(
            request.getCheckInDate(),
            request.getCheckOutDate(),
            request.getNumberOfGuests(),
            request.getRoomType()
        );
        
        logger.info("Query returned {} rooms", availableRooms.size());
        if (!availableRooms.isEmpty()) {
            availableRooms.forEach(room -> 
                logger.info("Room found: {} - {} - ${}", room.getRoomNumber(), room.getRoomType(), room.getPricePerNight())
            );
        }
        
        if (availableRooms.isEmpty()) {
            logger.error("NO ROOMS FOUND - Check database and query!");
            throw new ResourceNotFoundException("No available rooms found for the selected criteria");
        }
        
        long numberOfNights = ChronoUnit.DAYS.between(
            request.getCheckInDate(), 
            request.getCheckOutDate()
        );
        
        return availableRooms.stream()
            .map(room -> {
                BigDecimal totalPrice = room.getPricePerNight()
                    .multiply(BigDecimal.valueOf(numberOfNights));
                
                return new AvailableRoomResponse(
                    room.getId(),
                    room.getRoomNumber(),
                    room.getRoomType(),
                    room.getPricePerNight(),
                    room.getCapacity(),
                    room.getDescription(),
                    totalPrice,
                    (int) numberOfNights,
                    room.getImageUrl()
                );
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
            request.getCheckOutDate()
        );
        
        if (!conflictingBookings.isEmpty()) {
            throw new InvalidBookingException("Room is already booked for the selected dates");
        }
        
        Customer customer = customerRepository.findByEmail(request.getCustomerInfo().getEmail())
            .orElseGet(() -> {
                Customer newCustomer = new Customer();
                newCustomer.setFullName(request.getCustomerInfo().getFullName());
                newCustomer.setEmail(request.getCustomerInfo().getEmail());
                newCustomer.setPhoneNumber(request.getCustomerInfo().getPhoneNumber());
                return customerRepository.save(newCustomer);
            });
        
        long numberOfNights = ChronoUnit.DAYS.between(
            request.getCheckInDate(),
            request.getCheckOutDate()
        );
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
            savedBooking.getCreatedAt()
        );
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
            booking.getCreatedAt()
        );
    }
    
    private void validateDates(java.time.LocalDate checkInDate, java.time.LocalDate checkOutDate) {
        if (checkInDate.isAfter(checkOutDate) || checkInDate.isEqual(checkOutDate)) {
            throw new InvalidBookingException("Check-out date must be after check-in date");
        }
        
        if (checkInDate.isBefore(java.time.LocalDate.now())) {
            throw new InvalidBookingException("Check-in date cannot be in the past");
        }
    }
}
