package com.example.hotelbooking.service;

import com.paypal.api.payments.*;
import com.paypal.base.rest.APIContext;
import com.paypal.base.rest.PayPalRESTException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * PayPal Sandbox Integration Service.
 * Uses PayPal sandbox credentials - NO real money is charged.
 */
@Service
public class PayPalService {

    @Value("${paypal.client-id}")
    private String clientId;

    @Value("${paypal.client-secret}")
    private String clientSecret;

    @Value("${paypal.mode:sandbox}")
    private String mode;

    @Value("${paypal.return-url:http://localhost:8088/payments/paypal-return}")
    private String returnUrl;

    @Value("${paypal.cancel-url:http://localhost:8088/payments/paypal-cancel}")
    private String cancelUrl;

    private APIContext getApiContext() {
        return new APIContext(clientId, clientSecret, mode);
    }

    /**
     * Tạo PayPal payment và trả về approval URL để redirect khách hàng.
     */
    public String createPayment(Long bookingId, BigDecimal amount, String currency, String description) 
            throws PayPalRESTException {
        
        APIContext apiContext = getApiContext();

        // Set payer details
        Payer payer = new Payer();
        payer.setPaymentMethod("paypal");

        // Set redirect URLs
        RedirectUrls redirectUrls = new RedirectUrls();
        redirectUrls.setCancelUrl(cancelUrl + "?bookingId=" + bookingId);
        redirectUrls.setReturnUrl(returnUrl + "?bookingId=" + bookingId);

        // Set amount details
        Amount amountDetails = new Amount();
        amountDetails.setCurrency(currency);
        // PayPal requires amount with 2 decimal places
        amountDetails.setTotal(String.format("%.2f", amount.doubleValue()));

        // Set transaction details
        Transaction transaction = new Transaction();
        transaction.setAmount(amountDetails);
        transaction.setDescription(description);

        List<Transaction> transactions = new ArrayList<>();
        transactions.add(transaction);

        // Set payment details
        Payment payment = new Payment();
        payment.setIntent("sale");
        payment.setPayer(payer);
        payment.setRedirectUrls(redirectUrls);
        payment.setTransactions(transactions);

        // Create payment
        Payment createdPayment = payment.create(apiContext);

        // Get approval URL
        String approvalUrl = null;
        List<Links> links = createdPayment.getLinks();
        for (Links link : links) {
            if (link.getRel().equalsIgnoreCase("approval_url")) {
                approvalUrl = link.getHref();
                break;
            }
        }

        return approvalUrl;
    }

    /**
     * Execute PayPal payment sau khi khách hàng approve.
     */
    public Payment executePayment(String paymentId, String payerId) throws PayPalRESTException {
        APIContext apiContext = getApiContext();

        Payment payment = new Payment();
        payment.setId(paymentId);

        PaymentExecution paymentExecution = new PaymentExecution();
        paymentExecution.setPayerId(payerId);

        return payment.execute(apiContext, paymentExecution);
    }

    /**
     * Kiểm tra xem payment có thành công không.
     */
    public boolean isPaymentSuccess(Payment payment) {
        if (payment == null || payment.getState() == null) {
            return false;
        }
        return "approved".equalsIgnoreCase(payment.getState());
    }

    /**
     * Lấy transaction ID (Sale ID) từ PayPal payment.
     */
    public String getTransactionId(Payment payment) {
        if (payment == null || payment.getTransactions() == null || payment.getTransactions().isEmpty()) {
            return null;
        }
        Transaction transaction = payment.getTransactions().get(0);
        if (transaction.getRelatedResources() == null || transaction.getRelatedResources().isEmpty()) {
            return null;
        }
        RelatedResources relatedResource = transaction.getRelatedResources().get(0);
        if (relatedResource.getSale() != null) {
            return relatedResource.getSale().getId();
        }
        return payment.getId();
    }

    /**
     * Lấy Sale ID từ Payment object (dùng cho refund).
     */
    public String getSaleId(Payment payment) {
        if (payment == null || payment.getTransactions() == null || payment.getTransactions().isEmpty()) {
            return null;
        }
        Transaction transaction = payment.getTransactions().get(0);
        if (transaction.getRelatedResources() == null || transaction.getRelatedResources().isEmpty()) {
            return null;
        }
        RelatedResources relatedResource = transaction.getRelatedResources().get(0);
        if (relatedResource.getSale() != null) {
            return relatedResource.getSale().getId();
        }
        return null;
    }

    /**
     * Thực hiện PayPal refund (hoàn tiền) qua tài khoản Business.
     * 
     * @param saleId PayPal Sale ID (transaction ID từ lúc thanh toán)
     * @param amount Số tiền hoàn (null = full refund)
     * @param currency Currency (USD, VND, ...)
     * @param note Ghi chú lý do hoàn tiền
     * @return Refund object từ PayPal
     */
    public Refund refundPayment(String saleId, BigDecimal amount, String currency, String note)
            throws PayPalRESTException {
        
        APIContext apiContext = getApiContext();

        // Lấy Sale object từ Sale ID
        Sale sale = Sale.get(apiContext, saleId);

        // Tạo Refund object
        Refund refund = new Refund();

        // Nếu amount = null hoặc = 0, thì full refund
        // Nếu có amount, thì partial refund
        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            // Partial refund
            Amount refundAmount = new Amount();
            refundAmount.setCurrency(currency);
            refundAmount.setTotal(String.format("%.2f", amount.doubleValue()));
            refund.setAmount(refundAmount);
        }
        // Nếu không set amount, PayPal sẽ tự động full refund

        // Set note (optional)
        if (note != null && !note.isEmpty()) {
            refund.setDescription(note);
        }

        // Thực hiện refund
        return sale.refund(apiContext, refund);
    }

    /**
     * Kiểm tra xem refund có thành công không.
     */
    public boolean isRefundSuccess(Refund refund) {
        if (refund == null || refund.getState() == null) {
            return false;
        }
        return "completed".equalsIgnoreCase(refund.getState());
    }

    /**
     * Lấy refund ID từ PayPal refund response.
     */
    public String getRefundId(Refund refund) {
        if (refund == null) {
            return null;
        }
        return refund.getId();
    }
}
