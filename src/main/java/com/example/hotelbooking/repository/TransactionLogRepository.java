package com.example.hotelbooking.repository;

import com.example.hotelbooking.entity.TransactionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionLogRepository extends JpaRepository<TransactionLog, Long> {
    List<TransactionLog> findByBookingIdOrderByCreatedAtDesc(Long bookingId);
}
