package com.example.hotelbooking.service;

import com.example.hotelbooking.entity.Booking;
import com.example.hotelbooking.entity.Payment;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    
    public void sendBookingConfirmationEmail(Booking booking) {
        System.out.println("==============================================");
        System.out.println("SENDING CONFIRMATION EMAIL");
        System.out.println("==============================================");
        System.out.println("To: " + booking.getCustomer().getEmail());
        System.out.println("Subject: Booking Confirmation - " + booking.getId());
        System.out.println("----------------------------------------------");
        System.out.println("Dear " + booking.getCustomer().getFullName() + ",");
        System.out.println();
        System.out.println("Your booking has been confirmed!");
        System.out.println();
        System.out.println("Booking Details:");
        System.out.println("  Booking ID: " + booking.getId());
        System.out.println("  Room Number: " + booking.getRoom().getRoomNumber());
        System.out.println("  Room Type: " + booking.getRoom().getRoomType());
        System.out.println("  Check-in Date: " + booking.getCheckInDate());
        System.out.println("  Check-out Date: " + booking.getCheckOutDate());
        System.out.println("  Number of Guests: " + booking.getNumberOfGuests());
        System.out.println("  Total Amount: $" + booking.getTotalAmount());
        System.out.println();
        System.out.println("Thank you for choosing our hotel!");
        System.out.println("==============================================");
    }

    public void sendRefundEmail(Booking booking, Payment payment) {
        System.out.println("==============================================");
        System.out.println("SENDING REFUND NOTIFICATION EMAIL");
        System.out.println("==============================================");
        System.out.println("To: " + booking.getCustomer().getEmail());
        System.out.println("Subject: Refund Processed - Booking #" + booking.getId());
        System.out.println("----------------------------------------------");
        System.out.println("Dear " + booking.getCustomer().getFullName() + ",");
        System.out.println();
        System.out.println("Your refund has been processed successfully!");
        System.out.println();
        System.out.println("Refund Details:");
        System.out.println("  Booking ID: " + booking.getId());
        System.out.println("  Room Number: " + booking.getRoom().getRoomNumber());
        System.out.println("  Refund Amount: $" + payment.getAmount());
        System.out.println("  Payment Method: " + payment.getPaymentMethod());
        System.out.println("  Transaction ID: " + payment.getTransactionId());
        System.out.println();
        System.out.println("The refund will be credited within 5-10 business days.");
        System.out.println("Thank you for your patience!");
        System.out.println("==============================================");
    }

    public void sendBookingCancellationEmail(Booking booking) {
        System.out.println("==============================================");
        System.out.println("SENDING CANCELLATION EMAIL");
        System.out.println("==============================================");
        System.out.println("To: " + booking.getCustomer().getEmail());
        System.out.println("Subject: Booking Cancelled - #" + booking.getId());
        System.out.println("----------------------------------------------");
        System.out.println("Dear " + booking.getCustomer().getFullName() + ",");
        System.out.println();
        System.out.println("Your booking has been cancelled.");
        System.out.println();
        System.out.println("Cancelled Booking Details:");
        System.out.println("  Booking ID: " + booking.getId());
        System.out.println("  Room Number: " + booking.getRoom().getRoomNumber());
        System.out.println("  Check-in Date: " + booking.getCheckInDate());
        System.out.println("  Check-out Date: " + booking.getCheckOutDate());
        System.out.println("  Total Amount: $" + booking.getTotalAmount());
        System.out.println();
        System.out.println("If you paid for this booking, a refund will be processed shortly.");
        System.out.println("==============================================");
    }
}
