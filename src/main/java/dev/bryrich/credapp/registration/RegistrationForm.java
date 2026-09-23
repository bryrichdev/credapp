package dev.bryrich.credapp.registration;

import dev.bryrich.credapp.user.Role;
import jakarta.validation.constraints.*;

public class RegistrationForm {
    @NotBlank(message = "Name is required")
    @Size(max = 200)
    private String fullName;
    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    @Size(max = 254)
    private String email;
    @NotBlank(message = "Password is required")
    @Size(min = 12, max = 72, message = "Use a password with 12–72 characters")
    private String password;
    @NotBlank(message = "Confirm your password")
    private String confirmPassword;
    @NotNull(message = "Choose Admin or Coordinator")
    private Role role = Role.ADMIN;
    @Size(max = 200)
    private String groupName;
    @Size(max = 36)
    private String joinCode;

    public String getFullName() { return fullName; }
    public void setFullName(String value) { fullName = value == null ? null : value.trim(); }
    public String getEmail() { return email; }
    public void setEmail(String value) { email = value == null ? null : value.trim(); }
    public String getPassword() { return password; }
    public void setPassword(String value) { password = value; }
    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String value) { confirmPassword = value; }
    public Role getRole() { return role; }
    public void setRole(Role value) { role = value; }
    public String getGroupName() { return groupName; }
    public void setGroupName(String value) { groupName = value == null ? null : value.trim(); }
    public String getJoinCode() { return joinCode; }
    public void setJoinCode(String value) { joinCode = value == null ? null : value.trim().toLowerCase(java.util.Locale.ROOT); }
}
