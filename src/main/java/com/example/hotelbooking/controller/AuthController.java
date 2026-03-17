package com.example.hotelbooking.controller;

import com.example.hotelbooking.dto.LoginRequest;
import com.example.hotelbooking.entity.User;
import com.example.hotelbooking.service.AuthService;
import com.example.hotelbooking.service.WalletService;
import com.example.hotelbooking.repository.CustomerRepository;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.math.BigDecimal;
import java.util.Optional;

@Controller
public class AuthController {
    
    private final AuthService authService;
    private final WalletService walletService;
    private final CustomerRepository customerRepository;
    
    public AuthController(AuthService authService, WalletService walletService, CustomerRepository customerRepository) {
        this.authService = authService;
        this.walletService = walletService;
        this.customerRepository = customerRepository;
    }
    
    @GetMapping("/login")
    public String showLoginForm(@RequestParam(required = false) String returnUrl,
                               Model model, 
                               HttpSession session) {
        if (session.getAttribute("user") != null) {
            return "redirect:/";
        }
        model.addAttribute("loginRequest", new LoginRequest());
        if (returnUrl != null) {
            model.addAttribute("returnUrl", returnUrl);
        }
        return "login";
    }
    
    @PostMapping("/login")
    public String login(@Valid @ModelAttribute("loginRequest") LoginRequest request,
                       @RequestParam(required = false) String returnUrl,
                       BindingResult result,
                       HttpSession session,
                       RedirectAttributes redirectAttributes,
                       Model model) {
        if (result.hasErrors()) {
            if (returnUrl != null) {
                model.addAttribute("returnUrl", returnUrl);
            }
            return "login";
        }
        
        Optional<User> user = authService.authenticate(request.getUserId(), request.getPassword());
        
        if (user.isPresent()) {
            // Nếu là customer và đã có bản ghi Customer với status != active -> chặn đăng nhập
            if ("customer".equalsIgnoreCase(user.get().getRole())) {
                var customerOpt = customerRepository.findByUserId(user.get().getUserId());
                if (customerOpt.isPresent()
                        && customerOpt.get().getStatus() != null
                        && !"active".equalsIgnoreCase(customerOpt.get().getStatus())) {
                    model.addAttribute("error", "Tài khoản khách hàng của bạn đã bị khóa. Vui lòng liên hệ quản trị viên.");
                    if (returnUrl != null) {
                        model.addAttribute("returnUrl", returnUrl);
                    }
                    return "login";
                }
            }

            session.setAttribute("user", user.get());
            session.setAttribute("userId", user.get().getUserId());
            session.setAttribute("username", user.get().getUsername());
            session.setAttribute("role", user.get().getRole());
            // Wallet balance for customer header display
            try {
                if ("customer".equalsIgnoreCase(user.get().getRole())) {
                    // If chưa có customer linked -> chuyển qua trang liên kết
                    if (customerRepository.findByUserId(user.get().getUserId()).isEmpty()) {
                        redirectAttributes.addFlashAttribute("info", "Vui lòng liên kết thông tin khách hàng để sử dụng Ví và nhận hoàn tiền.");
                        // Redirect to returnUrl if present AFTER linking
                        if (returnUrl != null && !returnUrl.isEmpty()) {
                            session.setAttribute("postLinkReturnUrl", returnUrl);
                        }
                        return "redirect:/customer/link";
                    }

                    var wallet = walletService.getOrCreateWalletByUserId(user.get().getUserId());
                    session.setAttribute("walletBalance", wallet != null && wallet.getBalance() != null ? wallet.getBalance() : BigDecimal.ZERO);
                } else {
                    session.setAttribute("walletBalance", BigDecimal.ZERO);
                }
            } catch (Exception ignored) {
                session.setAttribute("walletBalance", BigDecimal.ZERO);
            }

            // Sync Spring Security authentication for web security checks
            var authority = new SimpleGrantedAuthority("ROLE_" + user.get().getRole().toUpperCase());
            var authentication = new UsernamePasswordAuthenticationToken(user.get().getUserId(), null, java.util.List.of(authority));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, SecurityContextHolder.getContext());

            redirectAttributes.addFlashAttribute("success", "Welcome, " + user.get().getUsername() + "!");
            
            // Redirect to return URL if present, otherwise go to home
            if (returnUrl != null && !returnUrl.isEmpty()) {
                return "redirect:" + returnUrl;
            }
            return "redirect:/";
        } else {
            model.addAttribute("error", "Invalid user ID or password, or account is not active");
            if (returnUrl != null) {
                model.addAttribute("returnUrl", returnUrl);
            }
            return "login";
        }
    }
    
    // ==================== ĐĂNG KÝ TÀI KHOẢN (REGISTER) ====================

    /**
     * Hiển thị form đăng ký tài khoản mới.
     * Nếu đã đăng nhập thì chuyển về trang chủ.
     */
    @GetMapping("/register")
    public String showRegisterForm(HttpSession session, Model model) {
        // Nếu đã đăng nhập thì không cần đăng ký
        if (session.getAttribute("user") != null) {
            return "redirect:/";
        }
        model.addAttribute("registerRequest", new com.example.hotelbooking.dto.RegisterRequest());
        return "register";
    }

    /**
     * Xử lý đăng ký tài khoản mới.
     * Kiểm tra: mật khẩu khớp, userId chưa tồn tại.
     * Tạo user với role "customer" và status "active".
     */
    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("registerRequest") com.example.hotelbooking.dto.RegisterRequest request,
                           BindingResult result,
                           RedirectAttributes redirectAttributes,
                           Model model) {
        // Kiểm tra lỗi validation
        if (result.hasErrors()) {
            return "register";
        }

        // Kiểm tra mật khẩu xác nhận
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            model.addAttribute("error", "Mật khẩu xác nhận không khớp.");
            return "register";
        }

        try {
            // Đăng ký tài khoản mới
            authService.register(request.getUserId(), request.getPassword(), request.getUsername());
            redirectAttributes.addFlashAttribute("success",
                    "Đăng ký thành công! Vui lòng đăng nhập với tài khoản vừa tạo.");
            return "redirect:/login";
        } catch (IllegalArgumentException e) {
            // UserId đã tồn tại
            model.addAttribute("error", e.getMessage());
            return "register";
        } catch (Exception e) {
            model.addAttribute("error", "Đăng ký thất bại: " + e.getMessage());
            return "register";
        }
    }

    // ==================== ĐĂNG XUẤT (LOGOUT) ====================

    /**
     * Đăng xuất: xóa session và Spring Security context.
     */
    @GetMapping("/logout")
    public String logout(HttpSession session, RedirectAttributes redirectAttributes) {
        session.invalidate();
        SecurityContextHolder.clearContext();
        redirectAttributes.addFlashAttribute("success", "Bạn đã đăng xuất thành công!");
        return "redirect:/login";
    }
}
