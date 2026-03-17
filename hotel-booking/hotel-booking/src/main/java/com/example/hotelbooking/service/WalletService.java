package com.example.hotelbooking.service;

import com.example.hotelbooking.entity.Booking;
import com.example.hotelbooking.entity.Customer;
import com.example.hotelbooking.entity.Payment;
import com.example.hotelbooking.entity.Refund;
import com.example.hotelbooking.entity.Wallet;
import com.example.hotelbooking.entity.WalletTransaction;
import com.example.hotelbooking.exception.PaymentException;
import com.example.hotelbooking.exception.ResourceNotFoundException;
import com.example.hotelbooking.repository.CustomerRepository;
import com.example.hotelbooking.repository.WalletRepository;
import com.example.hotelbooking.repository.WalletTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final CustomerRepository customerRepository;

    public WalletService(WalletRepository walletRepository,
                         WalletTransactionRepository walletTransactionRepository,
                         CustomerRepository customerRepository) {
        this.walletRepository = walletRepository;
        this.walletTransactionRepository = walletTransactionRepository;
        this.customerRepository = customerRepository;
    }

    @Transactional
    public Wallet getOrCreateWallet(Customer customer) {
        return walletRepository.findByCustomer(customer)
                .orElseGet(() -> {
                    Wallet wallet = new Wallet();
                    wallet.setCustomer(customer);
                    wallet.setBalance(BigDecimal.ZERO);
                    return walletRepository.save(wallet);
                });
    }

    public Wallet getWalletByUserId(String userId) {
        return walletRepository.findByCustomerUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found for userId: " + userId));
    }

    public List<WalletTransaction> getTransactionsForWallet(Wallet wallet) {
        return walletTransactionRepository.findByWalletOrderByCreatedAtDesc(wallet);
    }

    @Transactional
    public Wallet creditWalletFromRefund(Refund refund, Booking booking, Payment payment) {
        if (refund == null || refund.getAmount() == null || refund.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        Customer customer = booking.getCustomer();
        Wallet wallet = getOrCreateWallet(customer);

        BigDecimal newBalance = wallet.getBalance().add(refund.getAmount());
        wallet.setBalance(newBalance);
        Wallet savedWallet = walletRepository.save(wallet);

        WalletTransaction tx = new WalletTransaction();
        tx.setWallet(savedWallet);
        tx.setType(WalletTransaction.Type.CREDIT);
        tx.setAmount(refund.getAmount());
        tx.setDescription("Refund for booking #" + booking.getId());
        tx.setBookingId(booking.getId());
        tx.setPaymentId(payment != null ? payment.getId() : null);
        tx.setRefundId(refund.getId());
        walletTransactionRepository.save(tx);

        return savedWallet;
    }

    public Wallet getOrCreateWalletByUserId(String userId) {
        Customer customer = customerRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found for userId: " + userId));
        return getOrCreateWallet(customer);
    }

    /**
     * Nạp tiền vào ví khách (top-up).
     */
    @Transactional
    public Wallet topUpWallet(Wallet wallet, BigDecimal amount, String note) {
        if (wallet == null) {
            throw new IllegalArgumentException("Wallet is required");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be > 0");
        }

        BigDecimal newBalance = wallet.getBalance().add(amount);
        wallet.setBalance(newBalance);
        Wallet savedWallet = walletRepository.save(wallet);

        WalletTransaction tx = new WalletTransaction();
        tx.setWallet(savedWallet);
        tx.setType(WalletTransaction.Type.CREDIT);
        tx.setAmount(amount);
        tx.setDescription(note != null ? note : "Top up wallet");
        walletTransactionRepository.save(tx);

        return savedWallet;
    }

    /**
     * Trừ tiền ví khách khi thanh toán booking bằng ví.
     * Nếu ví không đủ tiền -> throw PaymentException.
     */
    @Transactional
    public Wallet debitCustomerWalletForBooking(Booking booking, Payment payment) {
        if (booking == null || booking.getCustomer() == null) {
            throw new PaymentException("Booking/customer not found for wallet payment");
        }

        Wallet wallet = getOrCreateWallet(booking.getCustomer());
        BigDecimal amount = booking.getTotalAmount() != null ? booking.getTotalAmount() : BigDecimal.ZERO;

        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new PaymentException("Số dư ví không đủ. Số dư: " + wallet.getBalance() + ", Cần: " + amount + ". Vui lòng nạp thêm tiền.");
        }

        wallet.setBalance(wallet.getBalance().subtract(amount));
        Wallet savedWallet = walletRepository.save(wallet);

        WalletTransaction tx = new WalletTransaction();
        tx.setWallet(savedWallet);
        tx.setType(WalletTransaction.Type.DEBIT);
        tx.setAmount(amount);
        tx.setDescription("Thanh toán booking #" + booking.getId() + " bằng ví");
        tx.setBookingId(booking.getId());
        tx.setPaymentId(payment != null ? payment.getId() : null);
        walletTransactionRepository.save(tx);

        return savedWallet;
    }

    /**
     * Lấy hoặc tạo admin wallet (customer với email "admin@hotel.com")
     */
    @Transactional
    public Wallet getOrCreateAdminWallet() {
        Customer adminCustomer = customerRepository.findByEmail("admin@hotel.com")
                .orElseGet(() -> {
                    Customer newAdmin = new Customer();
                    newAdmin.setFullName("Admin Hotel");
                    newAdmin.setEmail("admin@hotel.com");
                    newAdmin.setPhoneNumber("0000000000");
                    newAdmin.setStatus("active");
                    return customerRepository.save(newAdmin);
                });
        return getOrCreateWallet(adminCustomer);
    }

    /**
     * Cộng tiền vào admin wallet khi payment thành công
     */
    @Transactional
    public Wallet creditAdminWalletFromPayment(Payment payment, Booking booking) {
        if (payment == null || payment.getAmount() == null || payment.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        Wallet adminWallet = getOrCreateAdminWallet();
        BigDecimal newBalance = adminWallet.getBalance().add(payment.getAmount());
        adminWallet.setBalance(newBalance);
        Wallet savedWallet = walletRepository.save(adminWallet);

        // Tạo transaction log cho admin wallet
        WalletTransaction tx = new WalletTransaction();
        tx.setWallet(savedWallet);
        tx.setType(WalletTransaction.Type.CREDIT);
        tx.setAmount(payment.getAmount());
        tx.setDescription("Payment từ booking #" + booking.getId() + " - Khách: " + booking.getCustomer().getFullName());
        tx.setBookingId(booking.getId());
        tx.setPaymentId(payment.getId());
        walletTransactionRepository.save(tx);

        return savedWallet;
    }

    /**
     * Trừ tiền từ admin wallet khi refund cho khách
     * Nếu admin wallet không đủ tiền, vẫn cho phép refund nhưng sẽ có số dư âm
     */
    @Transactional
    public Wallet debitAdminWalletForRefund(Refund refund, Booking booking, Payment payment) {
        if (refund == null || refund.getAmount() == null || refund.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        Wallet adminWallet = getOrCreateAdminWallet();
        
        // Trừ tiền từ admin wallet (cho phép số dư âm nếu cần)
        BigDecimal newBalance = adminWallet.getBalance().subtract(refund.getAmount());
        adminWallet.setBalance(newBalance);
        Wallet savedWallet = walletRepository.save(adminWallet);

        // Tạo transaction log cho admin wallet
        WalletTransaction tx = new WalletTransaction();
        tx.setWallet(savedWallet);
        tx.setType(WalletTransaction.Type.DEBIT);
        tx.setAmount(refund.getAmount());
        String description = "Refund cho booking #" + booking.getId() + " - Khách: " + booking.getCustomer().getFullName();
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            description += " (Số dư âm: " + newBalance + ")";
        }
        tx.setDescription(description);
        tx.setBookingId(booking.getId());
        tx.setPaymentId(payment != null ? payment.getId() : null);
        tx.setRefundId(refund.getId());
        walletTransactionRepository.save(tx);

        return savedWallet;
    }
}

