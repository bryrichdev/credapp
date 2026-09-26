package dev.bryrich.credapp.twostep;

import dev.bryrich.credapp.mail.AccountEmails;
import dev.bryrich.credapp.security.CredAppUserDetails;
import dev.bryrich.credapp.security.SignInLockout;
import dev.bryrich.credapp.user.User;
import dev.bryrich.credapp.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/** The code prompt at sign-in, and setting two-step sign-in up or off from your account. */
@Controller
public class TwoStepController {

    private final TwoStepService twoStep;
    private final SignInLockout lockout;
    private final UserService users;
    private final PasswordEncoder passwords;
    private final AccountEmails emails;

    public TwoStepController(TwoStepService twoStep, SignInLockout lockout, UserService users,
                             PasswordEncoder passwords, AccountEmails emails) {
        this.twoStep = twoStep;
        this.lockout = lockout;
        this.users = users;
        this.passwords = passwords;
        this.emails = emails;
    }

    // ---------- at sign-in ----------

    @GetMapping(TwoStepFilter.PROMPT)
    public String prompt(@AuthenticationPrincipal CredAppUserDetails principal, HttpServletRequest request) {
        long id = principal.getUser().getId();
        if (!twoStep.isEnabled(id) || TwoStepFilter.verified(request, id)) {
            return "redirect:/";
        }
        return "twostep/prompt";
    }

    @PostMapping(TwoStepFilter.PROMPT)
    public String check(@AuthenticationPrincipal CredAppUserDetails principal,
                        @RequestParam(defaultValue = "") String code,
                        HttpServletRequest request, Model model, RedirectAttributes redirect) {
        User account = principal.getUser();
        if (TwoStepFilter.verified(request, account.getId())) {
            return "redirect:/";
        }
        TwoStepService.Result result = twoStep.verify(account.getId(), code);
        if (result == TwoStepService.Result.WRONG) {
            if (lockout.failed(account.getId())) {
                signOut(request);
                return "redirect:/login?locked";
            }
            model.addAttribute("error", "That code didn't work. Codes change every 30 seconds; use the newest one.");
            return "twostep/prompt";
        }
        lockout.succeeded(account.getId());
        // A new session id for the fully signed-in session, as at any sign-in.
        request.changeSessionId();
        TwoStepFilter.markVerified(request, account.getId());
        if (result == TwoStepService.Result.ACCEPTED_RECOVERY_CODE) {
            int left = twoStep.status(account.getId()).recoveryCodesLeft();
            emails.recoveryCodeUsed(account, left);
            redirect.addFlashAttribute("message", left == 0
                    ? "You used your last recovery code. Make new ones on your account page."
                    : "You signed in with a recovery code. " + left + " left.");
        }
        return "redirect:/";
    }

    // ---------- from your account ----------

    @GetMapping(TwoStepFilter.SETUP)
    public String setup(@AuthenticationPrincipal CredAppUserDetails principal, HttpServletRequest request,
                        Model model) {
        User account = principal.getUser();
        if (twoStep.isEnabled(account.getId())) {
            return "redirect:/account";
        }
        addSetup(account, request, model);
        return "twostep/setup";
    }

    @PostMapping(TwoStepFilter.SETUP)
    public String finishSetup(@AuthenticationPrincipal CredAppUserDetails principal,
                              @RequestParam(defaultValue = "") String code,
                              HttpServletRequest request, Model model, RedirectAttributes redirect) {
        User account = principal.getUser();
        if (twoStep.isEnabled(account.getId())) {
            return "redirect:/account";
        }
        List<String> codes = twoStep.finishSetup(account.getId(), code);
        if (codes.isEmpty()) {
            model.addAttribute("error", "That code didn't match. Check the app shows CredCloud and try the newest code.");
            addSetup(account, request, model);
            return "twostep/setup";
        }
        TwoStepFilter.markVerified(request, account.getId());
        emails.twoStepTurnedOn(account);
        redirect.addFlashAttribute("codes", codes);
        redirect.addFlashAttribute("message", "Two-step sign-in is on.");
        return "redirect:/account/two-step/codes";
    }

    /** Shown once, straight after they're made. */
    @GetMapping("/account/two-step/codes")
    public String codes(Model model) {
        return model.containsAttribute("codes") ? "twostep/codes" : "redirect:/account";
    }

    @PostMapping("/account/two-step/recovery-codes")
    public String newCodes(@AuthenticationPrincipal CredAppUserDetails principal,
                           @RequestParam(defaultValue = "") String currentPassword,
                           RedirectAttributes redirect) {
        User account = users.findById(principal.getUser().getId());
        if (!passwords.matches(currentPassword, account.getPasswordHash())) {
            redirect.addFlashAttribute("twoStepError", "That isn't your current password.");
            return "redirect:/account#two-step";
        }
        if (!twoStep.isEnabled(account.getId())) {
            return "redirect:/account";
        }
        redirect.addFlashAttribute("codes", twoStep.newRecoveryCodes(account.getId()));
        redirect.addFlashAttribute("message", "Your old recovery codes no longer work.");
        return "redirect:/account/two-step/codes";
    }

    @PostMapping("/account/two-step/off")
    public String turnOff(@AuthenticationPrincipal CredAppUserDetails principal,
                          @RequestParam(defaultValue = "") String currentPassword,
                          RedirectAttributes redirect) {
        User account = users.findById(principal.getUser().getId());
        if (twoStep.requiredFor(account.getRole())) {
            redirect.addFlashAttribute("twoStepError",
                    account.getRole().getLabel() + " accounts must use two-step sign-in.");
            return "redirect:/account#two-step";
        }
        if (!passwords.matches(currentPassword, account.getPasswordHash())) {
            redirect.addFlashAttribute("twoStepError", "That isn't your current password.");
            return "redirect:/account#two-step";
        }
        if (twoStep.turnOff(account.getId())) {
            emails.twoStepTurnedOff(account, null);
        }
        redirect.addFlashAttribute("message", "Two-step sign-in is off.");
        return "redirect:/account";
    }

    private void addSetup(User account, HttpServletRequest request, Model model) {
        model.addAttribute("setup", twoStep.setup(account.getId(), account.getEmail()));
        // Sent here by the filter straight after signing in, rather than choosing to come.
        model.addAttribute("required", twoStep.requiredFor(account.getRole())
                && !TwoStepFilter.verified(request, account.getId()));
    }

    private static void signOut(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
