package com.example.hotelbooking.repository;

import com.example.hotelbooking.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RefundRepository extends JpaRepository<Refund, Long> {
    List<Refund> findByBookingIdOrderByCreatedAtDesc(Long bookingId);
}
