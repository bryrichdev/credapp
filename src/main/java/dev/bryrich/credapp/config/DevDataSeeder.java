package dev.bryrich.credapp.config;

import dev.bryrich.credapp.entity.enums.Role;
import dev.bryrich.credapp.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class DevDataSeeder implements CommandLineRunner {

    private final UserService userService;
    private final String adminEmail;
    private final String adminPassword;

    public DevDataSeeder(UserService userService,
                         @Value("${credapp.dev.admin-email}") String adminEmail,
                         @Value("${credapp.dev.admin-password}") String adminPassword) {
        this.userService = userService;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        if (userService.existsByEmail(adminEmail)) {
            return;
        }
        userService.create(adminEmail, adminPassword, "Dev Admin", Role.ADMIN);
    }
}