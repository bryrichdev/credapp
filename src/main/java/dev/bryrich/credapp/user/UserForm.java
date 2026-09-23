package dev.bryrich.credapp.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * One form for both screens. On create the password is required; on edit a blank password
 * means "leave the stored one alone", so the field is validated in the controller rather
 * than by an annotation that can't tell the two cases apart.
 */
public class UserForm {

    @Email(message = "Enter a valid email address")
    @NotBlank(message = "Email is required")
    private String email;

    private String fullName;

    @Size(min = 12, message = "Password must be at least 12 characters")
    private String password;

    @NotNull(message = "Role is required")
    private Role role = Role.COORDINATOR;

    private boolean enabled = true;

    /** Empty form, for the create screen. */
    public UserForm() {
    }

    /** Copies a saved account's values in, apart from the password. */
    public static UserForm from(User user) {
        UserForm form = new UserForm();
        form.email = user.getEmail();
        form.fullName = user.getFullName();
        form.role = user.getRole();
        form.enabled = user.isEnabled();
        return form;
    }

    public boolean hasPassword() {
        return password != null && !password.isBlank();
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
