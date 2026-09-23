package dev.bryrich.credapp.usergroup;

import dev.bryrich.credapp.security.CredAppUserDetails;
import dev.bryrich.credapp.user.Role;
import dev.bryrich.credapp.user.UserManagementDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Lets a superuser open another user group the way that group's admin sees it, read-only,
 * and come back. See ViewedGroup for how the choice is held and GroupViewFilter for how
 * it's kept read-only.
 */
@Controller
@RequestMapping("/admin/user-groups")
public class UserGroupViewController {

    private final UserGroupRepository userGroups;

    public UserGroupViewController(UserGroupRepository userGroups) {
        this.userGroups = userGroups;
    }

    @PostMapping("/{id}/view")
    public String view(@AuthenticationPrincipal CredAppUserDetails principal,
                       @PathVariable Long id,
                       @RequestParam(required = false) String next,
                       HttpServletRequest request,
                       RedirectAttributes redirectAttributes) {
        if (principal.getUser().getRole() != Role.SUPERUSER) {
            throw new UserManagementDeniedException("Only a superuser can view another user group");
        }
        UserGroup group = userGroups.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "No such user group"));
        // "import" goes on to the spreadsheet import for that group; nothing else is accepted,
        // so this can't be used to redirect anywhere else.
        String destination = "import".equals(next) ? "redirect:/admin/import" : "redirect:/providers";
        if (group.getId().equals(principal.getUser().getUserGroupId())) {
            ViewedGroup.stop(request.getSession());
            return destination;
        }
        ViewedGroup.start(request.getSession(), group);
        if (!"import".equals(next)) {
            redirectAttributes.addFlashAttribute("message",
                    "You're viewing " + group.getName() + " as its admin sees it. Nothing can be changed here.");
        }
        return destination;
    }

    @PostMapping("/view/exit")
    public String exit(HttpServletRequest request, RedirectAttributes redirectAttributes) {
        ViewedGroup.stop(request.getSession());
        redirectAttributes.addFlashAttribute("message", "Back in your own user group.");
        return "redirect:/admin/users";
    }
}
