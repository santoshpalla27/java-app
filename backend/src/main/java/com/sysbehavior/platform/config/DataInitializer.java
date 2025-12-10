package com.sysbehavior.platform.config;

import com.sysbehavior.platform.domain.User;
import com.sysbehavior.platform.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.security.crypto.password.PasswordEncoder;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        // Generate hash dynamically to ensure compatibility
        String rawPassword = "admin123";
        String validHash = passwordEncoder.encode(rawPassword);
        
        userRepository.findByUsername("admin").ifPresentOrElse(
            user -> {
                user.setPassword(validHash);
                user.setRole(User.Role.ADMIN);
                userRepository.save(user);
                System.out.println("==========================================");
                System.out.println("ADMIN CREDENTIALS RESET TO: admin / " + rawPassword);
                System.out.println("GENERATED HASH: " + validHash);
                System.out.println("==========================================");
            },
            () -> {
                User admin = new User();
                admin.setUsername("admin");
                admin.setPassword(validHash);
                admin.setRole(User.Role.ADMIN);
                userRepository.save(admin);
                System.out.println("==========================================");
                System.out.println("ADMIN USER CREATED: admin / " + rawPassword);
                System.out.println("GENERATED HASH: " + validHash);
                System.out.println("==========================================");
            }
        );
    }
}
