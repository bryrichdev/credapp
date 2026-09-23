package dev.bryrich.credapp.common;

import dev.bryrich.credapp.security.CredAppUserDetails;
import dev.bryrich.credapp.user.Role;
import dev.bryrich.credapp.user.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Topbar values for every page. Controllers now live in their feature packages, so this
 * covers the whole app; the JSON controllers run it too, but nothing they return reads it.
 */
@ControllerAdvice(basePackages = "dev.bryrich.credapp")
public class WebModelAdvice {

    @ModelAttribute("user")
    public User currentUser(@AuthenticationPrincipal CredAppUserDetails principal) {
        return principal == null ? null : principal.getUser();
    }

    /** Controls the Users tab. True for both tiers that can manage accounts. */
    @ModelAttribute("isAdmin")
    public boolean isAdmin(@AuthenticationPrincipal CredAppUserDetails principal) {
        return principal != null && principal.getUser().getRole().canManageUsers();
    }

    @ModelAttribute("isSuperuser")
    public boolean isSuperuser(@AuthenticationPrincipal CredAppUserDetails principal) {
        return principal != null && principal.getUser().getRole() == Role.SUPERUSER;
    }
}