package com.example.hotelbooking.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

public class RoomSearchRequest {
    
    // Có thể để trống, nếu có giá trị thì sẽ kiểm tra logic ở service
    private LocalDate checkInDate;
    
    // Có thể để trống, nếu có giá trị thì sẽ kiểm tra logic ở service
    private LocalDate checkOutDate;
    
    // Có thể để trống, nếu có giá trị thì phải > 0
    @Positive(message = "Number of guests must be positive")
    private Integer numberOfGuests;
    
    private String roomType;

    // Tìm theo số phòng (cho phép nhập 1 phần, ví dụ '1' sẽ tìm được phòng 101, 201, ...)
    private String roomNumber;

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

    public String getRoomNumber() {
        return roomNumber;
    }

    public void setRoomNumber(String roomNumber) {
        this.roomNumber = roomNumber;
    }
}
