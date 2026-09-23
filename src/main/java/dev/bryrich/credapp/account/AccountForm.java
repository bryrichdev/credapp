package dev.bryrich.credapp.account;

import dev.bryrich.credapp.user.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The My account page: your own name, email and password. Nothing on it saves without
 * the current password, and a blank new password keeps the one you have.
 */
public class AccountForm {

    @Size(max = 200, message = "Name can be at most 200 characters")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    private String email;

    private String newPassword;

    private String confirmPassword;

    @NotBlank(message = "Enter your current password to save changes")
    private String currentPassword;

    public static AccountForm from(User user) {
        AccountForm form = new AccountForm();
        form.fullName = user.getFullName();
        form.email = user.getEmail();
        return form;
    }

    public boolean changesPassword() {
        return newPassword != null && !newPassword.isEmpty();
    }

    /** Passwords never go back out to the page, even when it's shown again with errors. */
    public void clearPasswords() {
        newPassword = null;
        confirmPassword = null;
        currentPassword = null;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }
}
