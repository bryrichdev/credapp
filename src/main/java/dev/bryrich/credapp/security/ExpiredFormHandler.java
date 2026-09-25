package dev.bryrich.credapp.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;

/**
 * A form whose CSRF token no longer matches the session: usually a sign-in or register page
 * left open in another tab while you signed in or out, or a page left open past the session
 * timeout. Instead of a bare 403, send the person back to fill it in again. A signed-in
 * request with a bad token on any other form still gets the 403: that's the attack CSRF
 * protection exists for, not an old tab.
 */
class ExpiredFormHandler implements AccessDeniedHandler {

    private final AccessDeniedHandler denied = new AccessDeniedHandlerImpl();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       org.springframework.security.access.AccessDeniedException exception)
            throws IOException, ServletException {
        if (exception instanceof CsrfException) {
            String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
            if (path.equals("/register") || path.startsWith("/password-reset")) {
                response.sendRedirect(request.getContextPath() + path + "?expired");
                return;
            }
            if (path.equals("/login") || !signedIn()) {
                response.sendRedirect(request.getContextPath() + "/login?expired");
                return;
            }
        }
        denied.handle(request, response, exception);
    }

    private static boolean signedIn() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}
