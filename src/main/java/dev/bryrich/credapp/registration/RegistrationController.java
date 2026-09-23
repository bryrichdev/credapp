package dev.bryrich.credapp.registration;

import dev.bryrich.credapp.user.*;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/register")
public class RegistrationController {
    private final RegistrationService registration;

    public RegistrationController(RegistrationService registration) { this.registration = registration; }

    @GetMapping
    public String form(Model model) {
        model.addAttribute("form", new RegistrationForm());
        return "register";
    }

    @PostMapping
    public String register(@Valid @ModelAttribute("form") RegistrationForm form,
                           BindingResult binding, RedirectAttributes redirect) {
        if (form.getRole() != Role.ADMIN && form.getRole() != Role.COORDINATOR) {
            binding.rejectValue("role", "role.invalid", "Choose Admin or Coordinator");
        }
        if (form.getPassword() != null && !form.getPassword().equals(form.getConfirmPassword())) {
            binding.rejectValue("confirmPassword", "password.mismatch", "Passwords must match");
        }
        if (form.getRole() == Role.ADMIN && (form.getGroupName() == null || form.getGroupName().isBlank())) {
            binding.rejectValue("groupName", "group.required", "Enter a name for your new user group");
        }
        if (form.getRole() == Role.COORDINATOR && (form.getJoinCode() == null || form.getJoinCode().isBlank())) {
            binding.rejectValue("joinCode", "group.required", "Enter the group code shared by your admin");
        }
        if (binding.hasErrors()) { return "register"; }
        try {
            registration.register(form);
        } catch (EmailAlreadyExistsException ex) {
            binding.rejectValue("email", "email.exists", "That email is already in use");
            return "register";
        } catch (DataIntegrityViolationException ex) {
            binding.reject("registration.conflict", "The account could not be created. The email may already be in use.");
            return "register";
        } catch (IllegalArgumentException ex) {
            binding.reject("registration.invalid", ex.getMessage());
            return "register";
        }
        redirect.addFlashAttribute("message", form.getRole() == Role.ADMIN
                ? "Account and user group created. You can now sign in."
                : "Request submitted. Your group's admin must approve your account before you can sign in.");
        return "redirect:/login";
    }
}
