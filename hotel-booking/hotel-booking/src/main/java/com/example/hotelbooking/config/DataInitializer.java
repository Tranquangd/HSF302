package com.example.hotelbooking.config;

import com.example.hotelbooking.entity.Room;
import com.example.hotelbooking.entity.User;
import com.example.hotelbooking.repository.RoomRepository;
import com.example.hotelbooking.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;

@Component
public class DataInitializer implements CommandLineRunner {
    
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    
    public DataInitializer(RoomRepository roomRepository, UserRepository userRepository) {
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
    }
    
    @Override
    public void run(String... args) {
        // Seed default users if none exist
        if (userRepository.count() == 0) {
            User admin = new User();
            admin.setUserId("admin");
            admin.setPassword("admin");
            admin.setUsername("Administrator");
            admin.setRole("admin");
            admin.setStatus("active");
            userRepository.save(admin);

            User manager = new User();
            manager.setUserId("manager1");
            manager.setPassword("123456");
            manager.setUsername("Manager One");
            manager.setRole("manager");
            manager.setStatus("active");
            userRepository.save(manager);

            User staff = new User();
            staff.setUserId("staff1");
            staff.setPassword("123456");
            staff.setUsername("Staff One");
            staff.setRole("staff");
            staff.setStatus("active");
            userRepository.save(staff);

            User customer = new User();
            customer.setUserId("customer1");
            customer.setPassword("123456");
            customer.setUsername("Customer One");
            customer.setRole("customer");
            customer.setStatus("active");
            userRepository.save(customer);

            System.out.println("Default users initialized: admin/admin, manager1/123456, staff1/123456, customer1/123456");
        }

        if (roomRepository.count() == 0) {
            Room room1 = new Room();
            room1.setRoomNumber("101");
            room1.setRoomType("Single");
            room1.setPricePerNight(new BigDecimal("50.00"));
            room1.setCapacity(1);
            room1.setDescription("Cozy single room with city view");
            room1.setAvailable(true);
            room1.setImageUrl("https://images.unsplash.com/photo-1631049307264-da0ec9d70304?w=800");

            Room room2 = new Room();
            room2.setRoomNumber("102");
            room2.setRoomType("Double");
            room2.setPricePerNight(new BigDecimal("80.00"));
            room2.setCapacity(2);
            room2.setDescription("Comfortable double room with queen bed");
            room2.setAvailable(true);
            room2.setImageUrl("https://images.unsplash.com/photo-1618773928121-c32242e63f39?w=800");

            Room room3 = new Room();
            room3.setRoomNumber("201");
            room3.setRoomType("Suite");
            room3.setPricePerNight(new BigDecimal("150.00"));
            room3.setCapacity(4);
            room3.setDescription("Luxury suite with separate living area");
            room3.setAvailable(true);
            room3.setImageUrl("https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?w=800");

            Room room4 = new Room();
            room4.setRoomNumber("202");
            room4.setRoomType("Double");
            room4.setPricePerNight(new BigDecimal("85.00"));
            room4.setCapacity(2);
            room4.setDescription("Deluxe double room with balcony");
            room4.setAvailable(true);
            room4.setImageUrl("https://images.unsplash.com/photo-1590490360182-c33d57733427?w=800");

            Room room5 = new Room();
            room5.setRoomNumber("301");
            room5.setRoomType("Family");
            room5.setPricePerNight(new BigDecimal("120.00"));
            room5.setCapacity(5);
            room5.setDescription("Spacious family room with two bedrooms");
            room5.setAvailable(true);
            room5.setImageUrl("https://images.unsplash.com/photo-1566665797739-1674de7a421a?w=800");

            roomRepository.save(room1);
            roomRepository.save(room2);
            roomRepository.save(room3);
            roomRepository.save(room4);
            roomRepository.save(room5);
            
            System.out.println("Sample rooms initialized successfully!");
        }
    }
}
