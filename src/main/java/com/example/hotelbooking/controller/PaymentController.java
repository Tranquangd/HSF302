package com.example.hotelbooking.controller;

import com.example.hotelbooking.dto.PaymentRequest;
import com.example.hotelbooking.dto.PaymentResponse;
import com.example.hotelbooking.entity.Booking;
import com.example.hotelbooking.entity.User;
import com.example.hotelbooking.enums.PaymentMethod;
import com.example.hotelbooking.service.PaymentService;
import com.example.hotelbooking.service.WalletService;
import com.example.hotelbooking.service.VNPayService;
import com.example.hotelbooking.service.PayPalService;
import com.paypal.base.rest.PayPalRESTException;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.math.BigDecimal;

/**
 * Web Controller xử lý thanh toán (Payment).
 * Bao gồm: hiển thị form, xử lý thanh toán.
 */
@Controller
@RequestMapping("/payments")
public class PaymentController {
    
    private final PaymentService paymentService;
    private final VNPayService vnPayService;
    private final PayPalService payPalService;
    private final WalletService walletService;

    public PaymentController(PaymentService paymentService, VNPayService vnPayService, PayPalService payPalService, WalletService walletService) {
        this.paymentService = paymentService;
        this.vnPayService = vnPayService;
        this.payPalService = payPalService;
        this.walletService = walletService;
    }
    
    @GetMapping("/process")
    public String showPaymentForm(@RequestParam Long bookingId, 
                                  HttpSession session,
                                  RedirectAttributes redirectAttributes,
                                  Model model) {
        // Check if user is logged in
        if (session.getAttribute("user") == null) {
            redirectAttributes.addFlashAttribute("error", "Please login to make payment");
            redirectAttributes.addFlashAttribute("returnUrl", "/payments/process?bookingId=" + bookingId);
            return "redirect:/login";
        }
        
        PaymentRequest paymentRequest = new PaymentRequest();
        paymentRequest.setBookingId(bookingId);
        model.addAttribute("paymentRequest", paymentRequest);
        model.addAttribute("bookingId", bookingId);
        return "payment-form";
    }
    
    @PostMapping("/process")
    public String processPayment(@Valid @ModelAttribute("paymentRequest") PaymentRequest request,
                                BindingResult result,
                                HttpSession session,
                                HttpServletRequest httpRequest,
                                RedirectAttributes redirectAttributes,
                                Model model) {
        // Check if user is logged in
        if (session.getAttribute("user") == null) {
            redirectAttributes.addFlashAttribute("error", "Please login to make payment");
            return "redirect:/login";
        }
        
        if (result.hasErrors()) {
            return "payment-form";
        }
        
        try {
            // Nếu chọn PayPal -> redirect đến trang thanh toán PayPal sandbox
            if (request.getPaymentMethod() == PaymentMethod.PAYPAL) {
                // Tạo payment với status PENDING trước
                PaymentResponse pendingPayment = paymentService.createPendingPayment(request);
                
                // Lấy booking để lấy thông tin số tiền
                Booking booking = paymentService.getBookingById(request.getBookingId());
                BigDecimal amount = booking.getTotalAmount();
                String description = "Hotel Booking #" + booking.getId();
                
                // Tạo PayPal payment và lấy approval URL
                String paypalUrl = payPalService.createPayment(
                    booking.getId(),
                    amount,
                    "USD", // PayPal sandbox thường dùng USD
                    description
                );
                
                // Redirect đến trang thanh toán PayPal
                return "redirect:" + paypalUrl;
            }
            
            // Nếu chọn VNPAY -> redirect đến trang thanh toán VNPay sandbox
            if (request.getPaymentMethod() == PaymentMethod.VNPAY) {
                // Tạo payment với status PENDING trước
                PaymentResponse pendingPayment = paymentService.createPendingPayment(request);
                
                // Lấy booking để lấy thông tin số tiền
                Booking booking = paymentService.getBookingById(request.getBookingId());
                long amountInVND = booking.getTotalAmount().longValue();
                
                // Tạo URL thanh toán VNPay
                String clientIp = getClientIpAddress(httpRequest);
                String orderInfo = "Thanh toan don hang #" + booking.getId();
                String vnpayUrl = vnPayService.createPaymentUrl(
                    booking.getId(), 
                    amountInVND, 
                    orderInfo, 
                    clientIp
                );
                
                // Redirect đến trang thanh toán VNPay
                return "redirect:" + vnpayUrl;
            }
            
            // Nếu chọn thanh toán tiền mặt -> xử lý nội bộ như cũ
            if (request.getPaymentMethod() == PaymentMethod.CASH) {
                PaymentResponse payment = paymentService.processPayment(request);

                if (payment.getPaymentStatus().name().equals("SUCCESS")) {
                    redirectAttributes.addFlashAttribute("success", "Payment successful! Booking confirmed.");
                    return "redirect:/payments/result/" + payment.getPaymentId();
                } else {
                    model.addAttribute("error", payment.getMessage());
                    return "payment-form";
                }
            }

            // Nếu chọn ví nội bộ (E_WALLET) -> trừ tiền ví và complete ngay
            if (request.getPaymentMethod() == PaymentMethod.E_WALLET) {
                PaymentResponse payment = paymentService.processPayment(request);
                if (payment.getPaymentStatus().name().equals("SUCCESS")) {
                    // refresh session wallet balance
                    try {
                        User user = (User) session.getAttribute("user");
                        if (user != null) {
                            var wallet = walletService.getOrCreateWalletByUserId(user.getUserId());
                            session.setAttribute("walletBalance", wallet != null && wallet.getBalance() != null ? wallet.getBalance() : java.math.BigDecimal.ZERO);
                        }
                    } catch (Exception ignored) {}
                    redirectAttributes.addFlashAttribute("success", "Thanh toán bằng ví thành công! Đơn đã được xác nhận.");
                    return "redirect:/payments/result/" + payment.getPaymentId();
                } else {
                    model.addAttribute("error", payment.getMessage());
                    return "payment-form";
                }
            }

            // Các phương thức khác (CARD, BANK, ...) nếu cần có thể xử lý nội bộ
            PaymentResponse payment = paymentService.processPayment(request);

            if (payment.getPaymentStatus().name().equals("SUCCESS")) {
                redirectAttributes.addFlashAttribute("success", "Payment successful! Booking confirmed.");
                return "redirect:/payments/result/" + payment.getPaymentId();
            } else {
                model.addAttribute("error", payment.getMessage());
                return "payment-form";
            }
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return "payment-form";
        }
    }
    
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-FORWARDED-FOR");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
    
