package com.sysbehavior.platform.config;

import com.sysbehavior.platform.domain.User;
import com.sysbehavior.platform.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Override
    public void run(String... args) throws Exception {
        // Valid BCrypt hash for "admin123"
        String validHash = "$2a$10$T4ImbDRHK0L/W8o4LfRp8ObdAw.Wtp1kos8pBIG6nlPCUo1ml8jHi.";
        
        userRepository.findByUsername("admin").ifPresentOrElse(
            user -> {
                user.setPassword(validHash);
                user.setRole(User.Role.ADMIN);
                userRepository.save(user);
                System.out.println("==========================================");
                System.out.println("ADMIN CREDENTIALS RESET TO: admin / admin123");
                System.out.println("==========================================");
            },
            () -> {
                User admin = new User();
                admin.setUsername("admin");
                admin.setPassword(validHash);
                admin.setRole(User.Role.ADMIN);
                userRepository.save(admin);
                System.out.println("==========================================");
                System.out.println("ADMIN USER CREATED: admin / admin123");
                System.out.println("==========================================");
            }
        );
    }
}
