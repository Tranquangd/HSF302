package com.example.hotelbooking.controller;

import com.example.hotelbooking.dto.CustomerLinkRequest;
import com.example.hotelbooking.entity.Customer;
import com.example.hotelbooking.entity.User;
import com.example.hotelbooking.repository.CustomerRepository;
import com.example.hotelbooking.service.WalletService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

@Controller
@RequestMapping("/customer")
public class CustomerLinkController {

    private final CustomerRepository customerRepository;
    private final WalletService walletService;

    public CustomerLinkController(CustomerRepository customerRepository, WalletService walletService) {
        this.customerRepository = customerRepository;
        this.walletService = walletService;
    }

    @GetMapping("/link")
    public String showLinkForm(HttpSession session,
                               RedirectAttributes redirectAttributes,
                               Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng đăng nhập để liên kết thông tin khách hàng.");
            redirectAttributes.addFlashAttribute("returnUrl", "/customer/link");
            return "redirect:/login";
        }

        // Đã liên kết rồi -> chuyển thẳng sang ví (hoặc returnUrl)
        if (customerRepository.findByUserId(user.getUserId()).isPresent()) {
            String returnUrl = (String) session.getAttribute("postLinkReturnUrl");
            if (returnUrl != null && !returnUrl.isBlank()) {
                session.removeAttribute("postLinkReturnUrl");
                return "redirect:" + returnUrl;
            }
            return "redirect:/wallet";
        }

        CustomerLinkRequest linkRequest = new CustomerLinkRequest();
        Object username = session.getAttribute("username");
        if (username != null) {
            linkRequest.setFullName(username.toString());
        }
        model.addAttribute("linkRequest", linkRequest);
        return "customer-link";
    }

    @PostMapping("/link")
    public String linkCustomer(@Valid @ModelAttribute("linkRequest") CustomerLinkRequest request,
                               BindingResult result,
                               HttpSession session,
                               RedirectAttributes redirectAttributes,
                               Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng đăng nhập để liên kết thông tin khách hàng.");
            redirectAttributes.addFlashAttribute("returnUrl", "/customer/link");
            return "redirect:/login";
        }

        if (result.hasErrors()) {
            return "customer-link";
        }

        String userId = user.getUserId();

        // userId đã liên kết -> tránh vi phạm unique index
        if (customerRepository.findByUserId(userId).isPresent()) {
            redirectAttributes.addFlashAttribute("info", "Tài khoản của bạn đã liên kết thông tin khách hàng rồi.");
            return "redirect:/wallet";
        }

        try {
            Customer customer = customerRepository.findByEmail(request.getEmail())
                    .orElseGet(() -> {
                        Customer c = new Customer();
                        c.setEmail(request.getEmail());
                        return c;
                    });

            // Email đã link với user khác
            if (customer.getUserId() != null && !customer.getUserId().equals(userId)) {
                model.addAttribute("error", "Email này đã được liên kết với một tài khoản khác. Vui lòng dùng email đã đặt phòng của bạn.");
                return "customer-link";
            }

            customer.setFullName(request.getFullName());
            customer.setPhoneNumber(request.getPhoneNumber());
            customer.setUserId(userId);
            if (customer.getStatus() == null || customer.getStatus().isBlank()) {
                customer.setStatus("active");
            }

            Customer saved = customerRepository.save(customer);

            // tạo ví & cập nhật số dư header
            var wallet = walletService.getOrCreateWallet(saved);
            session.setAttribute("walletBalance",
                    wallet != null && wallet.getBalance() != null ? wallet.getBalance() : BigDecimal.ZERO);

            redirectAttributes.addFlashAttribute("success",
                    "✅ Liên kết thành công! Bạn đã có thể xem ví/hoàn tiền và lịch sử giao dịch.");

            String returnUrl = (String) session.getAttribute("postLinkReturnUrl");
            if (returnUrl != null && !returnUrl.isBlank()) {
                session.removeAttribute("postLinkReturnUrl");
                return "redirect:" + returnUrl;
            }

            return "redirect:/wallet";
        } catch (Exception e) {
            model.addAttribute("error", "Liên kết thất bại: " + e.getMessage());
            return "customer-link";
        }
    }
}
