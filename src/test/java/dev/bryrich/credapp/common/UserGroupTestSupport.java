package dev.bryrich.credapp.common;

import dev.bryrich.credapp.security.CredAppUserDetails;
import dev.bryrich.credapp.user.User;
import dev.bryrich.credapp.usergroup.UserGroup;
import dev.bryrich.credapp.usergroup.UserGroupRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.context.transaction.BeforeTransaction;

/** Transactional persistence tests explicitly create and select their own workspace. */
public abstract class UserGroupTestSupport {
    @Autowired private UserGroupRepository userGroups;
    private Long userGroupId;

    @BeforeTransaction
    void selectTestGroup() {
        UserGroup group = userGroups.saveAndFlush(new UserGroup("Persistence test group"));
        userGroupId = group.getId();
        User account = new User("fixture@example.com", "unused-password-hash", userGroupId);
        CredAppUserDetails principal = new CredAppUserDetails(account);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
    }

    @AfterTransaction
    void removeTestGroup() {
        SecurityContextHolder.clearContext();
        userGroups.deleteById(userGroupId);
    }
}
