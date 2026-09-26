package dev.bryrich.credapp.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Short forms open in a dialog over the page (see modal.js), which loads and submits them
 * with fetch. A browser's fetch would follow a redirect itself, and the page it landed on
 * would use up the flash message meant for the reader. So for those requests a redirect is
 * turned into a 204 carrying the destination in a header, and the script navigates there.
 * Every other request, and every request without JavaScript, is untouched.
 */
public class ModalRequestFilter extends OncePerRequestFilter {

    public static final String REQUEST_HEADER = "X-Modal";
    public static final String REDIRECT_HEADER = "X-Modal-Redirect";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!"1".equals(request.getHeader(REQUEST_HEADER))) {
            chain.doFilter(request, response);
            return;
        }
        chain.doFilter(request, new HttpServletResponseWrapper(response) {
            @Override
            public void sendRedirect(String location) {
                redirect(location);
            }

            @Override
            public void sendRedirect(String location, int status) {
                redirect(location);
            }

            @Override
            public void sendRedirect(String location, boolean clearBuffer) {
                redirect(location);
            }

            @Override
            public void sendRedirect(String location, int status, boolean clearBuffer) {
                redirect(location);
            }

            private void redirect(String location) {
                resetBuffer();
                setStatus(HttpServletResponse.SC_NO_CONTENT);
                setHeader(REDIRECT_HEADER, location);
            }
        });
    }
}
