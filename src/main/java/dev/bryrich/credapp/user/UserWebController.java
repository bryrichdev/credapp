package dev.bryrich.credapp.user;

import dev.bryrich.credapp.security.CredAppUserDetails;
import jakarta.validation.Valid;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Account administration. Who may do what lives in UserService, which checks the signed-in
 * account against the one being changed; this controller turns a refusal into a message on
 * the page instead of an error response.
 */
@Controller
@RequestMapping("/admin/users")
public class UserWebController {

    private final UserService userService;

    public UserWebController(UserService userService) {
        this.userService = userService;
    }

    /** Blank text inputs submit "" — store null instead. Also lets a blank password mean "unchanged". */
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @PageableDefault(sort = "email") Pageable pageable,
                       Model model) {
        Page<User> page = (q == null || q.isBlank())
                ? userService.findAll(pageable)
                : userService.search(q, pageable);

        model.addAttribute("page", page);
        model.addAttribute("q", q);
        return "admin/users";
    }

    @GetMapping("/new")
    public String newUser(@AuthenticationPrincipal CredAppUserDetails principal, Model model) {
        model.addAttribute("form", new UserForm());
        model.addAttribute("roles", principal.getUser().getRole().assignableRoles());
        return "admin/user-form";
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
            model.addAttribute("roles", actor.getRole().assignableRoles());
            return "admin/user-form";
        }
        try {
            userService.createAs(actor, form.getEmail(), form.getPassword(),
                    form.getFullName(), form.getRole());
        } catch (EmailAlreadyExistsException ex) {
            binding.rejectValue("email", "email.exists", "That email is already in use");
            model.addAttribute("roles", actor.getRole().assignableRoles());
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
        User target = userService.findById(id);
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
        if (binding.hasErrors()) {
            model.addAttribute("roles", actor.getRole().assignableRoles());
            model.addAttribute("target", userService.findById(id));
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
            model.addAttribute("target", userService.findById(id));
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

    private String refused(RuntimeException ex, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        return "redirect:/admin/users";
    }
}
