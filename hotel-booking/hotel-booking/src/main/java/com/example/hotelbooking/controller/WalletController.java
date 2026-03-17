package com.example.hotelbooking.controller;

import com.example.hotelbooking.entity.User;
import com.example.hotelbooking.entity.Customer;
import com.example.hotelbooking.entity.Wallet;
import com.example.hotelbooking.entity.WalletTransaction;
import com.example.hotelbooking.service.CustomerService;
import com.example.hotelbooking.service.WalletService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;

@Controller
public class WalletController {

    private final WalletService walletService;
    private final CustomerService customerService;

    public WalletController(WalletService walletService, CustomerService customerService) {
        this.walletService = walletService;
        this.customerService = customerService;
    }

    /**
     * Trang ví cho khách hàng (đăng nhập bằng role customer).
     * Hiển thị số dư ví và lịch sử giao dịch (hoàn tiền).
     */
    @GetMapping("/wallet")
    public String viewMyWallet(HttpSession session,
                               RedirectAttributes redirectAttributes,
                               Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng đăng nhập để xem ví của bạn");
            redirectAttributes.addFlashAttribute("returnUrl", "/wallet");
            return "redirect:/login";
        }

        try {
            Wallet wallet = walletService.getOrCreateWalletByUserId(user.getUserId());
            List<WalletTransaction> transactions = walletService.getTransactionsForWallet(wallet);

            // refresh header wallet balance
            session.setAttribute("walletBalance", wallet != null && wallet.getBalance() != null ? wallet.getBalance() : BigDecimal.ZERO);

            model.addAttribute("wallet", wallet);
            model.addAttribute("transactions", transactions);
            return "wallet";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return "wallet";
        }
    }

    /**
     * Form nạp tiền vào ví (customer).
     */
    @GetMapping("/wallet/topup")
    public String showTopUpForm(HttpSession session,
                                RedirectAttributes redirectAttributes,
                                Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng đăng nhập để nạp tiền vào ví");
            redirectAttributes.addFlashAttribute("returnUrl", "/wallet/topup");
            return "redirect:/login";
        }
        model.addAttribute("amount", "");
        return "wallet-topup";
    }

    /**
     * Nạp tiền vào ví (customer).
     */
    @PostMapping("/wallet/topup")
    public String topUpWallet(@RequestParam BigDecimal amount,
                              HttpSession session,
                              RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng đăng nhập để nạp tiền vào ví");
            return "redirect:/login";
        }

        try {
            Wallet wallet = walletService.getOrCreateWalletByUserId(user.getUserId());
            walletService.topUpWallet(wallet, amount, "Customer top up");

            // refresh session balance
            Wallet refreshed = walletService.getOrCreateWalletByUserId(user.getUserId());
            session.setAttribute("walletBalance", refreshed.getBalance());

            redirectAttributes.addFlashAttribute("success", "✅ Nạp tiền thành công!");
            return "redirect:/wallet";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi nạp tiền: " + e.getMessage());
            return "redirect:/wallet/topup";
        }
    }

    /**
     * Trang ví chi tiết cho Admin/Manager xem ví của từng khách hàng.
     */
    @GetMapping("/admin/customers/{id}/wallet")
    public String viewCustomerWallet(@PathVariable Long id,
                                     HttpSession session,
                                     RedirectAttributes redirectAttributes,
                                     Model model) {
        String role = (String) session.getAttribute("role");
        if (role == null || !(role.equalsIgnoreCase("admin") || role.equalsIgnoreCase("manager"))) {
            redirectAttributes.addFlashAttribute("error", "Bạn không có quyền xem ví khách hàng.");
            return "redirect:/admin/customers";
        }

        try {
            Customer customer = customerService.getCustomerById(id);
            Wallet wallet = walletService.getOrCreateWallet(customer);
            List<WalletTransaction> transactions = walletService.getTransactionsForWallet(wallet);

            model.addAttribute("customer", customer);
            model.addAttribute("wallet", wallet);
            model.addAttribute("transactions", transactions);
            return "admin/customer-wallet";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/customers";
        }
    }
}

