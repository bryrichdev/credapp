package dev.bryrich.credapp.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import dev.bryrich.credapp.twostep.TwoStepFilter;
import dev.bryrich.credapp.twostep.TwoStepService;
import dev.bryrich.credapp.user.UserRepository;
import dev.bryrich.credapp.usergroup.ViewedGroup;
import org.springframework.http.HttpMethod;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.util.UrlPathHelper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {

    private static final String[] EDIT_ROLES = {"SUPERUSER", "ADMIN", "COORDINATOR"};

    /**
     * Only the app's own scripts, styles, images and fonts load, and forms post only back to
     * the app or to Cloudflare Access (whose sign-in a protected page redirects to; browsers
     * apply form-action to redirects too). Inline style attributes are allowed for the few
     * computed widths; inline scripts and event handlers are not, so templates wire behaviour
     * through data- attributes and the files in /js. The one exception is scripts carrying
     * this response's nonce, which only Cloudflare adds (see NonceContentSecurityPolicyWriter).
     */
    static String contentSecurityPolicy(String nonce) {
        return String.join("; ",
                "default-src 'self'",
                "script-src 'self' 'nonce-" + nonce + "'",
                "style-src 'self'",
                "style-src-attr 'unsafe-inline'",
                "img-src 'self' data:",
                "font-src 'self'",
                "connect-src 'self'",
                "form-action 'self' https://*.cloudflareaccess.com",
                "frame-ancestors 'none'",
                "base-uri 'self'",
                "object-src 'none'");
    }

    static final String PERMISSIONS_POLICY =
            "camera=(), microphone=(), geolocation=(), payment=(), usb=()";
    private static final RequestMatcher WRITE_REQUEST = request ->
            !java.util.Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod());
    private static final RequestMatcher EDIT_PAGE = request ->
            UrlPathHelper.defaultInstance.getPathWithinApplication(request).matches(".*/(new|edit)(/.*)?")
                    || request.getParameter("edit") != null;

    /**
     * The API CredCloud Helper calls. Signed in by device token only (RunnerTokenFilter); no
     * session, no form login, and a token grants nothing outside /runner/api. Pairing is the one
     * open call: the one-time code in it is the proof.
     */
    @Bean
    @Order(0)
    public SecurityFilterChain runnerSecurityFilterChain(HttpSecurity http,
                                                         dev.bryrich.credapp.portal.RunnerService runners) throws Exception {
        http
                .securityMatcher("/runner/api/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/runner/api/pair").permitAll()
                        .anyRequest().hasRole("RUNNER"))
                .addFilterBefore(new dev.bryrich.credapp.portal.RunnerTokenFilter(runners), AuthorizationFilter.class)
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(
                        (request, response, e) -> response.sendError(401)));
        return http.build();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, UserRepository users) throws Exception {
        http
                .securityMatcher("/api/**", "/actuator/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/api/users/**").hasAnyRole("SUPERUSER", "ADMIN")
                        .requestMatchers(WRITE_REQUEST).hasAnyRole(EDIT_ROLES)
                        .anyRequest().authenticated())
                .addFilterBefore(new AccountStatusFilter(users), AuthorizationFilter.class)
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain webSecurityFilterChain(HttpSecurity http, UserRepository users,
                                                      TwoStepService twoStep) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // The second half of a request that was already let in, such as the live
                        // picture of CredCloud's browser (an SseEmitter) finishing.
                        .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ASYNC).permitAll()
                        .requestMatchers(HttpMethod.GET, "/login", "/register", "/privacy", "/css/**", "/js/**",
                                "/favicon.ico", "/favicon.svg", "/apple-touch-icon.png").permitAll()
                        // Open to everyone, signed in or not: signing in again switches accounts,
                        // and registering never touches the account you're signed in with.
                        .requestMatchers(HttpMethod.POST, "/login", "/register").permitAll()
                        .requestMatchers("/password-reset", "/password-reset/*").permitAll()
                        .requestMatchers("/admin/**").hasAnyRole("SUPERUSER", "ADMIN")
                        // Reveals are reads with a server-generated audit entry, never record edits.
                        .requestMatchers(HttpMethod.POST, "/providers/{id}/ssn", "/owners/{id}/ssn",
                                "/providers/{id}/caqh-password").authenticated()
                        // Your own account, whatever your role; it asks for your current password.
                        .requestMatchers(HttpMethod.POST, "/account").authenticated()
                        // The two-step code at sign-in, and turning two-step on or off: anyone's own.
                        .requestMatchers(HttpMethod.POST, "/login/two-step", "/account/two-step/**").authenticated()
                        .requestMatchers(WRITE_REQUEST).hasAnyRole(EDIT_ROLES)
                        .requestMatchers(EDIT_PAGE).hasAnyRole(EDIT_ROLES)
                        .anyRequest().authenticated())
                .addFilterBefore(new AccountStatusFilter(users), AuthorizationFilter.class)
                .addFilterAfter(new TwoStepFilter(twoStep), AccountStatusFilter.class)
                .addFilterAfter(new GroupViewFilter(), TwoStepFilter.class)
                .formLogin(form -> form
                        .loginPage("/login")
                        // Always land on home. Switching accounts also ends any group the previous
                        // account was viewing, since the session carries over.
                        .successHandler((request, response, authentication) -> {
                            ViewedGroup.stop(request.getSession());
                            // The two-step code comes next, if the account uses one (TwoStepFilter).
                            TwoStepFilter.forget(request.getSession());
                            response.sendRedirect(request.getContextPath() + "/");
                        })
                        .failureHandler((request, response, exception) -> response.sendRedirect(
                                request.getContextPath() + (exception instanceof LockedException
                                        ? "/login?locked" : "/login?error")))
                        .permitAll())
                .exceptionHandling(exceptions -> exceptions.accessDeniedHandler(new ExpiredFormHandler()))
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
                .headers(headers -> headers
                        .addHeaderWriter(new NonceContentSecurityPolicyWriter())
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .permissionsPolicyHeader(permissions -> permissions.policy(PERMISSIONS_POLICY))
                        .frameOptions(frame -> frame.deny())
                        // Sent only over HTTPS, which behind the tunnel is every request.
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000)));
        return http.build();
    }
}