    /**
     * Hiển thị kết quả thanh toán.
     */
    @GetMapping("/result/{paymentId}")
    public String showPaymentResult(@PathVariable Long paymentId, Model model) {
        try {
            model.addAttribute("paymentId", paymentId);
            return "payment-result";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return "error";
        }
    }

    /**
     * URL trả về từ VNPay Sandbox (vnp_ReturnUrl).
     * Tại đây hệ thống kiểm tra chữ ký, trạng thái giao dịch và hoàn tất booking.
     */
    @GetMapping("/vnpay-return")
    public String handleVNPayReturn(@RequestParam java.util.Map<String, String> allParams,
                                    RedirectAttributes redirectAttributes,
                                    Model model) {
        try {
            // Kiểm tra chữ ký từ VNPay
            if (!vnPayService.validateReturnHash(allParams)) {
                redirectAttributes.addFlashAttribute("error", "Dữ liệu trả về từ VNPay không hợp lệ (sai chữ ký).");
                return "redirect:/payments/result/0";
            }

            // Lấy bookingId từ vnp_TxnRef
            String bookingIdStr = vnPayService.extractBookingId(allParams);
            Long bookingId = Long.parseLong(bookingIdStr);

            // Lấy transaction ID từ VNPay
            String vnpTransactionNo = vnPayService.extractTransactionId(allParams);
            String vnpResponseCode = allParams.getOrDefault("vnp_ResponseCode", "");

            // Kiểm tra trạng thái giao dịch VNPay
            if (!vnPayService.isPaymentSuccess(allParams)) {
                // Thanh toán thất bại -> cập nhật payment status = FAILED
                paymentService.completeVNPayPayment(bookingId, false, vnpTransactionNo, 
                    "VNPay Response Code: " + vnpResponseCode);
                
                redirectAttributes.addFlashAttribute("error", 
                    "Thanh toán VNPay thất bại. Mã lỗi: " + vnpResponseCode);
                return "redirect:/payments/result/0";
            }

            // Thanh toán thành công -> cập nhật payment status = SUCCESS
            PaymentResponse payment = paymentService.completeVNPayPayment(
                bookingId, true, vnpTransactionNo, "Thanh toán VNPay thành công");

            redirectAttributes.addFlashAttribute("success", 
                "✅ Thanh toán VNPay thành công! Đơn đã được xác nhận.");
            return "redirect:/payments/result/" + payment.getPaymentId();
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi xử lý VNPay: " + e.getMessage());
            return "redirect:/payments/result/0";
        }
    }

    /**
     * URL trả về từ PayPal sau khi khách hàng approve payment.
     */
    @GetMapping("/paypal-return")
    public String handlePayPalReturn(@RequestParam String paymentId,
                                     @RequestParam String PayerID,
                                     @RequestParam Long bookingId,
                                     RedirectAttributes redirectAttributes,
                                     Model model) {
        try {
            // Execute PayPal payment
            com.paypal.api.payments.Payment payment = payPalService.executePayment(paymentId, PayerID);
            
            // Kiểm tra kết quả
            if (payPalService.isPaymentSuccess(payment)) {
                // Thanh toán thành công
                String transactionId = payPalService.getTransactionId(payment);
                PaymentResponse paymentResponse = paymentService.completePayPalPayment(
                    bookingId, true, transactionId, "PayPal payment successful");
                
                redirectAttributes.addFlashAttribute("success", 
                    "✅ PayPal payment successful! Booking confirmed.");
                return "redirect:/payments/result/" + paymentResponse.getPaymentId();
            } else {
                // Thanh toán thất bại
                paymentService.completePayPalPayment(bookingId, false, null, 
                    "PayPal payment failed: " + payment.getState());
                
                redirectAttributes.addFlashAttribute("error", 
                    "PayPal payment failed. State: " + payment.getState());
                return "redirect:/payments/result/0";
            }
        } catch (PayPalRESTException e) {
            redirectAttributes.addFlashAttribute("error", 
                "PayPal payment error: " + e.getMessage());
            return "redirect:/payments/result/0";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", 
                "Error processing PayPal payment: " + e.getMessage());
            return "redirect:/payments/result/0";
        }
    }

    /**
     * URL khi khách hàng cancel PayPal payment.
     */
    @GetMapping("/paypal-cancel")
    public String handlePayPalCancel(@RequestParam Long bookingId,
                                     RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("error", 
            "PayPal payment was cancelled.");
        return "redirect:/bookings/" + bookingId;
    }
}
