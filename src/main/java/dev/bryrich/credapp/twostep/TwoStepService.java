package dev.bryrich.credapp.twostep;

import dev.bryrich.credapp.user.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Two-step sign-in with an authenticator app, plus one-time recovery codes for when the phone
 * isn't to hand.
 */
@Service
public class TwoStepService {

    static final String ISSUER = "CredCloud";
    static final int RECOVERY_CODES = 10;
    /** No 0/o or 1/l/i, so a code copied off paper can't be misread. */
    private static final String RECOVERY_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    public enum Result { ACCEPTED, ACCEPTED_RECOVERY_CODE, WRONG }

    /** What an account has set up: whether two-step is on, and recovery codes left. */
    public record Status(boolean enabled, int recoveryCodesLeft) {
    }

    /** A setup in progress: the key to show as a QR code and as text. */
    public record Setup(String uri, String key) {
    }

    private final JdbcTemplate jdbc;
    private final TwoStepCipher cipher;
    private final Clock clock;
    private final boolean requiredForAdmins;

    public TwoStepService(JdbcTemplate jdbc, TwoStepCipher cipher,
                          @Value("${credapp.two-step.required-for-admins:true}") boolean requiredForAdmins) {
        this.jdbc = jdbc;
        this.cipher = cipher;
        this.clock = Clock.systemUTC();
        this.requiredForAdmins = requiredForAdmins;
    }

    /** Admins and superusers must use two-step sign-in (unless switched off, as in dev). */
    public boolean requiredFor(Role role) {
        return requiredForAdmins && (role == Role.ADMIN || role == Role.SUPERUSER);
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(long userId) {
        return Boolean.TRUE.equals(jdbc.query(
                "SELECT enabled_at IS NOT NULL FROM user_two_step WHERE user_id = ?",
                rs -> rs.next() ? rs.getBoolean(1) : false, userId));
    }

    @Transactional(readOnly = true)
    public Status status(long userId) {
        int left = jdbc.queryForObject(
                "SELECT count(*) FROM recovery_codes WHERE user_id = ? AND used_at IS NULL", Integer.class, userId);
        return new Status(isEnabled(userId), left);
    }

    /** Which of these accounts have two-step sign-in on, for the Users page. */
    @Transactional(readOnly = true)
    public Set<Long> enabledAmong(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.queryForList(
                "SELECT user_id FROM user_two_step WHERE enabled_at IS NOT NULL AND user_id = ANY (?)",
                Long.class, (Object) userIds.toArray(Long[]::new)));
    }

    /**
     * Starts setting up, or picks up a setup already started (so reloading the page doesn't
     * invalidate a QR code that's already been scanned).
     */
    @Transactional
    public Setup setup(long userId, String email) {
        if (isEnabled(userId)) {
            throw new IllegalStateException("Two-step sign-in is already on");
        }
        byte[] stored = jdbc.query("SELECT secret FROM user_two_step WHERE user_id = ?",
                rs -> rs.next() ? rs.getBytes(1) : null, userId);
        byte[] secret;
        if (stored == null) {
            secret = Totp.newSecret();
            jdbc.update("INSERT INTO user_two_step (user_id, secret) VALUES (?, ?)", userId,
                    cipher.encrypt(userId, secret));
        } else {
            secret = cipher.decrypt(userId, stored);
        }
        return new Setup(Totp.uri(secret, email, ISSUER), group(Totp.base32(secret)));
    }

    /**
     * Turns two-step sign-in on once the first code from the app checks out.
     *
     * @return the new recovery codes, to show once; empty when the code was wrong
     */
    @Transactional
    public List<String> finishSetup(long userId, String typed) {
        byte[] stored = jdbc.query("SELECT secret FROM user_two_step WHERE user_id = ? AND enabled_at IS NULL FOR UPDATE",
                rs -> rs.next() ? rs.getBytes(1) : null, userId);
        if (stored == null) {
            return List.of();
        }
        long step = Totp.match(cipher.decrypt(userId, stored), digits(typed), clock.instant(), 0);
        if (step < 0) {
            return List.of();
        }
        jdbc.update("UPDATE user_two_step SET enabled_at = now(), last_step = ? WHERE user_id = ?", step, userId);
        return newRecoveryCodes(userId);
    }

    /** Checks a code from the app, or a recovery code, at sign-in. Each works once. */
    @Transactional
    public Result verify(long userId, String typed) {
        if (typed == null || typed.isBlank()) {
            return Result.WRONG;
        }
        var row = jdbc.query("""
                        SELECT secret, last_step FROM user_two_step
                        WHERE user_id = ? AND enabled_at IS NOT NULL FOR UPDATE""",
                rs -> rs.next() ? new Object[]{rs.getBytes(1), rs.getLong(2)} : null, userId);
        if (row == null) {
            return Result.WRONG;
        }
        String digits = digits(typed);
        if (digits.matches("\\d{6}")) {
            long step = Totp.match(cipher.decrypt(userId, (byte[]) row[0]), digits, clock.instant(), (Long) row[1]);
            if (step < 0) {
                return Result.WRONG;
            }
            jdbc.update("UPDATE user_two_step SET last_step = ? WHERE user_id = ?", step, userId);
            return Result.ACCEPTED;
        }
        int used = jdbc.update("""
                UPDATE recovery_codes SET used_at = now()
                WHERE id = (SELECT id FROM recovery_codes WHERE user_id = ? AND code_hash = ? AND used_at IS NULL LIMIT 1)""",
                userId, hash(normalizeRecoveryCode(typed)));
        return used == 1 ? Result.ACCEPTED_RECOVERY_CODE : Result.WRONG;
    }

    /** Replaces any recovery codes with a fresh set, returned once in plain text. */
    @Transactional
    public List<String> newRecoveryCodes(long userId) {
        jdbc.update("DELETE FROM recovery_codes WHERE user_id = ?", userId);
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODES; i++) {
            StringBuilder code = new StringBuilder();
            for (int c = 0; c < 10; c++) {
                code.append(RECOVERY_ALPHABET.charAt(RANDOM.nextInt(RECOVERY_ALPHABET.length())));
            }
            String plain = code.substring(0, 5) + "-" + code.substring(5);
            jdbc.update("INSERT INTO recovery_codes (user_id, code_hash) VALUES (?, ?)", userId,
                    hash(normalizeRecoveryCode(plain)));
            codes.add(plain);
        }
        return codes;
    }

    /** Turns two-step sign-in off and forgets the secret and recovery codes. */
    @Transactional
    public boolean turnOff(long userId) {
        jdbc.update("DELETE FROM recovery_codes WHERE user_id = ?", userId);
        return jdbc.update("DELETE FROM user_two_step WHERE user_id = ?", userId) > 0;
    }

    /** "123 456" and "123-456" as typed become "123456". */
    private static String digits(String typed) {
        return typed == null ? "" : typed.replaceAll("[\\s-]", "");
    }

    private static String normalizeRecoveryCode(String typed) {
        return typed.replaceAll("[\\s-]", "").toLowerCase(Locale.ROOT);
    }

    /** Recovery codes carry about 49 bits of randomness, too many to guess, so a plain hash does. */
    private static String hash(String code) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The key in blocks of four, easier to type into an app by hand. */
    private static String group(String key) {
        return key.replaceAll("(.{4})(?!$)", "$1 ");
    }
}
