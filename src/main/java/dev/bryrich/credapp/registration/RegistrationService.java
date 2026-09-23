package dev.bryrich.credapp.registration;

import dev.bryrich.credapp.user.*;
import dev.bryrich.credapp.usergroup.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {
    private final UserService users;
    private final UserGroupRepository groups;

    public RegistrationService(UserService users, UserGroupRepository groups) {
        this.users = users;
        this.groups = groups;
    }

    @Transactional
    public User register(RegistrationForm form) {
        if (form.getRole() != Role.ADMIN && form.getRole() != Role.COORDINATOR) {
            throw new IllegalArgumentException("Choose Admin or Coordinator");
        }
        if (form.getPassword() == null || !form.getPassword().equals(form.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords must match");
        }
        if (users.existsByEmail(form.getEmail())) {
            throw new EmailAlreadyExistsException(form.getEmail());
        }
        UserGroup group;
        if (form.getRole() == Role.ADMIN) {
            if (form.getGroupName() == null || form.getGroupName().isBlank()) {
                throw new IllegalArgumentException("Enter a name for your new user group");
            }
            group = groups.save(new UserGroup(form.getGroupName()));
        } else {
            group = groups.findByJoinCode(form.getJoinCode())
                    .orElseThrow(() -> new IllegalArgumentException("That group code was not found. Ask the group's admin for their code."));
        }
        User account = users.createInGroup(form.getEmail(), form.getPassword(), form.getFullName(),
                form.getRole(), group.getId());
        if (form.getRole() == Role.COORDINATOR) {
            account.setMembershipStatus(MembershipStatus.PENDING);
            account.setEnabled(false);
        }
        return account;
    }
}
