package dev.bryrich.credapp.security;

import dev.bryrich.credapp.user.Role;
import dev.bryrich.credapp.usergroup.ViewedGroup;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Keeps a superuser's view of another user group read-only. Pages still load, but nothing
 * that would change that group's records gets through: form posts are refused, and add or
 * edit screens send them back to the page they came from.
 *
 * The few posts that don't change records stay open: leaving the view, switching to
 * another group, signing out, and SSN/CAQH password reveals (which are reads, logged against the
 * superuser in that group's audit trail). The one deliberate write is the spreadsheet
 * import, which is how a superuser onboards a practice on its behalf.
 */
public class GroupViewFilter extends OncePerRequestFilter {

    private static final Set<String> READS = Set.of("GET", "HEAD", "OPTIONS");
    private static final Pattern ALLOWED_POSTS = Pattern.compile(
            "^/(logout|admin/user-groups/view/exit|admin/user-groups/\\d+/view|providers/\\d+/ssn|owners/\\d+/ssn"
                    + "|providers/\\d+/caqh-password"
                    // A superuser onboarding a practice imports into the group they're viewing.
                    + "|admin/import/(preview|confirm|cancel)"
                    // A wipe or delete names its group in the URL and is confirmed with a password.
                    + "|admin/user-groups/\\d+/(wipe|delete)"
                    // Your own account isn't the viewed group's data.
                    + "|account)$");
    private static final Pattern READABLE_PAGE = Pattern.compile("^(/admin)?/[a-z-]+(/\\d+)?$");
    private static final Pattern EDIT_SCREEN = Pattern.compile("^(.*?)/(new|edit)(/.*)?$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Long viewed = ViewedGroup.id(request);
        if (viewed == null) {
            chain.doFilter(request, response);
            return;
        }
        if (!signedInAsSuperuser()) {
            // Demoted or signed out since they started viewing: drop it rather than honour it.
            HttpSession session = request.getSession(false);
            if (session != null) {
                ViewedGroup.stop(session);
            }
            chain.doFilter(request, response);
            return;
        }

        String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        if (!READS.contains(request.getMethod())) {
            if (!ALLOWED_POSTS.matcher(path).matches()) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN,
                        "You're viewing " + ViewedGroup.name(request)
                                + " read-only. Go back to your own group to make changes.");
                return;
            }
        } else {
            var editScreen = EDIT_SCREEN.matcher(path);
            if (editScreen.matches() || request.getParameter("edit") != null) {
                String back = editScreen.matches() ? editScreen.group(1) : path;
                response.sendRedirect(request.getContextPath() + readablePage(back));
                return;
            }
        }
        chain.doFilter(request, response);
    }

    /** "/providers/5" or "/providers" as is; anything deeper goes to that section's list. */
    static String readablePage(String path) {
        if (READABLE_PAGE.matcher(path).matches()) {
            return path;
        }
        int second = path.indexOf('/', 1);
        return second > 0 ? path.substring(0, second) : "/";
    }

    private static boolean signedInAsSuperuser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof CredAppUserDetails principal
                && principal.isEnabled() && principal.getUser().getRole() == Role.SUPERUSER;
    }
}
