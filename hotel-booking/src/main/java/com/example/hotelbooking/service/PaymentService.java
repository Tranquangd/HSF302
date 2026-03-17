package com.example.hotelbooking.service;

import com.example.hotelbooking.dto.PaymentRequest;
import com.example.hotelbooking.dto.PaymentResponse;
import com.example.hotelbooking.entity.Booking;
import com.example.hotelbooking.entity.Payment;
import com.example.hotelbooking.enums.BookingStatus;
import com.example.hotelbooking.enums.PaymentMethod;
import com.example.hotelbooking.enums.PaymentStatus;
import com.example.hotelbooking.exception.InvalidBookingException;
import com.example.hotelbooking.exception.PaymentException;
import com.example.hotelbooking.exception.ResourceNotFoundException;
import com.example.hotelbooking.repository.BookingRepository;
import com.example.hotelbooking.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PaymentService {
    
    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final EmailService emailService;
    
    public PaymentService(PaymentRepository paymentRepository,
                         BookingRepository bookingRepository,
                         EmailService emailService) {
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.emailService = emailService;
    }
    
    // Get all payments (for admin dashboard)
    public java.util.List<Payment> getAllPayments() {
        return paymentRepository.findAll();
    }

    // Get payment by ID
    public Payment getPaymentById(Long id) {
        return paymentRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + id));
    }

    @Transactional
    public PaymentResponse processPayment(PaymentRequest request) {
        Booking booking = bookingRepository.findById(request.getBookingId())
            .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + request.getBookingId()));

        Payment existingPayment = paymentRepository.findByBookingId(request.getBookingId()).orElse(null);

        // If already paid successfully, return existing info instead of error
        if (existingPayment != null && existingPayment.getStatus() == PaymentStatus.SUCCESS) {
            return new PaymentResponse(
                existingPayment.getId(),
                booking.getId(),
                existingPayment.getAmount(),
                existingPayment.getPaymentMethod(),
                PaymentStatus.SUCCESS,
                booking.getStatus(),
                existingPayment.getTransactionId(),
                "Payment already completed for this booking.",
                existingPayment.getPaymentDate()
            );
        }

        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT && booking.getStatus() != BookingStatus.CANCELLED) {
            throw new InvalidBookingException("Booking is not in PENDING_PAYMENT status. Current status: " + booking.getStatus());
        }

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            booking.setStatus(BookingStatus.PENDING_PAYMENT);
        }

        Payment payment = existingPayment != null ? existingPayment : new Payment();
        payment.setBooking(booking);
        booking.setPayment(payment);
        payment.setAmount(booking.getTotalAmount());
        payment.setPaymentMethod(request.getPaymentMethod());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setFailureReason(null);

        Payment savedPayment = paymentRepository.save(payment);

        // Simple immediate success for both card and cash (no external gateway)
        return completePaymentSuccess(savedPayment, booking, savedPayment.getPaymentMethod());
    }

    private PaymentResponse completePaymentSuccess(Payment payment, Booking booking, PaymentMethod method) {
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setTransactionId(generateTransactionId());
        payment.setPaymentDate(LocalDateTime.now());

        booking.setStatus(BookingStatus.CONFIRMED);

        paymentRepository.save(payment);
        bookingRepository.save(booking);

        emailService.sendBookingConfirmationEmail(booking);

        return new PaymentResponse(
            payment.getId(),
            booking.getId(),
            payment.getAmount(),
            method,
            PaymentStatus.SUCCESS,
            BookingStatus.CONFIRMED,
            payment.getTransactionId(),
            "Payment processed successfully. Booking confirmed.",
            payment.getPaymentDate()
        );
    }

    private PaymentResponse completePaymentFailed(Payment payment, Booking booking, String reason) {
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason(reason);

        booking.setStatus(BookingStatus.CANCELLED);

        paymentRepository.save(payment);
        bookingRepository.save(booking);

        return new PaymentResponse(
            payment.getId(),
            booking.getId(),
            payment.getAmount(),
            payment.getPaymentMethod(),
            PaymentStatus.FAILED,
            BookingStatus.CANCELLED,
            null,
            "Payment failed: " + reason,
            null
        );
    }

    private boolean processPaymentWithExternalSystem(PaymentRequest request) {
        // Legacy gateway removed; always handled inline.
        return true;
    }
    
    private String generateTransactionId() {
        return "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
    
    public PaymentResponse getPaymentByBookingId(Long bookingId) {
        Payment payment = paymentRepository.findByBookingId(bookingId)
            .orElseThrow(() -> new ResourceNotFoundException("Payment not found for booking id: " + bookingId));
        
        return new PaymentResponse(
            payment.getId(),
            payment.getBooking().getId(),
            payment.getAmount(),
            payment.getPaymentMethod(),
            payment.getStatus(),
            payment.getBooking().getStatus(),
            payment.getTransactionId(),
            payment.getStatus() == PaymentStatus.SUCCESS ? "Payment successful" : payment.getFailureReason(),
            payment.getPaymentDate()
        );
    }

    public Booking getBookingById(Long bookingId) {
        return bookingRepository.findById(bookingId)
            .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));
    }
}
