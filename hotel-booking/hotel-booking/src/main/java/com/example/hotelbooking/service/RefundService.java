package com.example.hotelbooking.service;

import com.example.hotelbooking.entity.Booking;
import com.example.hotelbooking.entity.Payment;
import com.example.hotelbooking.entity.Refund;
import com.example.hotelbooking.entity.TransactionLog;
import com.example.hotelbooking.service.WalletService;
import com.example.hotelbooking.enums.RefundStatus;
import com.example.hotelbooking.enums.TransactionType;
import com.example.hotelbooking.repository.RefundRepository;
import com.example.hotelbooking.repository.TransactionLogRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
public class RefundService {

    private final RefundRepository refundRepository;
    private final TransactionLogRepository transactionLogRepository;
    private final WalletService walletService;

    @Value("${refund.policy.full.hours:24}")
    private int fullRefundHours;

    @Value("${refund.policy.partial.percentage:50}")
    private int partialRefundPercentage;

    @Value("${refund.policy.checkin.hour:14}")
    private int checkInHour;

    public RefundService(RefundRepository refundRepository,
                         TransactionLogRepository transactionLogRepository,
                         WalletService walletService) {
        this.refundRepository = refundRepository;
        this.transactionLogRepository = transactionLogRepository;
        this.walletService = walletService;
    }

    /**
     * Tính phần trăm hoàn tiền theo rule cộng dồn mỗi 24 giờ:
     *
     * - Trong 24 giờ đầu tiên kể từ thời điểm check-in  -> phạt 0%  (hoàn 100%).
     * - Sau 24 giờ (qua ngày 2)                         -> phạt 10% (hoàn 90%).
     * - Sau 48 giờ (qua ngày 3)                         -> phạt 20% (hoàn 80%).
     * - Sau 72 giờ (qua ngày 4)                         -> phạt 30% (hoàn 70%).
     * - Cứ mỗi 24 giờ tăng thêm 10% phạt, cho tới khi kết thúc kỳ ở.
     * - Trong 24 giờ cuối cùng trước thời điểm check-out -> không hoàn (0%).
     */
    public int calculateRefundPercentage(Booking booking, LocalDateTime cancelledAt) {
        // Thời điểm bắt đầu kỳ ở
        LocalDateTime checkInDateTime = booking.getCheckInDate().atTime(checkInHour, 0);
        // Thời điểm kết thúc kỳ ở (dùng cùng giờ cấu hình để đơn giản)
        LocalDateTime checkOutDateTime = booking.getCheckOutDate().atTime(checkInHour, 0);

        // Nếu đã qua thời điểm check-out thì chắc chắn không hoàn
        if (!cancelledAt.isBefore(checkOutDateTime)) {
            return 0;
        }

        // Thời điểm bắt đầu "24 giờ cuối" trước khi kết thúc kỳ đặt
        LocalDateTime last24HoursStart = checkOutDateTime.minusHours(24);

        // Nếu đang trong 24 giờ cuối -> không hoàn
        if (!cancelledAt.isBefore(last24HoursStart)) {
            return 0;
        }

        // Tính số giờ đã trôi qua kể từ thời điểm check-in
        long hoursSinceCheckIn = java.time.temporal.ChronoUnit.HOURS.between(checkInDateTime, cancelledAt);

        // Nếu hủy trước thời điểm check-in -> không phạt, hoàn 100%
        if (hoursSinceCheckIn <= 0) {
            return 100;
        }

        // Mỗi 24 giờ trôi qua -> cộng thêm 10% phạt
        long fullDays = hoursSinceCheckIn / 24;
        int penaltyPercentage = (int) Math.min(fullDays * 10, 100);

        int refundPercentage = 100 - penaltyPercentage;

        // Đảm bảo không âm
        if (refundPercentage < 0) {
            refundPercentage = 0;
        }
        return refundPercentage;
    }

    public BigDecimal calculateRefundAmount(BigDecimal paidAmount, int percentage) {
        if (paidAmount == null) {
            return BigDecimal.ZERO;
        }
        if (percentage <= 0) {
            return BigDecimal.ZERO;
        }
        return paidAmount
                .multiply(BigDecimal.valueOf(percentage))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    @Transactional
    public Refund createAndCompleteRefund(Booking booking, Payment payment, BigDecimal paidAmount,
                                         int percentage, String reason) {
        return createAndCompleteRefund(booking, payment, paidAmount, percentage, reason, true);
    }

    @Transactional
    public Refund createAndCompleteRefund(Booking booking, Payment payment, BigDecimal paidAmount,
                                          int percentage, String reason, boolean creditToWallet) {
        BigDecimal refundAmount = calculateRefundAmount(paidAmount, percentage);

        Refund refund = new Refund();
        refund.setBooking(booking);
        refund.setPayment(payment);
        refund.setAmount(refundAmount);
        refund.setRefundPercentage(percentage);
        refund.setReason(reason);

        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            refund.setStatus(RefundStatus.FAILED);
            refund.setProcessedAt(LocalDateTime.now());
        } else {
            refund.setStatus(RefundStatus.COMPLETED);
            refund.setProcessedAt(LocalDateTime.now());

            if (creditToWallet) {
                // Credit refund amount to customer's wallet
                walletService.creditWalletFromRefund(refund, booking, payment);
            }
        }

        Refund savedRefund = refundRepository.save(refund);

        TransactionLog log = new TransactionLog();
        log.setType(TransactionType.REFUND);
        log.setBookingId(booking.getId());
        log.setPaymentId(payment != null ? payment.getId() : null);
        log.setRefundId(savedRefund.getId());
        log.setAmount(savedRefund.getAmount());
        log.setStatus(savedRefund.getStatus().name());
        log.setMessage(reason);
        transactionLogRepository.save(log);

        return savedRefund;
    }
}
