package com.example.hotelbooking.controller;

import com.example.hotelbooking.dto.PaymentRequest;
import com.example.hotelbooking.dto.PaymentResponse;
import com.example.hotelbooking.service.PaymentService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/payments")
public class PaymentController {
    
    private final PaymentService paymentService;
    
    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
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
}
