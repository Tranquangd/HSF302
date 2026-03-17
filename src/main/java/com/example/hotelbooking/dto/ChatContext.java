package com.example.hotelbooking.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ChatContext {
    private LocalDate checkInDate;
    private LocalDate checkOutDate;
    private Integer numberOfGuests;
    private String roomType;
    /**
     * Ngân sách tối đa theo giá mỗi đêm (currency theo hệ thống, ví dụ USD).
     */
    private BigDecimal maxPricePerNight;

    public ChatContext() {
    }

    public ChatContext(LocalDate checkInDate, LocalDate checkOutDate,
                       Integer numberOfGuests, String roomType,
                       BigDecimal maxPricePerNight) {
        this.checkInDate = checkInDate;
        this.checkOutDate = checkOutDate;
        this.numberOfGuests = numberOfGuests;
        this.roomType = roomType;
        this.maxPricePerNight = maxPricePerNight;
    }

    public LocalDate getCheckInDate() {
        return checkInDate;
    }

    public void setCheckInDate(LocalDate checkInDate) {
        this.checkInDate = checkInDate;
    }

    public LocalDate getCheckOutDate() {
        return checkOutDate;
    }

    public void setCheckOutDate(LocalDate checkOutDate) {
        this.checkOutDate = checkOutDate;
    }

    public Integer getNumberOfGuests() {
        return numberOfGuests;
    }

    public void setNumberOfGuests(Integer numberOfGuests) {
        this.numberOfGuests = numberOfGuests;
    }

    public String getRoomType() {
        return roomType;
    }

    public void setRoomType(String roomType) {
        this.roomType = roomType;
    }

    public BigDecimal getMaxPricePerNight() {
        return maxPricePerNight;
    }

    public void setMaxPricePerNight(BigDecimal maxPricePerNight) {
        this.maxPricePerNight = maxPricePerNight;
    }

    // Merge with new context, keeping existing values if new ones are null
    public ChatContext merge(ChatContext other) {
        if (other == null) return this;
        
        ChatContext merged = new ChatContext();
        merged.checkInDate = other.checkInDate != null ? other.checkInDate : this.checkInDate;
        merged.checkOutDate = other.checkOutDate != null ? other.checkOutDate : this.checkOutDate;
        merged.numberOfGuests = other.numberOfGuests != null ? other.numberOfGuests : this.numberOfGuests;
        merged.roomType = other.roomType != null ? other.roomType : this.roomType;
        merged.maxPricePerNight = other.maxPricePerNight != null ? other.maxPricePerNight : this.maxPricePerNight;
        return merged;
    }
}
