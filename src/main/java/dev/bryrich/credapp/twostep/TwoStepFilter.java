package dev.bryrich.credapp.twostep;

import dev.bryrich.credapp.security.CredAppUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Holds a session that's past the password at the two-step prompt until the code checks out.
 * Until then it can reach only the code page (or, for an admin who hasn't set two-step up
 * yet, the setup page), sign out, or sign in as someone else.
 *
 * A session counts as through once it's been marked for this very account, so signing in as
 * someone else in the same browser starts over.
 */
public class TwoStepFilter extends OncePerRequestFilter {

    static final String VERIFIED = "credapp.twoStep.verifiedUser";
    static final String PROMPT = "/login/two-step";
    static final String SETUP = "/account/two-step/setup";

    private static final Pattern ALWAYS_OPEN = Pattern.compile(
            "^/(login|logout|register|error|favicon\\.ico|favicon\\.svg|apple-touch-icon\\.png)$"
                    + "|^/(css|js)/.*|^/password-reset(/.*)?$");

    private final TwoStepService twoStep;

    public TwoStepFilter(TwoStepService twoStep) {
        this.twoStep = twoStep;
    }

    /** Marks this session as through two-step sign-in for the account. */
    static void markVerified(HttpServletRequest request, long userId) {
        request.getSession().setAttribute(VERIFIED, userId);
    }

    /** Called at every password sign-in: the new account has to earn it again. */
    public static void forget(HttpSession session) {
        session.removeAttribute(VERIFIED);
    }

    static boolean verified(HttpServletRequest request, long userId) {
        HttpSession session = request.getSession(false);
        return session != null && Long.valueOf(userId).equals(session.getAttribute(VERIFIED));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication != null && authentication.getPrincipal() instanceof CredAppUserDetails principal)) {
            chain.doFilter(request, response);
            return;
        }
        long userId = principal.getUser().getId();
        String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        if (ALWAYS_OPEN.matcher(path).matches() || verified(request, userId)) {
            chain.doFilter(request, response);
            return;
        }
        if (twoStep.isEnabled(userId)) {
            if (path.equals(PROMPT)) {
                chain.doFilter(request, response);
            } else {
                response.sendRedirect(request.getContextPath() + PROMPT);
            }
            return;
        }
        if (twoStep.requiredFor(principal.getUser().getRole())) {
            if (path.startsWith("/account/two-step/")) {
                chain.doFilter(request, response);
            } else {
                response.sendRedirect(request.getContextPath() + SETUP);
            }
            return;
        }
        chain.doFilter(request, response);
    }
}
