package com.example.hotelbooking.repository;

import com.example.hotelbooking.entity.Customer;
import com.example.hotelbooking.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByCustomer(Customer customer);

    Optional<Wallet> findByCustomerUserId(String userId);
}

