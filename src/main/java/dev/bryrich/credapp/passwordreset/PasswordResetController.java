package dev.bryrich.credapp.passwordreset;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Forgot password: ask for a link, then choose a new password from it. Open to everyone. */
@Controller
@RequestMapping("/password-reset")
public class PasswordResetController {

    /** Same answer whatever happened, so the page can't be used to find out who has an account. */
    static final String REQUESTED = "If that email has a CredCloud account, we've sent the next step. "
            + "Admins get a reset link by email right away. Everyone else's request goes to their admin first, "
            + "and the link arrives once they approve it.";

    private final PasswordResetService resets;

    public PasswordResetController(PasswordResetService resets) {
        this.resets = resets;
    }

    @GetMapping
    public String form() {
        return "password-reset/request";
    }

    @PostMapping
    public String request(@RequestParam(required = false) String email, HttpServletRequest request,
                          Model model, RedirectAttributes redirect) {
        if (email == null || email.isBlank()) {
            model.addAttribute("error", "Enter the email you sign in with");
            return "password-reset/request";
        }
        resets.request(email, request.getRemoteAddr());
        redirect.addFlashAttribute("message", REQUESTED);
        return "redirect:/login";
    }

    @GetMapping("/{token:[A-Za-z0-9_-]{20,100}}")
    public String choose(@PathVariable String token, Model model) {
        return resets.emailFor(token)
                .map(email -> {
                    model.addAttribute("email", email);
                    model.addAttribute("token", token);
                    return "password-reset/choose";
                })
                .orElse("password-reset/invalid");
    }

    @PostMapping("/{token:[A-Za-z0-9_-]{20,100}}")
    public String reset(@PathVariable String token,
                        @RequestParam(required = false) String newPassword,
                        @RequestParam(required = false) String confirmPassword,
                        HttpServletRequest request, Model model, RedirectAttributes redirect) {
        String error = null;
        if (newPassword == null || !newPassword.equals(confirmPassword)) {
            error = "The passwords don't match";
        } else {
            try {
                resets.reset(token, newPassword);
            } catch (InvalidResetLinkException ex) {
                return "password-reset/invalid";
            } catch (IllegalArgumentException ex) {
                error = ex.getMessage();
            }
        }
        if (error != null) {
            model.addAttribute("error", error);
            model.addAttribute("token", token);
            resets.emailFor(token).ifPresent(email -> model.addAttribute("email", email));
            return model.containsAttribute("email") ? "password-reset/choose" : "password-reset/invalid";
        }

        // Whoever was signed in on this browser is signed out too, along with every other session.
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        redirect.addFlashAttribute("message", "Your password is changed. Sign in with the new one.");
        return "redirect:/login";
    }
}
