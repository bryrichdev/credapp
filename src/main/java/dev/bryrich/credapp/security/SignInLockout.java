package dev.bryrich.credapp.security;

import dev.bryrich.credapp.mail.AccountEmails;
import dev.bryrich.credapp.twostep.TwoStepService;
import dev.bryrich.credapp.user.User;
import dev.bryrich.credapp.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * Locks an account for a while after too many wrong passwords or two-step codes in a row, so
 * nobody can keep guessing. The count starts over after a quiet spell, when the lock is set,
 * and after a complete sign-in.
 *
 * Only wrong guesses at real accounts count; an unknown email changes nothing.
 */
@Component
public class SignInLockout {

    public static final int MAX_FAILURES = 5;
    public static final Duration LOCK = Duration.ofMinutes(15);

    private static final Logger log = LoggerFactory.getLogger(SignInLockout.class);

    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final TwoStepService twoStep;
    private final AccountEmails emails;

    public SignInLockout(JdbcTemplate jdbc, UserRepository users, TwoStepService twoStep, AccountEmails emails) {
        this.jdbc = jdbc;
        this.users = users;
        this.twoStep = twoStep;
        this.emails = emails;
    }

    @EventListener
    public void wrongPassword(AuthenticationFailureBadCredentialsEvent event) {
        String email = event.getAuthentication().getName();
        if (email != null && !email.isBlank()) {
            users.findByEmail(User.normalizeEmail(email)).ifPresent(user -> failed(user.getId()));
        }
    }

    /**
     * A right password clears the count, unless a two-step code is still to come: then only
     * the right code does, or someone with the password could guess codes indefinitely.
     */
    @EventListener
    public void rightPassword(AuthenticationSuccessEvent event) {
        if (event.getAuthentication().getPrincipal() instanceof CredAppUserDetails principal) {
            long id = principal.getUser().getId();
            if (!twoStep.isEnabled(id)) {
                succeeded(id);
            }
        }
    }

    /**
     * Counts one wrong password or code.
     *
     * @return true when this one locked the account
     */
    @Transactional
    public boolean failed(long userId) {
        Integer count = jdbc.query("""
                        UPDATE users SET
                            failed_sign_ins = CASE WHEN last_failed_sign_in > now() - make_interval(secs => ?)
                                                   THEN failed_sign_ins + 1 ELSE 1 END,
                            last_failed_sign_in = now()
                        WHERE id = ?
                        RETURNING failed_sign_ins""",
                rs -> rs.next() ? rs.getInt(1) : null, LOCK.toSeconds(), userId);
        if (count == null || count < MAX_FAILURES) {
            return false;
        }
        jdbc.update("""
                UPDATE users SET locked_until = now() + make_interval(secs => ?), failed_sign_ins = 0
                WHERE id = ?""", LOCK.toSeconds(), userId);
        log.warn("Locked account {} for {} after {} failed sign-ins", userId, LOCK, count);
        users.findById(userId).ifPresent(user -> emails.accountLocked(user, MAX_FAILURES, LOCK));
        return true;
    }

    /** A complete sign-in: the count starts over. */
    @Transactional
    public void succeeded(long userId) {
        jdbc.update("UPDATE users SET failed_sign_ins = 0 WHERE id = ? AND failed_sign_ins <> 0", userId);
    }

    /** Lifts a lock early, from the Users page. */
    @Transactional
    public void unlock(long userId) {
        jdbc.update("UPDATE users SET locked_until = NULL, failed_sign_ins = 0 WHERE id = ?", userId);
    }
}
