package dev.bryrich.credapp.user;

import dev.bryrich.credapp.usergroup.UserGroupRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class UserService {

    /** Matches the @Size(min = 12) on CreateUserRequest. */
    public static final int MIN_PASSWORD_LENGTH = 12;

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;
    private final UserGroupRepository userGroups;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, UserGroupRepository userGroups) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userGroups = userGroups;
    }

    @Transactional(readOnly = true)
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    /** Checks a password against the account as saved now, not a copy held in the session. */
    @Transactional(readOnly = true)
    public boolean passwordMatches(Long userId, String rawPassword) {
        return rawPassword != null && !rawPassword.isEmpty()
                && passwordEncoder.matches(rawPassword, findById(userId).getPasswordHash());
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
    public User createInGroup(String email, String password, String fullName, Role role, Long groupId) {
        if (groupId == null || groupId <= 0 || !userGroups.existsById(groupId)) {
            throw new IllegalArgumentException("An existing user group is required");
        }
        String normalized = User.normalizeEmail(email);
        if (userRepository.existsByEmail(normalized)) {
            throw new EmailAlreadyExistsException(normalized);
        }

        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("password must be at least 12 characters");
        }
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Password must be at most 72 bytes (use fewer characters)");
        }

        User user = new User(normalized, passwordEncoder.encode(password), groupId);
        user.setFullName(fullName);
        if (role != null) {
            user.setRole(role);
        }
        return userRepository.save(user);
    }

    // ============ your own account ============

    /**
     * Someone changing their own name, email or password from the My account page, whatever
     * their role. The current password has to match first, so a session left open on a
     * shared computer can't be used to take the account over. A blank new password keeps
     * the current one.
     */
    @Transactional
    public User updateOwnAccount(Long userId, String currentPassword, String email, String fullName,
                                 String newPassword) {
        User user = findById(userId);
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new WrongPasswordException();
        }

        // Every check runs before anything changes, so a refused save leaves the account as it was.
        boolean changesPassword = newPassword != null && !newPassword.isEmpty();
        if (changesPassword) {
            requireValidPassword(newPassword);
            if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
                throw new IllegalArgumentException("Choose a password different from your current one");
            }
        }
        String normalized = User.normalizeEmail(email);
        boolean changesEmail = !normalized.equals(user.getEmail());
        if (changesEmail && userRepository.existsByEmail(normalized)) {
            throw new EmailAlreadyExistsException(normalized);
        }

        if (changesEmail) {
            user.setEmail(normalized);
        }
        user.setFullName(fullName == null || fullName.isBlank() ? null : fullName.trim());
        if (changesPassword) {
            user.setPasswordHash(passwordEncoder.encode(newPassword));
        }
        return user;
    }

    /** The password rules: 12 characters at least, and no more than bcrypt reads (72 bytes). */
    public static void requireValidPassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Password must be at most 72 bytes (use fewer characters)");
        }
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

    @Transactional(readOnly = true)
    public Page<User> searchAs(User actor, String term, Pageable pageable) {
        return searchAs(actor, term, null, pageable);
    }

    /**
     * Accounts the actor may see. A superuser sees every user group's, optionally narrowed
     * to one; an admin only ever sees their own group's, whatever groupId says.
     */
    @Transactional(readOnly = true)
    public Page<User> searchAs(User actor, String term, Long groupId, Pageable pageable) {
        requireUserAdmin(actor);
        Long scope = actor.getRole() == Role.SUPERUSER ? groupId : actor.getUserGroupId();
        return userRepository.searchInGroup(scope, term == null ? "" : term.trim(), pageable);
    }

    /** Every user group with its account counts, for the superuser's overview. */
    @Transactional(readOnly = true)
    public List<UserGroupSummary> userGroupSummariesAs(User actor) {
        requireSuperuser(actor);
        Map<Long, long[]> counts = new HashMap<>();
        for (Object[] row : userRepository.countByUserGroup()) {
            counts.put((Long) row[0], new long[]{((Number) row[1]).longValue(), ((Number) row[2]).longValue()});
        }
        return userGroups.findAll(Sort.by("name")).stream()
                .map(group -> {
                    long[] c = counts.getOrDefault(group.getId(), new long[]{0, 0});
                    return new UserGroupSummary(group.getId(), group.getName(), group.getJoinCode(), c[0], c[1]);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public User findByIdAs(User actor, Long id) {
        requireUserAdmin(actor);
        User target = findById(id);
        requireSameGroup(actor, target);
        return target;
    }

    /** Whether `actor` may act on `target` at all, for hiding buttons the click would reject. */
    @Transactional(readOnly = true)
    public boolean canManage(User actor, User target) {
        return actor != null && target != null
                && !target.getId().equals(actor.getId())
                && (actor.getRole() == Role.SUPERUSER || actor.getUserGroupId().equals(target.getUserGroupId()))
                && actor.getRole().canManage(target.getRole());
    }

    @Transactional
    public User createAs(User actor, String email, String password, String fullName, Role role) {
        return createAs(actor, email, password, fullName, role, null);
    }

    /**
     * Creates an account. An admin's always joins their own user group. A superuser can put
     * it in any group; with no group named it goes in theirs.
     */
    @Transactional
    public User createAs(User actor, String email, String password, String fullName, Role role, Long groupId) {
        requireUserAdmin(actor);
        requireAssignable(actor, role);
        Long target = actor.getUserGroupId();
        if (groupId != null && !groupId.equals(target)) {
            requireSuperuser(actor);
            target = groupId;
        }
        return createInGroup(email, password, fullName, role, target);
    }

    @Transactional
    public User updateAs(User actor, Long targetId, String email, String fullName,
                         Role role, boolean enabled) {
        User target = loadTarget(actor, targetId);
        requireApproved(target);
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
        requireApproved(target);
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
        requireApproved(target);
        if (target.getId().equals(actor.getId())) {
            throw new UserManagementDeniedException("You cannot disable your own account");
        }
        target.setEnabled(enabled);
        return target;
    }

    @Transactional
    public void changePasswordAs(User actor, Long targetId, String password) {
        User target = loadTarget(actor, targetId);
        requireApproved(target);
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        target.setPasswordHash(passwordEncoder.encode(password));
    }

    /**
     * Checks the actor may manage how this account signs in (lift a lockout, reset two-step
     * sign-in) and returns it. Never your own: that would let you skip your own two-step.
     */
    @Transactional(readOnly = true)
    public User manageSignInAs(User actor, Long targetId) {
        User target = loadTarget(actor, targetId);
        if (target.getId().equals(actor.getId())) {
            throw new UserManagementDeniedException("You can't do that to your own account");
        }
        return target;
    }

    @Transactional
    public void decideJoinRequestAs(User actor, Long targetId, boolean approve) {
        User target = loadTarget(actor, targetId);
        if (!target.isPendingApproval()) {
            throw new UserManagementDeniedException("This account has no pending join request");
        }
        target.setMembershipStatus(approve ? MembershipStatus.APPROVED : MembershipStatus.REJECTED);
        target.setEnabled(approve);
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
        requireSameGroup(actor, target);
        boolean self = target.getId().equals(actor.getId());
        if (!self && !actor.getRole().canManage(target.getRole())) {
            throw new UserManagementDeniedException(
                    "You are not allowed to manage " + target.getRole().getLabel() + " accounts");
        }
        return target;
    }

    private void requireSuperuser(User actor) {
        if (actor == null || actor.getRole() != Role.SUPERUSER) {
            throw new UserManagementDeniedException("Only a superuser can work across user groups");
        }
    }

    private void requireSameGroup(User actor, User target) {
        if (actor.getRole() != Role.SUPERUSER && !actor.getUserGroupId().equals(target.getUserGroupId())) {
            throw new UserManagementDeniedException("You can only manage accounts in your own user group");
        }
    }

    private void requireApproved(User target) {
        if (target.getMembershipStatus() != MembershipStatus.APPROVED) {
            throw new UserManagementDeniedException("Review the join request before changing this account");
        }
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
