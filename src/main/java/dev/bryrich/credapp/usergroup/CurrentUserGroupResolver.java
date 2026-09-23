package dev.bryrich.credapp.usergroup;

import dev.bryrich.credapp.security.CredAppUserDetails;
import dev.bryrich.credapp.user.Role;
import dev.bryrich.credapp.user.User;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The user group every group-scoped query and insert runs in: the signed-in account's own,
 * or, for a superuser who chose to view another group, that one (see ViewedGroup). While
 * viewing, GroupViewFilter turns away anything that would write.
 */
public class CurrentUserGroupResolver implements CurrentTenantIdentifierResolver<Long> {
    @Override
    public Long resolveCurrentTenantIdentifier() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof CredAppUserDetails principal
                && principal.isEnabled()) {
            User user = principal.getUser();
            if (user.getRole() == Role.SUPERUSER) {
                Long viewed = ViewedGroup.currentId();
                if (viewed != null) {
                    return viewed;
                }
            }
            return user.getUserGroupId();
        }
        // Zero is not a group. Public requests and background work without an explicit
        // authenticated group can read global account metadata, but no group records.
        return 0L;
    }

    @Override
    public boolean validateExistingCurrentSessions() { return true; }
}
