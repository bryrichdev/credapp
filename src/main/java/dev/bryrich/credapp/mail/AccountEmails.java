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

    public void accountLocked(User account, int failures, Duration lock) {
        mailer.send(account.getEmail(), "Your CredCloud account was locked",
                greeting(account)
                        + "Someone got your password or two-step code wrong " + failures + " times in a row, "
                        + "so your account is locked for " + describe(lock) + ".\n\n"
                        + "If that was you, wait and try again, or ask your admin to unlock it now. "
                        + "If it wasn't you, someone may be guessing your password: change it once you're back in, "
                        + "and tell your admin.");
    }

    public void twoStepTurnedOn(User account) {
        mailer.send(account.getEmail(), "Two-step sign-in is on for your CredCloud account",
                greeting(account)
                        + "Two-step sign-in is now on. From now on, signing in asks for a code from your "
                        + "authenticator app after your password.\n\n"
                        + "Keep your recovery codes somewhere safe: each one gets you in once without your phone.\n\n"
                        + "If this wasn't you, tell your admin right away.");
    }

    public void twoStepTurnedOff(User account, User byAdmin) {
        String who = byAdmin == null ? "You turned off" : name(byAdmin) + " reset";
        mailer.send(account.getEmail(), "Two-step sign-in was turned off for your CredCloud account",
                greeting(account)
                        + who + " two-step sign-in for your account, so signing in no longer asks for a code"
                        + (byAdmin == null ? "." : " until you set it up again.") + "\n\n"
                        + "If you didn't expect this, tell your admin right away.");
    }

    public void recoveryCodeUsed(User account, int left) {
        mailer.send(account.getEmail(), "A CredCloud recovery code was used",
                greeting(account)
                        + "Someone just signed in to your account with one of your recovery codes. "
                        + (left == 0 ? "That was your last one: make new codes on your account page."
                                     : "You have " + left + " left.") + "\n\n"
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
