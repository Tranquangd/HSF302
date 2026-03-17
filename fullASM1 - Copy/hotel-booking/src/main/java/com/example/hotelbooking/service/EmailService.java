package com.example.hotelbooking.service;

import com.example.hotelbooking.entity.Booking;
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
}
