package dev.bryrich.credapp.user;

import dev.bryrich.credapp.security.CredAppUserDetails;
import dev.bryrich.credapp.usergroup.UserGroupRepository;
import dev.bryrich.credapp.usergroup.ViewedGroup;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Account administration. Who may do what lives in UserService, which checks the signed-in
 * account against the one being changed; this controller turns a refusal into a message on
 * the page instead of an error response.
 */
@Controller
@RequestMapping("/admin/users")
public class UserWebController {

    private final UserService userService;
    private final UserGroupRepository userGroups;

    public UserWebController(UserService userService, UserGroupRepository userGroups) {
        this.userService = userService;
        this.userGroups = userGroups;
    }

    /** Blank text inputs submit "" — store null instead. Also lets a blank password mean "unchanged". */
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    /**
     * An admin sees their own user group's accounts. A superuser sees every group's, can
     * narrow the list to one, and gets the list of groups to open read-only; while they're
     * viewing one, the page shows just that group, the way its admin would see it.
     */
    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(name = "group", required = false) Long groupFilter,
                       @AuthenticationPrincipal CredAppUserDetails principal,
                       @PageableDefault(sort = "email") Pageable pageable,
                       HttpServletRequest request,
                       Model model) {
        User actor = principal.getUser();
        boolean superuser = actor.getRole() == Role.SUPERUSER;
        Long viewed = superuser ? ViewedGroup.id(request) : null;
        Long scope = viewed != null ? viewed : groupFilter;

        model.addAttribute("page", userService.searchAs(actor, q, scope, pageable));
        model.addAttribute("q", q);
        if (superuser && viewed == null) {
            List<UserGroupSummary> summaries = userService.userGroupSummariesAs(actor);
            model.addAttribute("userGroupSummaries", summaries);
            model.addAttribute("userGroupNames", summaries.stream()
                    .collect(Collectors.toMap(UserGroupSummary::id, UserGroupSummary::name)));
            model.addAttribute("groupFilter", groupFilter);
        } else {
            model.addAttribute("userGroup",
                    userGroups.findById(viewed != null ? viewed : actor.getUserGroupId()).orElseThrow());
        }
        return "admin/users";
    }

    @GetMapping("/new")
    public String newUser(@AuthenticationPrincipal CredAppUserDetails principal,
                          @RequestParam(name = "group", required = false) Long groupId,
                          Model model) {
        UserForm form = new UserForm();
        form.setUserGroupId(groupId != null ? groupId : principal.getUser().getUserGroupId());
        model.addAttribute("form", form);
        addFormOptions(principal.getUser(), model);
        return "admin/user-form";
    }

    private void addFormOptions(User actor, Model model) {
        model.addAttribute("roles", actor.getRole().assignableRoles());
        if (actor.getRole() == Role.SUPERUSER) {
            model.addAttribute("userGroupChoices", userGroups.findAll(Sort.by("name")));
        }
    }

    @PostMapping
    public String create(@AuthenticationPrincipal CredAppUserDetails principal,
                         @Valid @ModelAttribute("form") UserForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        User actor = principal.getUser();
        if (!form.hasPassword()) {
            binding.rejectValue("password", "password.required", "Password is required");
        }
        if (binding.hasErrors()) {
            addFormOptions(actor, model);
            return "admin/user-form";
        }
        try {
            userService.createAs(actor, form.getEmail(), form.getPassword(),
                    form.getFullName(), form.getRole(),
                    actor.getRole() == Role.SUPERUSER ? form.getUserGroupId() : null);
        } catch (EmailAlreadyExistsException ex) {
            binding.rejectValue("email", "email.exists", "That email is already in use");
            addFormOptions(actor, model);
            return "admin/user-form";
        } catch (UserManagementDeniedException | IllegalArgumentException ex) {
            return refused(ex, redirectAttributes);
        }
        redirectAttributes.addFlashAttribute("message", "Account created.");
        return "redirect:/admin/users";
    }

    @GetMapping("/{id}/edit")
    public String editUser(@AuthenticationPrincipal CredAppUserDetails principal,
                           @PathVariable Long id,
                           Model model,
                           RedirectAttributes redirectAttributes) {
        User actor = principal.getUser();
        User target = userService.findByIdAs(actor, id);
        if (!target.getId().equals(actor.getId()) && !actor.getRole().canManage(target.getRole())) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "You are not allowed to manage " + target.getRole().getLabel() + " accounts.");
            return "redirect:/admin/users";
        }
        model.addAttribute("form", UserForm.from(target));
        model.addAttribute("roles", actor.getRole().assignableRoles());
        model.addAttribute("target", target);
        return "admin/user-form";
    }

    @PostMapping("/{id}/edit")
    public String updateUser(@AuthenticationPrincipal CredAppUserDetails principal,
                             @PathVariable Long id,
                             @Valid @ModelAttribute("form") UserForm form,
                             BindingResult binding,
                             Model model,
                             RedirectAttributes redirectAttributes) {
        User actor = principal.getUser();
        userService.findByIdAs(actor, id);
        if (binding.hasErrors()) {
            model.addAttribute("roles", actor.getRole().assignableRoles());
            model.addAttribute("target", userService.findByIdAs(actor, id));
            return "admin/user-form";
        }
        try {
            userService.updateAs(actor, id, form.getEmail(), form.getFullName(),
                    form.getRole(), form.isEnabled());
            if (form.hasPassword()) {
                userService.changePasswordAs(actor, id, form.getPassword());
            }
        } catch (EmailAlreadyExistsException ex) {
            binding.rejectValue("email", "email.exists", "That email is already in use");
            model.addAttribute("roles", actor.getRole().assignableRoles());
            model.addAttribute("target", userService.findByIdAs(actor, id));
            return "admin/user-form";
        } catch (UserManagementDeniedException | IllegalArgumentException ex) {
            return refused(ex, redirectAttributes);
        }
        redirectAttributes.addFlashAttribute("message", "Account updated.");
        return "redirect:/admin/users";
    }

    /** Promote or demote straight from the list. */
    @PostMapping("/{id}/role")
    public String changeRole(@AuthenticationPrincipal CredAppUserDetails principal,
                             @PathVariable Long id,
                             @RequestParam Role role,
                             RedirectAttributes redirectAttributes) {
        try {
            userService.changeRoleAs(principal.getUser(), id, role);
        } catch (UserManagementDeniedException | IllegalArgumentException ex) {
            return refused(ex, redirectAttributes);
        }
        redirectAttributes.addFlashAttribute("message", "Role changed to " + role.getLabel() + ".");
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/enabled")
    public String setEnabled(@AuthenticationPrincipal CredAppUserDetails principal,
                             @PathVariable Long id,
                             @RequestParam boolean enabled,
                             RedirectAttributes redirectAttributes) {
        try {
            userService.setEnabledAs(principal.getUser(), id, enabled);
        } catch (UserManagementDeniedException | IllegalArgumentException ex) {
            return refused(ex, redirectAttributes);
        }
        redirectAttributes.addFlashAttribute("message", enabled ? "Account enabled." : "Account disabled.");
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/delete")
    public String delete(@AuthenticationPrincipal CredAppUserDetails principal,
                         @PathVariable Long id,
                         RedirectAttributes redirectAttributes) {
        try {
            userService.deleteAs(principal.getUser(), id);
        } catch (UserManagementDeniedException | IllegalArgumentException ex) {
            return refused(ex, redirectAttributes);
        }
        redirectAttributes.addFlashAttribute("message", "Account deleted.");
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/approval")
    public String decideJoinRequest(@AuthenticationPrincipal CredAppUserDetails principal,
                                    @PathVariable Long id, @RequestParam boolean approve,
                                    RedirectAttributes redirectAttributes) {
        try {
            userService.decideJoinRequestAs(principal.getUser(), id, approve);
        } catch (UserManagementDeniedException ex) {
            return refused(ex, redirectAttributes);
        }
        redirectAttributes.addFlashAttribute("message", approve ? "Join request approved. The coordinator can now sign in."
                : "Join request rejected. The account has no access.");
        return "redirect:/admin/users";
    }

    private String refused(RuntimeException ex, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        return "redirect:/admin/users";
    }
}
