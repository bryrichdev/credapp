package dev.bryrich.credapp.config;

import dev.bryrich.credapp.user.Role;
import dev.bryrich.credapp.user.User;
import dev.bryrich.credapp.user.UserService;
import dev.bryrich.credapp.usergroup.UserGroup;
import dev.bryrich.credapp.usergroup.UserGroupRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
public class DevDataSeeder implements CommandLineRunner {

    private final UserService userService;
    private final String adminEmail;
    private final String adminPassword;
    private final String groupName;
    private final UserGroupRepository userGroups;

    public DevDataSeeder(UserService userService, UserGroupRepository userGroups,
                         @Value("${credapp.dev.admin-email}") String adminEmail,
                         @Value("${credapp.dev.admin-password}") String adminPassword,
                         @Value("${credapp.dev.admin-group-name:}") String groupName) {
        this.userService = userService;
        this.userGroups = userGroups;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.groupName = groupName;
    }

    /**
     * Optional development bootstrap. A new account requires an explicitly named group;
     * otherwise use the registration page. Existing accounts retain their own group.
     */
    @Override
    @Transactional
    public void run(String... args) {
        User existing = userService.findByEmail(adminEmail).orElse(null);
        if (existing == null) {
            if (groupName == null || groupName.isBlank()) {
                return;
            }
            UserGroup group = userGroups.save(new UserGroup(groupName));
            userService.createInGroup(adminEmail, adminPassword, "Dev Superuser", Role.SUPERUSER, group.getId());
            return;
        }
        if (existing.getRole() != Role.SUPERUSER
                && userService.countByRole(Role.SUPERUSER) == 0) {
            userService.promoteToSuperuser(existing.getId());
        }
    }
}
