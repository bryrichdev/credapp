package dev.bryrich.credapp.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.header.HeaderWriter;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Writes the Content-Security-Policy with a fresh random nonce in script-src on every
 * response. The app's own scripts are all files under /js and don't need it. It's there for
 * Cloudflare: Bot Fight Mode injects small inline scripts (JavaScript detections), and
 * Cloudflare adds the nonce it finds in this header to them, so they run while any other
 * inline script is still blocked.
 */
public class NonceContentSecurityPolicyWriter implements HeaderWriter {

    static final String HEADER = "Content-Security-Policy";

    private final SecureRandom random = new SecureRandom();

    @Override
    public void writeHeaders(HttpServletRequest request, HttpServletResponse response) {
        if (!response.containsHeader(HEADER)) {
            response.setHeader(HEADER, SecurityConfig.contentSecurityPolicy(nonce()));
        }
    }

    private String nonce() {
        byte[] bytes = new byte[18];
        random.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
