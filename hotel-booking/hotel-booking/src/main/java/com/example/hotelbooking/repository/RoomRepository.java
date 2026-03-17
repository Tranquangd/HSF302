package com.example.hotelbooking.repository;

import com.example.hotelbooking.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {
    
    // Simple test query to verify database connection
    @Query(value = "SELECT r.* FROM rooms r WHERE r.available = 1", nativeQuery = true)
    List<Room> findAllAvailableRooms();
    
    @Query(value = "SELECT r.* FROM rooms r WHERE r.available = 1 " +
           "AND r.capacity >= :numberOfGuests " +
           "AND (:roomType IS NULL OR :roomType = '' OR r.room_type = :roomType) " +
           "AND r.id NOT IN (" +
           "  SELECT b.room_id FROM bookings b " +
           "  WHERE b.status IN ('PENDING_PAYMENT', 'CONFIRMED') " +
           "  AND ((b.check_in_date <= :checkOutDate AND b.check_out_date >= :checkInDate))" +
           ")", nativeQuery = true)
    List<Room> findAvailableRooms(@Param("checkInDate") LocalDate checkInDate,
                                   @Param("checkOutDate") LocalDate checkOutDate,
                                   @Param("numberOfGuests") Integer numberOfGuests,
                                   @Param("roomType") String roomType);
    
    // Find rooms by capacity and type only (without date filter)
    @Query(value = "SELECT r.* FROM rooms r WHERE r.available = 1 " +
           "AND r.capacity >= :numberOfGuests " +
           "AND (:roomType IS NULL OR :roomType = '' OR r.room_type = :roomType)", 
           nativeQuery = true)
    List<Room> findRoomsByCapacityAndType(@Param("numberOfGuests") Integer numberOfGuests,
                                          @Param("roomType") String roomType);
}
