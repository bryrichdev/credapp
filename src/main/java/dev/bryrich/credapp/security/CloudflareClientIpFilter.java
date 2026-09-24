package dev.bryrich.credapp.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Behind Cloudflare, the visitor's address arrives in CF-Connecting-IP, which Cloudflare's
 * edge sets and a visitor can't override (unlike X-Forwarded-For, whose first entry is
 * whatever the client sent). This makes it the request's remote address, so the SSN and
 * CAQH access logs record who actually asked.
 *
 * Only switch it on (credapp.cloudflare.trust-client-ip=true) when Cloudflare is the only
 * way in, as with the tunnel and no published ports; otherwise anyone reaching the app
 * directly could put any address in the header.
 */
public class CloudflareClientIpFilter extends OncePerRequestFilter {

    static final String HEADER = "CF-Connecting-IP";

    /** IPv4 or IPv6 characters only; anything else is ignored rather than trusted. */
    private static final Pattern ADDRESS = Pattern.compile("[0-9A-Fa-f.:]{2,45}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String client = request.getHeader(HEADER);
        if (client == null || !ADDRESS.matcher(client.trim()).matches()) {
            chain.doFilter(request, response);
            return;
        }
        String address = client.trim();
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override
            public String getRemoteAddr() {
                return address;
            }

            @Override
            public String getRemoteHost() {
                return address;
            }
        }, response);
    }
}
