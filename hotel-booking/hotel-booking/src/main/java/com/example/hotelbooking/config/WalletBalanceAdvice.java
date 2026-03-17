package com.example.hotelbooking.config;

import com.example.hotelbooking.entity.User;
import com.example.hotelbooking.service.WalletService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.math.BigDecimal;

/**
 * Luôn refresh số dư ví cho customer để header hiển thị đúng ngay sau refund/topup/payment.
 */
@ControllerAdvice(annotations = Controller.class)
public class WalletBalanceAdvice {

    private final WalletService walletService;

    public WalletBalanceAdvice(WalletService walletService) {
        this.walletService = walletService;
    }

    @ModelAttribute
    public void refreshWalletBalance(HttpSession session) {
        if (session == null) return;

        Object userObj = session.getAttribute("user");
        if (!(userObj instanceof User user)) {
            return;
        }

        try {
            Object roleObj = session.getAttribute("role");
            String role = roleObj != null ? roleObj.toString() : null;
            if (role != null && role.equalsIgnoreCase("customer")) {
                var wallet = walletService.getOrCreateWalletByUserId(user.getUserId());
                session.setAttribute("walletBalance", wallet != null && wallet.getBalance() != null ? wallet.getBalance() : BigDecimal.ZERO);
            }
        } catch (Exception ignored) {
            // do not block rendering
        }
    }
}

