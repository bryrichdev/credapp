package dev.bryrich.credapp.usergroup;

import dev.bryrich.credapp.security.CredAppUserDetails;
import dev.bryrich.credapp.user.Role;
import dev.bryrich.credapp.user.UserManagementDeniedException;
import dev.bryrich.credapp.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
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

import java.util.Objects;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * A superuser deletes a whole user group: its data, its accounts and the group itself.
 * Confirmed the same way as a wipe, with the group's name and the superuser's password.
 */
@Controller
@RequestMapping("/admin/user-groups/{id}/delete")
public class UserGroupDeleteController {

    private final UserGroupRepository userGroups;
    private final UserGroupDeleteService deletes;
    private final UserService users;

    public UserGroupDeleteController(UserGroupRepository userGroups, UserGroupDeleteService deletes,
                                     UserService users) {
        this.userGroups = userGroups;
        this.deletes = deletes;
        this.users = users;
    }

    @GetMapping
    public String confirm(@AuthenticationPrincipal CredAppUserDetails principal, @PathVariable Long id, Model model) {
        UserGroup group = groupFor(principal, id);
        show(model, principal, group, new WipeForm());
        return "admin/delete-group";
    }

    @PostMapping
    public String delete(@AuthenticationPrincipal CredAppUserDetails principal,
                         @PathVariable Long id,
                         @ModelAttribute("form") WipeForm form,
                         BindingResult errors,
                         Model model,
                         HttpServletRequest request,
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
            return "admin/delete-group";
        }

        UserGroupDeleteService.Preview deleted;
        try {
            deleted = deletes.delete(group.getId(), principal.getUser().getUserGroupId(),
                    principal.getUser().getEmail());
        } catch (UserGroupDeleteService.RefusedException refused) {
            // Someone was promoted between loading the page and submitting it.
            show(model, principal, group, new WipeForm());
            return "admin/delete-group";
        }
        if (Objects.equals(ViewedGroup.id(request), group.getId())) {
            ViewedGroup.stop(request.getSession());
        }
        long records = deleted.contents().total();
        int accounts = deleted.accounts().size();
        redirectAttributes.addFlashAttribute("message", "Deleted " + group.getName() + ": "
                + records + (records == 1 ? " record" : " records") + " and "
                + accounts + (accounts == 1 ? " account." : " accounts."));
        return "redirect:/admin/users";
    }

    private void show(Model model, CredAppUserDetails principal, UserGroup group, WipeForm form) {
        model.addAttribute("deleteGroup", group);
        model.addAttribute("preview", deletes.preview(group.getId(), principal.getUser().getUserGroupId()));
        model.addAttribute("form", form);
    }

    private UserGroup groupFor(CredAppUserDetails principal, Long id) {
        if (principal.getUser().getRole() != Role.SUPERUSER) {
            throw new UserManagementDeniedException("Only a superuser can delete a user group");
        }
        return userGroups.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "No such user group"));
    }
}
