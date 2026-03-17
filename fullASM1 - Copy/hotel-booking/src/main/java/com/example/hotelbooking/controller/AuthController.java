package com.example.hotelbooking.controller;

import com.example.hotelbooking.dto.LoginRequest;
import com.example.hotelbooking.entity.User;
import com.example.hotelbooking.service.AuthService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.Optional;

@Controller
public class AuthController {
    
    private final AuthService authService;
    
    public AuthController(AuthService authService) {
        this.authService = authService;
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
            session.setAttribute("user", user.get());
            session.setAttribute("userId", user.get().getUserId());
            session.setAttribute("username", user.get().getUsername());
            session.setAttribute("role", user.get().getRole());
            
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
    
    @GetMapping("/logout")
    public String logout(HttpSession session, RedirectAttributes redirectAttributes) {
        session.invalidate();
        redirectAttributes.addFlashAttribute("success", "You have been logged out successfully");
        return "redirect:/login";
    }
}
