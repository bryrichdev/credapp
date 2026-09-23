package dev.bryrich.credapp.caqh;

import dev.bryrich.credapp.provider.ProviderNotFoundException;
import dev.bryrich.credapp.security.CredAppUserDetails;
import dev.bryrich.credapp.ssn.SsnConverter;
import dev.bryrich.credapp.user.Role;
import dev.bryrich.credapp.user.User;
import dev.bryrich.credapp.usergroup.CurrentUserGroupResolver;
import dev.bryrich.credapp.usergroup.ViewedGroup;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Shares the SSN AES-256-GCM cipher and key. The ciphertext is intentionally absent
 * from Provider, its DTOs and exports; only an explicit, audited reveal decrypts it.
 * Every SQL operation is scoped to the authenticated (or superuser-viewed) workspace.
 */
@Service
public class CaqhPasswordService {
    private final JdbcTemplate jdbc;
    private final SsnConverter encryption;

    public CaqhPasswordService(JdbcTemplate jdbc, SsnConverter encryption) {
        this.jdbc = jdbc;
        this.encryption = encryption;
    }

    /** Blank input preserves the existing password; removal must be explicit. */
    @Transactional
    public void save(Long providerId, String password, boolean remove) {
        boolean supplied = password != null && !password.isEmpty();
        if (!supplied && !remove) {
            return;
        }
        User actor = currentActor();
        if (!actor.getRole().canEdit()
                || (actor.getRole() == Role.SUPERUSER && ViewedGroup.currentId() != null)) {
            throw new AccessDeniedException("You are not allowed to change stored CAQH passwords");
        }
        if (supplied && (remove || password.length() > 1024)) {
            throw new IllegalArgumentException("Invalid CAQH password change");
        }
        String encrypted = remove ? null : encryption.convertToDatabaseColumn(password);
        int updated = jdbc.update("""
                UPDATE providers SET caqh_password_ciphertext = ?
                WHERE id = ? AND user_group_id = ?
                """, encrypted, providerId, currentGroup());
        if (updated != 1) {
            throw new ProviderNotFoundException(providerId);
        }
    }

    @Transactional(readOnly = true)
    public boolean onFile(Long providerId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM providers
                WHERE id = ? AND user_group_id = ? AND caqh_password_ciphertext IS NOT NULL)
                """, Boolean.class, providerId, currentGroup()));
    }

    /** The audit insert and decryption must both succeed before a value is returned. */
    @Transactional
    public String reveal(Long providerId, String ipAddress) {
        User actor = currentActor();
        if (!actor.getRole().canRevealCaqhPassword()) {
            throw new AccessDeniedException("You are not allowed to view stored CAQH passwords");
        }
        Long group = currentGroup();
        List<StoredPassword> found = jdbc.query("""
                SELECT first_name, last_name, caqh_password_ciphertext FROM providers
                WHERE id = ? AND user_group_id = ?
                """, (rs, row) -> new StoredPassword(rs.getString("first_name") + " " + rs.getString("last_name"),
                rs.getString("caqh_password_ciphertext")), providerId, group);
        if (found.isEmpty()) {
            throw new ProviderNotFoundException(providerId);
        }
        StoredPassword stored = found.getFirst();
        jdbc.update("""
                INSERT INTO caqh_password_access_log
                    (user_group_id, provider_id, provider_name, user_id, user_email, ip_address)
                VALUES (?, ?, ?, ?, ?, ?)
                """, group, providerId, stored.name(), actor.getId(), actor.getEmail(), ipAddress);
        return encryption.convertToEntityAttribute(stored.ciphertext());
    }

    @Transactional(readOnly = true)
    public List<AccessEntry> recentAccess(Long providerId) {
        return jdbc.query("""
                SELECT accessed_at, user_email, ip_address FROM caqh_password_access_log
                WHERE user_group_id = ? AND provider_id = ? ORDER BY accessed_at DESC, id DESC LIMIT 10
                """, (rs, row) -> new AccessEntry(rs.getTimestamp("accessed_at").toInstant(),
                rs.getString("user_email"), rs.getString("ip_address")), currentGroup(), providerId);
    }

    public record AccessEntry(Instant accessedAt, String userEmail, String ipAddress) {}
    private record StoredPassword(String name, String ciphertext) {}

    private static Long currentGroup() {
        return new CurrentUserGroupResolver().resolveCurrentTenantIdentifier();
    }

    private static User currentActor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof CredAppUserDetails principal)
                || !principal.isEnabled()) {
            throw new AccessDeniedException("You are not allowed to access stored CAQH passwords");
        }
        return principal.getUser();
    }
}
