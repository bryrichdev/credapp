package dev.bryrich.credapp.usergroup;

import dev.bryrich.credapp.user.Role;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Deletes a user group outright: its data, as a wipe would, and then everything a wipe keeps
 * (its accounts, their sign-ins, its access logs and tracking settings) and the group itself.
 *
 * Two groups can't be deleted: the superuser's own, which would delete the account doing it,
 * and any group holding another superuser, which would remove someone with the same power
 * as the person deleting. That superuser has to be demoted first.
 */
@Service
public class UserGroupDeleteService {

    private static final Logger log = LoggerFactory.getLogger(UserGroupDeleteService.class);

    /**
     * The group-scoped tables a wipe keeps, deleted after the wipe's own list. Users go last
     * of these because nothing else may still point at them.
     */
    static final List<String> AFTER_WIPE = List.of(
            "password_reset_requests", "ssn_access_log", "caqh_password_access_log", "tracking_settings", "users");

    static {
        if (!Set.copyOf(AFTER_WIPE).equals(UserGroupWipeService.KEPT)) {
            throw new IllegalStateException("A delete must remove every table a wipe keeps");
        }
    }

    /** One account that goes with the group. */
    public record Account(String email, String fullName, Role role) {
    }

    /** What deleting a group would remove, or why it can't be deleted. */
    public record Preview(UserGroupWipeService.Contents contents, List<Account> accounts, String refusal) {
        public boolean allowed() {
            return refusal == null;
        }
    }

    /** Thrown when the group can't be deleted, with the reason to show. */
    public static class RefusedException extends RuntimeException {
        public RefusedException(String reason) {
            super(reason);
        }
    }

    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;
    private final UserGroupWipeService wipes;

    public UserGroupDeleteService(JdbcTemplate jdbc, EntityManager entityManager, UserGroupWipeService wipes) {
        this.jdbc = jdbc;
        this.entityManager = entityManager;
        this.wipes = wipes;
    }

    @Transactional(readOnly = true)
    public Preview preview(Long userGroupId, Long actorUserGroupId) {
        List<Account> accounts = accounts(userGroupId);
        return new Preview(wipes.count(userGroupId), accounts, refusal(userGroupId, actorUserGroupId, accounts));
    }

    /**
     * Deletes the group and everything in it in one transaction.
     *
     * @return what was there before, for the confirmation message
     * @throws RefusedException if it's the actor's own group or holds another superuser
     */
    @Transactional
    public Preview delete(Long userGroupId, Long actorUserGroupId, String deletedBy) {
        // Hold the row so nobody joins, is promoted or wipes it while this runs.
        jdbc.queryForObject("SELECT id FROM user_groups WHERE id = ? FOR UPDATE", Long.class, userGroupId);
        entityManager.flush();
        List<Account> accounts = accounts(userGroupId);
        String refusal = refusal(userGroupId, actorUserGroupId, accounts);
        if (refusal != null) {
            throw new RefusedException(refusal);
        }
        Preview before = new Preview(wipes.count(userGroupId), accounts, null);

        for (String table : UserGroupWipeService.DELETE_ORDER) {
            jdbc.update("DELETE FROM " + table + " WHERE user_group_id = ?", userGroupId);
        }
        // Sign its people out everywhere. Their next request would end the session anyway,
        // but there's no reason to leave the rows behind.
        jdbc.update("DELETE FROM spring_session WHERE lower(principal_name) IN "
                + "(SELECT lower(email) FROM users WHERE user_group_id = ?)", userGroupId);
        for (String table : AFTER_WIPE) {
            jdbc.update("DELETE FROM " + table + " WHERE user_group_id = ?", userGroupId);
        }
        jdbc.update("DELETE FROM user_groups WHERE id = ?", userGroupId);
        entityManager.clear();

        log.warn("{} deleted user group {}: {} records and {} accounts deleted",
                deletedBy, userGroupId, before.contents().total(), accounts.size());
        return before;
    }

    private List<Account> accounts(Long userGroupId) {
        return jdbc.query("SELECT email, full_name, role FROM users WHERE user_group_id = ? ORDER BY lower(email)",
                (row, i) -> new Account(row.getString("email"), row.getString("full_name"),
                        Role.fromValue(row.getString("role"))),
                userGroupId);
    }

    private static String refusal(Long userGroupId, Long actorUserGroupId, List<Account> accounts) {
        if (userGroupId.equals(actorUserGroupId)) {
            return "You can't delete your own user group, because your account is in it. "
                    + "Wipe its data instead, or have another superuser delete it.";
        }
        List<String> superusers = accounts.stream()
                .filter(account -> account.role() == Role.SUPERUSER)
                .map(Account::email)
                .toList();
        if (!superusers.isEmpty()) {
            return "This group has another superuser (" + String.join(", ", superusers) + "). "
                    + "Change their role first; one superuser can't delete another.";
        }
        return null;
    }
}
