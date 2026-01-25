package com.example.hotelbooking.service;

import com.example.hotelbooking.dto.PaymentRequest;
import com.example.hotelbooking.dto.PaymentResponse;
import com.example.hotelbooking.entity.Booking;
import com.example.hotelbooking.entity.Payment;
import com.example.hotelbooking.enums.BookingStatus;
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
    
    @Transactional
    public PaymentResponse processPayment(PaymentRequest request) {
        Booking booking = bookingRepository.findById(request.getBookingId())
            .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + request.getBookingId()));
        
        // Kiểm tra nếu đã có payment thành công cho booking này
        Payment existingPayment = paymentRepository.findByBookingId(request.getBookingId()).orElse(null);

        if (existingPayment != null && existingPayment.getStatus() == PaymentStatus.SUCCESS) {
            throw new InvalidBookingException("Payment already completed for this booking. Transaction ID: " + existingPayment.getTransactionId());
        }

        // Cho phép thanh toán lại nếu booking đang PENDING_PAYMENT hoặc đã bị CANCELLED do payment failed
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT && booking.getStatus() != BookingStatus.CANCELLED) {
            throw new InvalidBookingException("Booking is not in PENDING_PAYMENT status. Current status: " + booking.getStatus());
        }
        
        // Reset booking status về PENDING_PAYMENT nếu đang retry
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            booking.setStatus(BookingStatus.PENDING_PAYMENT);
        }

        Payment payment;
        if (existingPayment != null) {
            // Cập nhật payment hiện có thay vì tạo mới
            payment = existingPayment;
            payment.setPaymentMethod(request.getPaymentMethod());
            payment.setStatus(PaymentStatus.PENDING);
            payment.setFailureReason(null);
        } else {
            // Tạo payment mới
            payment = new Payment();
            payment.setBooking(booking);
            payment.setAmount(booking.getTotalAmount());
            payment.setPaymentMethod(request.getPaymentMethod());
            payment.setStatus(PaymentStatus.PENDING);
        }

        Payment savedPayment = paymentRepository.save(payment);
        
        boolean paymentSuccess = processPaymentWithExternalSystem(request);
        
        if (paymentSuccess) {
            savedPayment.setStatus(PaymentStatus.SUCCESS);
            savedPayment.setTransactionId(generateTransactionId());
            savedPayment.setPaymentDate(LocalDateTime.now());
            
            booking.setStatus(BookingStatus.CONFIRMED);
            
            paymentRepository.save(savedPayment);
            bookingRepository.save(booking);
            
            emailService.sendBookingConfirmationEmail(booking);
            
            return new PaymentResponse(
                savedPayment.getId(),
                booking.getId(),
                savedPayment.getAmount(),
                savedPayment.getPaymentMethod(),
                PaymentStatus.SUCCESS,
                BookingStatus.CONFIRMED,
                savedPayment.getTransactionId(),
                "Payment processed successfully. Booking confirmed.",
                savedPayment.getPaymentDate()
            );
        } else {
            String failureReason = "Payment declined by payment gateway";
            savedPayment.setStatus(PaymentStatus.FAILED);
            savedPayment.setFailureReason(failureReason);
            
            booking.setStatus(BookingStatus.CANCELLED);
            
            paymentRepository.save(savedPayment);
            bookingRepository.save(booking);
            
            return new PaymentResponse(
                savedPayment.getId(),
                booking.getId(),
                savedPayment.getAmount(),
                savedPayment.getPaymentMethod(),
                PaymentStatus.FAILED,
                BookingStatus.CANCELLED,
                null,
                "Payment failed: " + failureReason,
                null
            );
        }
    }
    
    private boolean processPaymentWithExternalSystem(PaymentRequest request) {
        try {
            Thread.sleep(1000);
            
            if (request.getCardNumber() != null && request.getCardNumber().startsWith("4")) {
                return true;
            }
            
            return Math.random() > 0.2;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentException("Payment processing was interrupted");
        }
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
}
