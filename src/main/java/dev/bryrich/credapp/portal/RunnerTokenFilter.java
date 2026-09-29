package dev.bryrich.credapp.portal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Signs CredCloud Helper in from its {@code Authorization: Bearer <token>} header, for the runner API
 * only. The principal is the {@link RunnerService.Identity}, never the user, so a token can't
 * reach any of the app's pages.
 */
public class RunnerTokenFilter extends OncePerRequestFilter {

    private final RunnerService runners;

    public RunnerTokenFilter(RunnerService runners) {
        this.runners = runners;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            runners.authenticate(header.substring(7).trim(), request.getHeader("X-CredCloud-Helper")).ifPresent(identity -> {
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                        identity, null, AuthorityUtils.createAuthorityList("ROLE_RUNNER")));
                SecurityContextHolder.setContext(context);
            });
        }
        chain.doFilter(request, response);
    }
}
