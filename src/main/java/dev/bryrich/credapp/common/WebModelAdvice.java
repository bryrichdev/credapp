package dev.bryrich.credapp.common;

import dev.bryrich.credapp.security.CredAppUserDetails;
import dev.bryrich.credapp.tracking.TrackingBadge;
import dev.bryrich.credapp.tracking.TrackingService;
import dev.bryrich.credapp.user.Role;
import dev.bryrich.credapp.user.User;
import dev.bryrich.credapp.usergroup.ViewedGroup;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Topbar values for every page. Controllers now live in their feature packages, so this
 * covers the whole app; the JSON controllers run it too, but nothing they return reads it.
 */
@ControllerAdvice(basePackages = "dev.bryrich.credapp")
public class WebModelAdvice {

    /** Absent in controller test slices, where the Tracking count just shows nothing. */
    private final ObjectProvider<TrackingService> tracking;

    public WebModelAdvice(ObjectProvider<TrackingService> tracking) {
        this.tracking = tracking;
    }

    /** How many things need doing now, for the Tracking link. Counted only if a page shows it. */
    @ModelAttribute("trackingBadge")
    public TrackingBadge trackingBadge(@AuthenticationPrincipal CredAppUserDetails principal) {
        TrackingService service = tracking.getIfAvailable();
        if (principal == null || !principal.isEnabled() || service == null) {
            return TrackingBadge.NONE;
        }
        return new TrackingBadge(service::needingAction);
    }

    @ModelAttribute("canEdit")
    public boolean canEdit(@AuthenticationPrincipal CredAppUserDetails principal, HttpServletRequest request) {
        return principal != null && principal.isEnabled() && principal.getUser().getRole().canEdit()
                && viewingGroupName(principal, request) == null;
    }

    /**
     * The other user group a superuser is looking at, read-only, or null when they're in
     * their own. Drives the banner under the top bar and switches editing off everywhere.
     */
    @ModelAttribute("viewingGroupName")
    public String viewingGroupName(@AuthenticationPrincipal CredAppUserDetails principal,
                                   HttpServletRequest request) {
        if (principal == null || principal.getUser().getRole() != Role.SUPERUSER) {
            return null;
        }
        return ViewedGroup.id(request) == null ? null : ViewedGroup.name(request);
    }

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
