package dev.bryrich.credapp.usergroup;

import dev.bryrich.credapp.security.CredAppUserDetails;
import dev.bryrich.credapp.user.Role;
import dev.bryrich.credapp.user.UserManagementDeniedException;
import dev.bryrich.credapp.user.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * A superuser empties a user group of its data (any group, their own included) and keeps
 * its accounts. It can't be undone, so it asks for the group's name and the superuser's
 * current password first.
 */
@Controller
@RequestMapping("/admin/user-groups/{id}/wipe")
public class UserGroupWipeController {

    private final UserGroupRepository userGroups;
    private final UserGroupWipeService wipes;
    private final UserService users;

    public UserGroupWipeController(UserGroupRepository userGroups, UserGroupWipeService wipes, UserService users) {
        this.userGroups = userGroups;
        this.wipes = wipes;
        this.users = users;
    }

    @GetMapping
    public String confirm(@AuthenticationPrincipal CredAppUserDetails principal, @PathVariable Long id, Model model) {
        UserGroup group = groupFor(principal, id);
        show(model, principal, group, new WipeForm());
        return "admin/wipe";
    }

    @PostMapping
    public String wipe(@AuthenticationPrincipal CredAppUserDetails principal,
                       @PathVariable Long id,
                       @ModelAttribute("form") WipeForm form,
                       BindingResult errors,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        UserGroup group = groupFor(principal, id);
        String typed = form.getConfirmName() == null ? "" : form.getConfirmName().trim();
        if (!typed.equals(group.getName().trim())) {
            errors.rejectValue("confirmName", "mismatch", "Type the group's name exactly as shown");
        }
        if (!users.passwordMatches(principal.getUser().getId(), form.getCurrentPassword())) {
            errors.rejectValue("currentPassword", "wrong", "That isn't your current password");
        }
        if (errors.hasErrors()) {
            form.setCurrentPassword(null);
            show(model, principal, group, form);
            return "admin/wipe";
        }

        UserGroupWipeService.Contents wiped = wipes.wipe(group.getId(), principal.getUser().getEmail());
        redirectAttributes.addFlashAttribute("message", wiped.isEmpty()
                ? group.getName() + " had no data to wipe."
                : "Wiped " + group.getName() + ": " + wiped.total() + (wiped.total() == 1 ? " record" : " records")
                        + " deleted. Its " + accounts(wiped.accounts()) + " kept.");
        return "redirect:/admin/users";
    }

    private void show(Model model, CredAppUserDetails principal, UserGroup group, WipeForm form) {
        model.addAttribute("wipeGroup", group);
        model.addAttribute("own", group.getId().equals(principal.getUser().getUserGroupId()));
        model.addAttribute("contents", wipes.contents(group.getId()));
        model.addAttribute("form", form);
    }

    private UserGroup groupFor(CredAppUserDetails principal, Long id) {
        if (principal.getUser().getRole() != Role.SUPERUSER) {
            throw new UserManagementDeniedException("Only a superuser can wipe a user group");
        }
        return userGroups.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "No such user group"));
    }

    private static String accounts(long count) {
        return count == 1 ? "1 account was" : count + " accounts were";
    }
}
