package dev.bryrich.credapp.config;

import dev.bryrich.credapp.entity.User;
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

    /**
     * Makes sure there is always one superuser to sign in as. If the dev account already
     * exists from before the superuser tier was added, it gets promoted rather than
     * duplicated — an existing password is left alone.
     */
    @Override
    public void run(String... args) {
        User existing = userService.findByEmail(adminEmail).orElse(null);
        if (existing == null) {
            userService.create(adminEmail, adminPassword, "Dev Superuser", Role.SUPERUSER);
            return;
        }
        if (existing.getRole() != Role.SUPERUSER
                && userService.countByRole(Role.SUPERUSER) == 0) {
            userService.promoteToSuperuser(existing.getId());
        }
    }
}