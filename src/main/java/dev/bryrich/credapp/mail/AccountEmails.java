package dev.bryrich.credapp.mail;

import dev.bryrich.credapp.user.User;
import dev.bryrich.credapp.usergroup.UserGroup;
import dev.bryrich.credapp.usergroup.UserGroupRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;

/** The wording of every email about someone's account. */
@Component
public class AccountEmails {

    private final Mailer mailer;
    private final UserGroupRepository userGroups;

    public AccountEmails(Mailer mailer, UserGroupRepository userGroups) {
        this.mailer = mailer;
        this.userGroups = userGroups;
    }

    public void joinApproved(User account, User approver) {
        mailer.send(account.getEmail(), "Your CredCloud account is approved",
                greeting(account)
                        + name(approver) + " approved your request to join " + groupName(account) + ".\n\n"
                        + "You can sign in now:\n" + mailer.link("/login"));
    }

    public void resetRequested(Collection<User> approvers, User requester) {
        String who = requester.getFullName() == null || requester.getFullName().isBlank()
                ? requester.getEmail()
                : requester.getFullName().trim() + " (" + requester.getEmail() + ")";
        for (User approver : approvers) {
            mailer.send(approver.getEmail(), who + " asked to reset their CredCloud password",
                    greeting(approver)
                            + who + " in " + groupName(requester) + " says they've forgotten their password "
                            + "and asked for a reset link.\n\n"
                            + "If that sounds right, approve it on the Users page and CredCloud emails them the link:\n"
                            + mailer.link("/admin/users") + "\n\n"
                            + "If you don't recognize the request, decline it. Their password stays as it is either way "
                            + "until they choose a new one.");
        }
    }

    public void resetLink(User account, String token, Duration valid) {
        mailer.send(account.getEmail(), "Reset your CredCloud password",
                greeting(account)
                        + "Use this link to choose a new password. It works once and expires in "
                        + describe(valid) + ".\n\n"
                        + mailer.link("/password-reset/" + token) + "\n\n"
                        + "If you didn't ask for this, you can ignore this email. Your password hasn't changed.");
    }

    public void resetDeclined(User account) {
        mailer.send(account.getEmail(), "Your CredCloud password reset was declined",
                greeting(account)
                        + "Your admin declined the request to reset your password, so it hasn't changed.\n\n"
                        + "If you still can't sign in, ask your admin directly.");
    }

    public void passwordChanged(User account) {
        mailer.send(account.getEmail(), "Your CredCloud password was changed",
                greeting(account)
                        + "Your password was just changed with a reset link, and you've been signed out everywhere.\n\n"
                        + "If this wasn't you, tell your admin right away.");
    }

    private String groupName(User account) {
        return userGroups.findById(account.getUserGroupId()).map(UserGroup::getName).orElse("your group");
    }

    private static String greeting(User account) {
        String fullName = account.getFullName();
        return fullName == null || fullName.isBlank() ? "Hi,\n\n" : "Hi " + fullName.trim().split("\\s+")[0] + ",\n\n";
    }

    private static String name(User account) {
        String fullName = account.getFullName();
        return fullName == null || fullName.isBlank() ? "Your admin" : fullName.trim();
    }

    private static String describe(Duration valid) {
        long hours = valid.toHours();
        if (hours >= 1 && valid.toMinutes() % 60 == 0) {
            return hours == 1 ? "1 hour" : hours + " hours";
        }
        return valid.toMinutes() + " minutes";
    }
}
