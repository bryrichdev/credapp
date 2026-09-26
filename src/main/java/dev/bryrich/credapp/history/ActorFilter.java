package dev.bryrich.credapp.history;

import dev.bryrich.credapp.security.CredAppUserDetails;
import dev.bryrich.credapp.user.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Notes who is signed in for the rest of the request, so database connections can be stamped
 * with it (see {@link ActorStampingDataSource}).
 *
 * It runs after the security filters, once sign-in is settled. Looking the user up while
 * taking a connection instead would load the session, which takes another connection: a loop.
 */
@Component
class ActorFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CredAppUserDetails principal) {
            User user = principal.getUser();
            CurrentActor.set(new CurrentActor.Actor(user.getId(), user.getEmail()));
        }
        try {
            chain.doFilter(request, response);
        } finally {
            CurrentActor.clear();
        }
    }
}
