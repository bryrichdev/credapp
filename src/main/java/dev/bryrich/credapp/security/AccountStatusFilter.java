package dev.bryrich.credapp.security;

import dev.bryrich.credapp.user.UserRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/** Apply role changes and account disablement to already signed-in sessions. */
public class AccountStatusFilter extends OncePerRequestFilter {
    private final UserRepository users;

    public AccountStatusFilter(UserRepository users) { this.users = users; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CredAppUserDetails principal) {
            var current = users.findById(principal.getUser().getId()).orElse(null);
            if (current == null || !current.isEnabled()) {
                SecurityContextHolder.clearContext();
                var session = request.getSession(false);
                if (session != null) { session.invalidate(); }
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "This account does not have access");
                return;
            }
            var details = new CredAppUserDetails(current);
            var refreshed = UsernamePasswordAuthenticationToken.authenticated(details, null, details.getAuthorities());
            refreshed.setDetails(authentication.getDetails());
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(refreshed);
            SecurityContextHolder.setContext(context);
        }
        chain.doFilter(request, response);
    }
}
