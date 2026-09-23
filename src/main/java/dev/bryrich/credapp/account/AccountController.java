package dev.bryrich.credapp.account;

import dev.bryrich.credapp.security.CredAppUserDetails;
import dev.bryrich.credapp.user.EmailAlreadyExistsException;
import dev.bryrich.credapp.user.User;
import dev.bryrich.credapp.user.UserService;
import dev.bryrich.credapp.user.WrongPasswordException;
import dev.bryrich.credapp.usergroup.UserGroup;
import dev.bryrich.credapp.usergroup.UserGroupRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashSet;
import java.util.List;


/**
 * My account: every signed-in user can change their own name, email and password here,
 * read-only accounts included. Admins and superusers come here for their own account too,
 * so every change to your own account asks for your current password.
 */
@Controller
@RequestMapping("/account")
public class AccountController {

    private final UserService userService;
    private final UserGroupRepository userGroups;
    private final ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> sessions;
    private final HttpSessionSecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    public AccountController(UserService userService,
                             UserGroupRepository userGroups,
                             ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> sessions) {
        this.userService = userService;
        this.userGroups = userGroups;
        this.sessions = sessions;
    }

    @GetMapping
    public String page(@AuthenticationPrincipal CredAppUserDetails principal, Model model) {
        User account = userService.findById(principal.getUser().getId());
        model.addAttribute("form", AccountForm.from(account));
        addContext(account, model);
        return "account/account";
    }

    @PostMapping
    public String save(@AuthenticationPrincipal CredAppUserDetails principal,
                       @Valid @ModelAttribute("form") AccountForm form,
                       BindingResult binding,
                       Model model,
                       HttpServletRequest request,
                       HttpServletResponse response,
                       RedirectAttributes redirectAttributes) {
        User before = userService.findById(principal.getUser().getId());
        // Open session in view hands back the same object after the save, so keep the old
        // address now: sessions signed in under it still need ending after a password change.
        String previousEmail = before.getEmail();
        if (form.changesPassword()) {
            try {
                UserService.requireValidPassword(form.getNewPassword());
            } catch (IllegalArgumentException ex) {
                binding.rejectValue("newPassword", "password.invalid", ex.getMessage());
            }
            if (!form.getNewPassword().equals(form.getConfirmPassword())) {
                binding.rejectValue("confirmPassword", "password.mismatch", "The new passwords don't match");
            }
        }
        if (!binding.hasErrors()) {
            try {
                User saved = userService.updateOwnAccount(before.getId(), form.getCurrentPassword(),
                        form.getEmail(), form.getFullName(), form.getNewPassword());
                afterSave(previousEmail, saved, form.changesPassword(), request, response);
                redirectAttributes.addFlashAttribute("message", form.changesPassword()
                        ? "Your account is updated. Your new password is in use, and any other devices "
                        + "signed in to this account have been signed out."
                        : "Your account is updated.");
                return "redirect:/account";
            } catch (WrongPasswordException ex) {
                binding.rejectValue("currentPassword", "password.wrong", ex.getMessage());
            } catch (EmailAlreadyExistsException ex) {
                binding.rejectValue("email", "email.exists", "That email is already in use");
            } catch (IllegalArgumentException ex) {
                binding.rejectValue("newPassword", "password.invalid", ex.getMessage());
            }
        }
        form.clearPasswords();
        addContext(before, model);
        return "account/account";
    }

    /**
     * Keeps this session signed in as the updated account. After a password change, every
     * other session for the account is ended, since the point of changing a password is
     * usually that someone else might know the old one, and this session gets a new id.
     */
    private void afterSave(String previousEmail, User saved, boolean passwordChanged,
                           HttpServletRequest request, HttpServletResponse response) {
        if (passwordChanged) {
            FindByIndexNameSessionRepository<? extends Session> repository = sessions.getIfAvailable();
            HttpSession current = request.getSession(false);
            if (repository != null) {
                String currentId = current == null ? null : current.getId();
                for (String name : new HashSet<>(List.of(previousEmail, saved.getEmail()))) {
                    repository.findByPrincipalName(name).keySet().stream()
                            .filter(id -> !id.equals(currentId))
                            .forEach(repository::deleteById);
                }
            }
            if (current != null) {
                request.changeSessionId();
            }
        }
        CredAppUserDetails details = new CredAppUserDetails(saved);
        var authentication = UsernamePasswordAuthenticationToken.authenticated(details, null, details.getAuthorities());
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
    }

    private void addContext(User account, Model model) {
        model.addAttribute("account", account);
        model.addAttribute("accountGroup", userGroups.findById(account.getUserGroupId())
                .map(UserGroup::getName).orElse(null));
    }
}
