package com.example.hotelbooking.service;

import com.example.hotelbooking.dto.PaymentRequest;
import com.example.hotelbooking.dto.PaymentResponse;
import com.example.hotelbooking.entity.Booking;
import com.example.hotelbooking.entity.Payment;
import com.example.hotelbooking.entity.Refund;
import com.example.hotelbooking.entity.TransactionLog;
import com.example.hotelbooking.enums.BookingStatus;
import com.example.hotelbooking.enums.PaymentMethod;
import com.example.hotelbooking.enums.PaymentStatus;
import com.example.hotelbooking.enums.TransactionType;
import com.example.hotelbooking.exception.InvalidBookingException;
import com.example.hotelbooking.exception.PaymentException;
import com.example.hotelbooking.exception.ResourceNotFoundException;
import com.example.hotelbooking.repository.BookingRepository;
import com.example.hotelbooking.repository.PaymentRepository;
import com.example.hotelbooking.repository.TransactionLogRepository;
import com.paypal.base.rest.PayPalRESTException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PaymentService {
    
    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final EmailService emailService;
    private final RefundService refundService;
    private final TransactionLogRepository transactionLogRepository;
    private final PayPalService payPalService;
    private final WalletService walletService;
    
    public PaymentService(PaymentRepository paymentRepository,
                         BookingRepository bookingRepository,
                         EmailService emailService,
                         RefundService refundService,
                         TransactionLogRepository transactionLogRepository,
                         PayPalService payPalService,
                         WalletService walletService) {
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.emailService = emailService;
        this.refundService = refundService;
        this.transactionLogRepository = transactionLogRepository;
        this.payPalService = payPalService;
        this.walletService = walletService;
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

        // Nếu là VNPAY -> chỉ tạo payment PENDING, không complete ngay (chờ callback từ VNPay)
        if (request.getPaymentMethod() == PaymentMethod.VNPAY) {
            return new PaymentResponse(
                savedPayment.getId(),
                booking.getId(),
                savedPayment.getAmount(),
                savedPayment.getPaymentMethod(),
                PaymentStatus.PENDING,
                booking.getStatus(),
                null,
                "Payment pending - redirecting to VNPay gateway...",
                null
            );
        }

        // Nếu là ví nội bộ -> trừ tiền ví khách trước khi complete
        if (request.getPaymentMethod() == PaymentMethod.E_WALLET) {
            walletService.debitCustomerWalletForBooking(booking, savedPayment);
        }

        // Simple immediate success for card, cash, etc. (no external gateway)
        return completePaymentSuccess(savedPayment, booking, savedPayment.getPaymentMethod());
    }
    
    /**
     * Tạo payment với status PENDING (dùng cho VNPay trước khi redirect)
     */
    @Transactional
    public PaymentResponse createPendingPayment(PaymentRequest request) {
        Booking booking = bookingRepository.findById(request.getBookingId())
            .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + request.getBookingId()));

        Payment existingPayment = paymentRepository.findByBookingId(request.getBookingId()).orElse(null);

        if (existingPayment != null && existingPayment.getStatus() == PaymentStatus.SUCCESS) {
            throw new PaymentException("Payment already completed for this booking.");
        }

        Payment payment = existingPayment != null ? existingPayment : new Payment();
        payment.setBooking(booking);
        booking.setPayment(payment);
        payment.setAmount(booking.getTotalAmount());
        payment.setPaymentMethod(request.getPaymentMethod());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setFailureReason(null);

        Payment savedPayment = paymentRepository.save(payment);

        return new PaymentResponse(
            savedPayment.getId(),
            booking.getId(),
            savedPayment.getAmount(),
            savedPayment.getPaymentMethod(),
            PaymentStatus.PENDING,
            booking.getStatus(),
            null,
            "Payment pending - redirecting to VNPay gateway...",
            null
        );
    }
    
    /**
     * Hoàn tất thanh toán VNPay sau khi nhận callback từ VNPay.
     */
    @Transactional
    public PaymentResponse completeVNPayPayment(Long bookingId, boolean success, 
                                                 String transactionId, String message) {
        Booking booking = bookingRepository.findById(bookingId)
            .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));

        Payment payment = paymentRepository.findByBookingId(bookingId)
            .orElseThrow(() -> new ResourceNotFoundException("Payment not found for booking id: " + bookingId));

        if (success) {
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setTransactionId(transactionId);
            payment.setPaymentDate(LocalDateTime.now());
            payment.setFailureReason(null);
            booking.setStatus(BookingStatus.CONFIRMED);
            
            paymentRepository.save(payment);
            bookingRepository.save(booking);
            
            // Cộng tiền vào admin wallet
            try {
                walletService.creditAdminWalletFromPayment(payment, booking);
            } catch (Exception e) {
                // Log error nhưng không fail payment
                System.err.println("Lỗi khi cộng tiền vào admin wallet: " + e.getMessage());
            }
            
            emailService.sendBookingConfirmationEmail(booking);
            
            return new PaymentResponse(
                payment.getId(),
                booking.getId(),
                payment.getAmount(),
                payment.getPaymentMethod(),
                PaymentStatus.SUCCESS,
                BookingStatus.CONFIRMED,
                transactionId,
                message,
                payment.getPaymentDate()
            );
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(message);
            paymentRepository.save(payment);
            
            return new PaymentResponse(
                payment.getId(),
                booking.getId(),
                payment.getAmount(),
                payment.getPaymentMethod(),
                PaymentStatus.FAILED,
                booking.getStatus(),
                transactionId,
                message,
                null
            );
        }
    }

    private PaymentResponse completePaymentSuccess(Payment payment, Booking booking, PaymentMethod method) {
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setTransactionId(generateTransactionId());
        payment.setPaymentDate(LocalDateTime.now());

        booking.setStatus(BookingStatus.CONFIRMED);

        paymentRepository.save(payment);
        bookingRepository.save(booking);

        // Cộng tiền vào admin wallet
        try {
            walletService.creditAdminWalletFromPayment(payment, booking);
        } catch (Exception e) {
            // Log error nhưng không fail payment
            System.err.println("Lỗi khi cộng tiền vào admin wallet: " + e.getMessage());
        }

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

    @Transactional
    public PaymentResponse refundPayment(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
            .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));

        Payment payment = paymentRepository.findByBookingId(bookingId)
            .orElseThrow(() -> new ResourceNotFoundException("Payment not found for booking id: " + bookingId));

        // Only allow refund if payment was successful
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new PaymentException("Cannot refund payment with status: " + payment.getStatus() + 
                ". Only SUCCESS payments can be refunded.");
        }

        // Chỉ cho phép hoàn tiền khi khách đã hủy (booking = CANCELLED)
        if (booking.getStatus() != BookingStatus.CANCELLED) {
            throw new PaymentException("Không thể hoàn tiền khi khách chưa hủy đặt phòng. Trạng thái booking hiện tại: " + booking.getStatus());
        }

        BigDecimal paidAmount = payment.getAmount() != null ? payment.getAmount() : BigDecimal.ZERO;
        com.example.hotelbooking.entity.Refund refundRecord;
        // Tính phần trăm hoàn tiền theo rule mới (ngày thứ 2 trừ 10%, 24h cuối không hoàn)
        int refundPercentage = refundService.calculateRefundPercentage(booking, LocalDateTime.now());
        
        // Nếu payment method là PayPal, thực hiện PayPal refund trước
        if (payment.getPaymentMethod() == PaymentMethod.PAYPAL) {
            try {
                String paypalSaleId = payment.getTransactionId();
                if (paypalSaleId == null || paypalSaleId.isEmpty()) {
                    throw new PaymentException("PayPal transaction ID not found. Cannot process PayPal refund.");
                }
                
                // Thực hiện PayPal refund qua tài khoản Business
                String currency = "USD"; // PayPal sandbox thường dùng USD
                String refundNote = "Admin refund for booking #" + bookingId;
                
                com.paypal.api.payments.Refund paypalRefund = payPalService.refundPayment(
                    paypalSaleId,
                    paidAmount, // Full refund
                    currency,
                    refundNote
                );
                
                // Kiểm tra kết quả PayPal refund
                if (!payPalService.isRefundSuccess(paypalRefund)) {
                    throw new PaymentException("PayPal refund failed. State: " + 
                        (paypalRefund != null ? paypalRefund.getState() : "unknown"));
                }
                
                // PayPal refund thành công, tiếp tục tạo refund record trong hệ thống
                // Yêu cầu: admin refund thì tiền sẽ cộng vào ví khách để khách dùng đặt lại
                refundRecord = refundService.createAndCompleteRefund(
                    booking,
                    payment,
                    paidAmount,
                    refundPercentage,
                    "PayPal refund completed. Refund ID: " + payPalService.getRefundId(paypalRefund),
                    true
                );
                
                if (refundRecord.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                    // Trừ tiền từ admin wallet (vì đã nhận tiền từ PayPal trước đó)
                    try {
                        walletService.debitAdminWalletForRefund(refundRecord, booking, payment);
                    } catch (Exception e) {
                        throw new PaymentException("Lỗi khi trừ tiền từ admin wallet: " + e.getMessage(), e);
                    }
                    
                    payment.setStatus(PaymentStatus.REFUNDED);
                    payment.setFailureReason(null);
                    paymentRepository.save(payment);
                }
            } catch (PayPalRESTException e) {
                throw new PaymentException("PayPal refund error: " + e.getMessage(), e);
            }
        } else {
            // Các payment method khác (CASH, VNPAY, ...) -> hoàn tiền vào ví ảo
            refundRecord = refundService.createAndCompleteRefund(
                booking,
                payment,
                paidAmount,
                refundPercentage,
                "Admin refund request for booking #" + bookingId
            );

            if (refundRecord.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                // Trừ tiền từ admin wallet
                try {
                    walletService.debitAdminWalletForRefund(refundRecord, booking, payment);
                } catch (Exception e) {
                    throw new PaymentException("Lỗi khi trừ tiền từ admin wallet: " + e.getMessage(), e);
                }
                
                payment.setStatus(PaymentStatus.REFUNDED);
                payment.setFailureReason(null);
                paymentRepository.save(payment);
            }
        }

        TransactionLog paymentLog = new TransactionLog();
        paymentLog.setType(TransactionType.CANCELLATION);
        paymentLog.setBookingId(booking.getId());
        paymentLog.setPaymentId(payment.getId());
        paymentLog.setRefundId(refundRecord.getId());
        paymentLog.setAmount(refundRecord.getAmount());
        paymentLog.setStatus(payment.getStatus().name());
        paymentLog.setMessage("Refund executed via admin flow");
        transactionLogRepository.save(paymentLog);

        // Update booking status to CANCELLED if not already
        if (booking.getStatus() != BookingStatus.CANCELLED) {
            booking.setStatus(BookingStatus.CANCELLED);
            bookingRepository.save(booking);
        }

        // Send refund notification
        emailService.sendRefundEmail(booking, payment);

        return new PaymentResponse(
            payment.getId(),
            booking.getId(),
            payment.getAmount(),
            payment.getPaymentMethod(),
            PaymentStatus.REFUNDED,
            BookingStatus.CANCELLED,
            payment.getTransactionId(),
            "Refund processed successfully. Amount: " + payment.getAmount(),
            payment.getPaymentDate()
        );
    }

    public Booking getBookingById(Long bookingId) {
        return bookingRepository.findById(bookingId)
            .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));
    }
    
    /**
     * Hoàn tất thanh toán PayPal sau khi nhận callback từ PayPal.
     */
    @Transactional
    public PaymentResponse completePayPalPayment(Long bookingId, boolean success, 
                                                 String transactionId, String message) {
        Booking booking = bookingRepository.findById(bookingId)
            .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));

        Payment payment = paymentRepository.findByBookingId(bookingId)
            .orElseThrow(() -> new ResourceNotFoundException("Payment not found for booking id: " + bookingId));

        if (success) {
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setTransactionId(transactionId);
            payment.setPaymentDate(LocalDateTime.now());
            payment.setFailureReason(null);
            booking.setStatus(BookingStatus.CONFIRMED);
            
            paymentRepository.save(payment);
            bookingRepository.save(booking);
            
            // Cộng tiền vào admin wallet
            try {
                walletService.creditAdminWalletFromPayment(payment, booking);
            } catch (Exception e) {
                // Log error nhưng không fail payment
                System.err.println("Lỗi khi cộng tiền vào admin wallet: " + e.getMessage());
            }
            
            emailService.sendBookingConfirmationEmail(booking);
            
            return new PaymentResponse(
                payment.getId(),
                booking.getId(),
                payment.getAmount(),
                payment.getPaymentMethod(),
                PaymentStatus.SUCCESS,
                BookingStatus.CONFIRMED,
                transactionId,
                message,
                payment.getPaymentDate()
            );
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(message);
            paymentRepository.save(payment);
            
            return new PaymentResponse(
                payment.getId(),
                booking.getId(),
                payment.getAmount(),
                payment.getPaymentMethod(),
                PaymentStatus.FAILED,
                booking.getStatus(),
                transactionId,
                message,
                null
            );
        }
    }
}
