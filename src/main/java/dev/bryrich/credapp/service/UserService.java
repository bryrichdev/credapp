package dev.bryrich.credapp.service;

import dev.bryrich.credapp.entity.enums.Role;
import dev.bryrich.credapp.entity.User;
import dev.bryrich.credapp.exception.EmailAlreadyExistsException;
import dev.bryrich.credapp.exception.UserManagementDeniedException;
import dev.bryrich.credapp.exception.UserNotFoundException;
import dev.bryrich.credapp.repository.UserRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    /** Matches the @Size(min = 12) on CreateUserRequest. */
    public static final int MIN_PASSWORD_LENGTH = 12;

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,  PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(User.normalizeEmail(email));
    }

    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(User.normalizeEmail(email));
    }

    @Transactional(readOnly = true)
    public List<User> findAllByRole(Role role) {
        return userRepository.findAllByRole(role);
    }

    @Transactional
    public User create(String email, String password, String fullName, Role role) {
        String normalized = User.normalizeEmail(email);
        if (userRepository.existsByEmail(normalized)) {
            throw new EmailAlreadyExistsException(normalized);
        }

        if (password == null || password.length() < 10) {
            throw new IllegalArgumentException("password must be at least 12 characters");
        }

        User user = new User(normalized, passwordEncoder.encode(password));
        user.setFullName(fullName);
        if (role != null) {
            user.setRole(role);
        }
        return userRepository.save(user);
    }

    // ============ account administration ============
    //
    // Every method below takes the signed-in account as `actor` and checks what that
    // account is allowed to do before touching anything. Role.canManage holds the rule:
    // a superuser manages everyone, an admin manages coordinators and read-only accounts.

    @Transactional(readOnly = true)
    public Page<User> findAll(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<User> search(String term, Pageable pageable) {
        return userRepository.findByEmailContainingIgnoreCaseOrFullNameContainingIgnoreCase(
                term, term, pageable);
    }

    @Transactional(readOnly = true)
    public long countByRole(Role role) {
        return userRepository.countByRole(role);
    }

    /** Whether `actor` may act on `target` at all, for hiding buttons the click would reject. */
    @Transactional(readOnly = true)
    public boolean canManage(User actor, User target) {
        return actor != null && target != null
                && !target.getId().equals(actor.getId())
                && actor.getRole().canManage(target.getRole());
    }

    @Transactional
    public User createAs(User actor, String email, String password, String fullName, Role role) {
        requireUserAdmin(actor);
        requireAssignable(actor, role);
        return create(email, password, fullName, role);
    }

    @Transactional
    public User updateAs(User actor, Long targetId, String email, String fullName,
                         Role role, boolean enabled) {
        User target = loadTarget(actor, targetId);
        boolean self = target.getId().equals(actor.getId());

        if (role != null && role != target.getRole()) {
            if (self) {
                throw new UserManagementDeniedException("You cannot change your own role");
            }
            requireAssignable(actor, role);
            requireNotLastSuperuser(target, role);
            target.setRole(role);
        }

        if (enabled != target.isEnabled()) {
            if (self) {
                throw new UserManagementDeniedException("You cannot disable your own account");
            }
            target.setEnabled(enabled);
        }

        String normalized = User.normalizeEmail(email);
        if (!normalized.equals(target.getEmail())) {
            if (userRepository.existsByEmail(normalized)) {
                throw new EmailAlreadyExistsException(normalized);
            }
            target.setEmail(normalized);
        }

        target.setFullName(fullName);
        return target;
    }

    /** Promote or demote in one step, for the role control on the user list. */
    @Transactional
    public User changeRoleAs(User actor, Long targetId, Role role) {
        User target = loadTarget(actor, targetId);
        if (target.getId().equals(actor.getId())) {
            throw new UserManagementDeniedException("You cannot change your own role");
        }
        requireAssignable(actor, role);
        requireNotLastSuperuser(target, role);
        target.setRole(role);
        return target;
    }

    @Transactional
    public User setEnabledAs(User actor, Long targetId, boolean enabled) {
        User target = loadTarget(actor, targetId);
        if (target.getId().equals(actor.getId())) {
            throw new UserManagementDeniedException("You cannot disable your own account");
        }
        target.setEnabled(enabled);
        return target;
    }

    @Transactional
    public void changePasswordAs(User actor, Long targetId, String password) {
        User target = loadTarget(actor, targetId);
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        target.setPasswordHash(passwordEncoder.encode(password));
    }

    @Transactional
    public void deleteAs(User actor, Long targetId) {
        User target = loadTarget(actor, targetId);
        if (target.getId().equals(actor.getId())) {
            throw new UserManagementDeniedException("You cannot delete your own account");
        }
        requireNotLastSuperuser(target, null);
        userRepository.delete(target);
    }

    /**
     * Bootstrap only: promotes an account without an actor check, for the seeder to use
     * when a database predates the superuser tier and nobody holds it yet.
     */
    @Transactional
    public User promoteToSuperuser(Long id) {
        User user = findById(id);
        user.setRole(Role.SUPERUSER);
        return user;
    }

    // ----- guards -----

    private void requireUserAdmin(User actor) {
        if (actor == null || !actor.getRole().canManageUsers()) {
            throw new UserManagementDeniedException("You are not allowed to manage accounts");
        }
    }

    /**
     * Loads the target and checks the actor may act on it. Acting on yourself is allowed
     * here so you can fix your own name or password; the callers block the dangerous
     * self-service cases individually.
     */
    private User loadTarget(User actor, Long targetId) {
        requireUserAdmin(actor);
        User target = findById(targetId);
        boolean self = target.getId().equals(actor.getId());
        if (!self && !actor.getRole().canManage(target.getRole())) {
            throw new UserManagementDeniedException(
                    "You are not allowed to manage " + target.getRole().getLabel() + " accounts");
        }
        return target;
    }

    private void requireAssignable(User actor, Role role) {
        if (role != null && !actor.getRole().assignableRoles().contains(role)) {
            throw new UserManagementDeniedException(
                    "You are not allowed to assign the " + role.getLabel() + " role");
        }
    }

    /** Stops the last superuser being deleted or demoted, which would lock everyone out. */
    private void requireNotLastSuperuser(User target, Role newRole) {
        if (target.getRole() == Role.SUPERUSER
                && newRole != Role.SUPERUSER
                && userRepository.countByRole(Role.SUPERUSER) <= 1) {
            throw new UserManagementDeniedException("The last superuser cannot be removed or demoted");
        }
    }
}
