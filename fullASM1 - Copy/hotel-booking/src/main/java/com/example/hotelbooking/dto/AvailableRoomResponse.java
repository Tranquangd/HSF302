package com.example.hotelbooking.dto;

import java.math.BigDecimal;

public class AvailableRoomResponse {
    
    private Long roomId;
    private String roomNumber;
    private String roomType;
    private BigDecimal pricePerNight;
    private Integer capacity;
    private String description;
    private BigDecimal totalPrice;
    private Integer numberOfNights;
    private String imageUrl;

    public AvailableRoomResponse() {
    }

    public AvailableRoomResponse(Long roomId, String roomNumber, String roomType, 
                                BigDecimal pricePerNight, Integer capacity, 
                                String description, BigDecimal totalPrice, Integer numberOfNights) {
        this.roomId = roomId;
        this.roomNumber = roomNumber;
        this.roomType = roomType;
        this.pricePerNight = pricePerNight;
        this.capacity = capacity;
        this.description = description;
        this.totalPrice = totalPrice;
        this.numberOfNights = numberOfNights;
    }

    public AvailableRoomResponse(Long roomId, String roomNumber, String roomType,
                                BigDecimal pricePerNight, Integer capacity,
                                String description, BigDecimal totalPrice, Integer numberOfNights,
                                String imageUrl) {
        this.roomId = roomId;
        this.roomNumber = roomNumber;
        this.roomType = roomType;
        this.pricePerNight = pricePerNight;
        this.capacity = capacity;
        this.description = description;
        this.totalPrice = totalPrice;
        this.numberOfNights = numberOfNights;
        this.imageUrl = imageUrl;
    }

    public Long getRoomId() {
        return roomId;
    }

    public void setRoomId(Long roomId) {
        this.roomId = roomId;
    }

    public String getRoomNumber() {
        return roomNumber;
    }

    public void setRoomNumber(String roomNumber) {
        this.roomNumber = roomNumber;
    }

    public String getRoomType() {
        return roomType;
    }

    public void setRoomType(String roomType) {
        this.roomType = roomType;
    }

    public BigDecimal getPricePerNight() {
        return pricePerNight;
    }

    public void setPricePerNight(BigDecimal pricePerNight) {
        this.pricePerNight = pricePerNight;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }

    public Integer getNumberOfNights() {
        return numberOfNights;
    }

    public void setNumberOfNights(Integer numberOfNights) {
        this.numberOfNights = numberOfNights;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}
