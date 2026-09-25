package dev.bryrich.credapp.passwordreset;

import dev.bryrich.credapp.mail.AccountEmails;
import dev.bryrich.credapp.user.MembershipStatus;
import dev.bryrich.credapp.user.Role;
import dev.bryrich.credapp.user.User;
import dev.bryrich.credapp.user.UserManagementDeniedException;
import dev.bryrich.credapp.user.UserRepository;
import dev.bryrich.credapp.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Forgotten passwords.
 *
 * <p>Anyone can ask from the sign-in page. Admins and superusers are emailed a reset link at
 * once. A coordinator's or read-only account's request waits for someone who manages them, an
 * admin of their group or a superuser, to approve it, and only then is the link emailed.</p>
 *
 * <p>Nothing here tells the person asking whether the email has an account. Links work once,
 * expire, and are stored only as a SHA-256 hash, so a leaked database can't be used to reset
 * anyone's password. Finishing a reset signs the account out everywhere.</p>
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    /** How long a request waits for an admin before it lapses. */
    static final Duration PENDING_FOR = Duration.ofDays(7);
    /** An approved link has to survive until the person next checks their email. */
    static final Duration APPROVED_LINK_VALID = Duration.ofHours(24);
    /** A link the person asked for themselves, and should be waiting for. */
    static final Duration SELF_SERVE_LINK_VALID = Duration.ofHours(1);
    /** Asking again this soon after a link was sent doesn't send another. */
    static final Duration RESEND_AFTER = Duration.ofMinutes(5);

    private static final SecureRandom RANDOM = new SecureRandom();

    /** A request waiting for a decision, as the Users page lists it. */
    public record PendingRequest(long id, long userId, String email, String fullName, Role role,
                                 long userGroupId, Instant requestedAt) {
        public String displayName() {
            return fullName == null || fullName.isBlank() ? email : fullName;
        }

        public String requestedAgo() {
            Duration since = Duration.between(requestedAt, Instant.now());
            if (since.toMinutes() < 1) {
                return "just now";
            }
            if (since.toHours() < 1) {
                return since.toMinutes() == 1 ? "1 minute ago" : since.toMinutes() + " minutes ago";
            }
            if (since.toDays() < 1) {
                return since.toHours() == 1 ? "1 hour ago" : since.toHours() + " hours ago";
            }
            return since.toDays() == 1 ? "1 day ago" : since.toDays() + " days ago";
        }
    }

    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final AccountEmails emails;

    public PasswordResetService(JdbcTemplate jdbc, UserRepository users, UserService userService,
                                PasswordEncoder passwordEncoder, AccountEmails emails) {
        this.jdbc = jdbc;
        this.users = users;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.emails = emails;
    }

    /** Someone on the sign-in page says they've forgotten the password for this email. */
    @Transactional
    public void request(String email, String ipAddress) {
        if (email == null || email.isBlank()) {
            return;
        }
        Optional<User> found = users.findByEmail(User.normalizeEmail(email));
        if (found.isEmpty() || !canSignIn(found.get())) {
            return;
        }
        User user = found.get();
        // One request at a time per account, even if the button is pressed twice at once.
        jdbc.queryForObject("SELECT id FROM users WHERE id = ? FOR UPDATE", Long.class, user.getId());

        if (user.getRole() == Role.ADMIN || user.getRole() == Role.SUPERUSER) {
            if (exists("status = 'SENT' AND decided_at > ?", user.getId(), ago(RESEND_AFTER))) {
                return;
            }
            cancelOpenRequests(user.getId());
            String token = newToken();
            jdbc.update("""
                    INSERT INTO password_reset_requests
                        (user_group_id, user_id, status, token_hash, requested_ip, decided_at, decided_by, link_expires_at)
                    VALUES (?, ?, 'SENT', ?, ?, now(), 'automatic', ?)""",
                    user.getUserGroupId(), user.getId(), hash(token), ipAddress, from(SELF_SERVE_LINK_VALID));
            emails.resetLink(user, token, SELF_SERVE_LINK_VALID);
            return;
        }

        if (exists("status = 'PENDING' AND requested_at > ?", user.getId(), ago(PENDING_FOR))) {
            return;
        }
        jdbc.update("""
                INSERT INTO password_reset_requests (user_group_id, user_id, status, requested_ip)
                VALUES (?, ?, 'PENDING', ?)""", user.getUserGroupId(), user.getId(), ipAddress);
        List<User> approvers = approversFor(user);
        if (approvers.isEmpty()) {
            log.warn("Password reset for {} has nobody to approve it", user.getEmail());
        }
        emails.resetRequested(approvers, user);
    }

    /** Requests the actor can decide: their own group's for an admin, every group's for a superuser. */
    @Transactional(readOnly = true)
    public List<PendingRequest> pendingFor(User actor) {
        if (actor == null || !actor.getRole().canManageUsers()) {
            return List.of();
        }
        boolean superuser = actor.getRole() == Role.SUPERUSER;
        return jdbc.query("""
                        SELECT r.id, r.user_id, u.email, u.full_name, u.role, r.user_group_id, r.requested_at
                        FROM password_reset_requests r JOIN users u ON u.id = r.user_id
                        WHERE r.status = 'PENDING' AND r.requested_at > ? AND (? OR r.user_group_id = ?)
                        ORDER BY r.requested_at""",
                        (row, i) -> new PendingRequest(row.getLong("id"), row.getLong("user_id"),
                                row.getString("email"), row.getString("full_name"),
                                Role.fromValue(row.getString("role")), row.getLong("user_group_id"),
                                row.getTimestamp("requested_at").toInstant()),
                        ago(PENDING_FOR), superuser, actor.getUserGroupId())
                .stream()
                .filter(request -> superuser || actor.getRole().canManage(request.role()))
                .toList();
    }

    /**
     * An admin or superuser approves a request, which emails the link, or declines it.
     *
     * @return the account the request was for
     */
    @Transactional
    public User decide(User actor, long requestId, boolean approve) {
        List<Long> found = jdbc.queryForList("""
                SELECT user_id FROM password_reset_requests
                WHERE id = ? AND status = 'PENDING' AND requested_at > ? FOR UPDATE""",
                Long.class, requestId, ago(PENDING_FOR));
        if (found.isEmpty()) {
            throw new UserManagementDeniedException("That reset request was already handled or has lapsed.");
        }
        User target = users.findById(found.getFirst())
                .orElseThrow(() -> new UserManagementDeniedException("That account no longer exists."));
        if (!userService.canManage(actor, target)) {
            throw new UserManagementDeniedException("You can't reset the password of " + target.getEmail() + ".");
        }
        if (!canSignIn(target)) {
            jdbc.update("UPDATE password_reset_requests SET status = 'CANCELLED' WHERE id = ?", requestId);
            throw new UserManagementDeniedException(target.getEmail() + " can't sign in, so there's nothing to reset.");
        }

        if (approve) {
            String token = newToken();
            jdbc.update("""
                    UPDATE password_reset_requests
                    SET status = 'SENT', token_hash = ?, decided_at = now(), decided_by = ?, link_expires_at = ?
                    WHERE id = ?""", hash(token), actor.getEmail(), from(APPROVED_LINK_VALID), requestId);
            emails.resetLink(target, token, APPROVED_LINK_VALID);
        } else {
            jdbc.update("""
                    UPDATE password_reset_requests SET status = 'DECLINED', decided_at = now(), decided_by = ?
                    WHERE id = ?""", actor.getEmail(), requestId);
            emails.resetDeclined(target);
        }
        return target;
    }

    /** The account a reset link is for, if the link is still good. */
    @Transactional(readOnly = true)
    public Optional<String> emailFor(String token) {
        return jdbc.queryForList("""
                SELECT u.email FROM password_reset_requests r JOIN users u ON u.id = r.user_id
                WHERE r.token_hash = ? AND r.status = 'SENT' AND r.link_expires_at > now()
                  AND u.is_enabled AND u.membership_status = 'APPROVED'""", String.class, hash(token))
                .stream().findFirst();
    }

    /**
     * Sets the new password from a reset link, uses up the link and signs the account out everywhere.
     *
     * @throws IllegalArgumentException if the password isn't acceptable
     * @throws InvalidResetLinkException if the link is wrong, used or expired
     */
    @Transactional
    public void reset(String token, String newPassword) {
        UserService.requireValidPassword(newPassword);
        List<long[]> found = jdbc.query("""
                SELECT r.id, r.user_id FROM password_reset_requests r JOIN users u ON u.id = r.user_id
                WHERE r.token_hash = ? AND r.status = 'SENT' AND r.link_expires_at > now()
                  AND u.is_enabled AND u.membership_status = 'APPROVED'
                FOR UPDATE OF r""", (row, i) -> new long[]{row.getLong(1), row.getLong(2)}, hash(token));
        if (found.isEmpty()) {
            throw new InvalidResetLinkException();
        }
        long requestId = found.getFirst()[0];
        User user = users.findById(found.getFirst()[1]).orElseThrow(InvalidResetLinkException::new);

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        users.saveAndFlush(user);
        jdbc.update("UPDATE password_reset_requests SET status = 'USED', used_at = now() WHERE id = ?", requestId);
        cancelOpenRequests(user.getId());
        jdbc.update("DELETE FROM spring_session WHERE lower(principal_name) = lower(?)", user.getEmail());
        emails.passwordChanged(user);
        log.info("Password reset for {}", user.getEmail());
    }

    private List<User> approversFor(User user) {
        List<User> admins = users.findAllByUserGroupIdAndRole(user.getUserGroupId(), Role.ADMIN).stream()
                .filter(PasswordResetService::canSignIn)
                .toList();
        if (!admins.isEmpty()) {
            return admins;
        }
        // A group with no admin left: the superusers are the only ones who can approve it.
        return users.findAllByRole(Role.SUPERUSER).stream().filter(PasswordResetService::canSignIn).toList();
    }

    private void cancelOpenRequests(long userId) {
        jdbc.update("""
                UPDATE password_reset_requests SET status = 'CANCELLED'
                WHERE user_id = ? AND status IN ('PENDING', 'SENT')""", userId);
    }

    private boolean exists(String condition, long userId, Timestamp since) {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM password_reset_requests WHERE user_id = ? AND " + condition,
                Long.class, userId, since);
        return count != null && count > 0;
    }

    private static boolean canSignIn(User user) {
        return user.isEnabled() && user.getMembershipStatus() == MembershipStatus.APPROVED;
    }

    private static Timestamp ago(Duration duration) {
        return Timestamp.from(Instant.now().minus(duration));
    }

    private static Timestamp from(Duration duration) {
        return Timestamp.from(Instant.now().plus(duration));
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
