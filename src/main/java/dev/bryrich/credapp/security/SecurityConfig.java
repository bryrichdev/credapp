package dev.bryrich.credapp.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import dev.bryrich.credapp.user.UserRepository;
import org.springframework.http.HttpMethod;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.util.UrlPathHelper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {

    private static final String[] EDIT_ROLES = {"SUPERUSER", "ADMIN", "COORDINATOR"};
    private static final RequestMatcher WRITE_REQUEST = request ->
            !java.util.Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod());
    private static final RequestMatcher EDIT_PAGE = request ->
            UrlPathHelper.defaultInstance.getPathWithinApplication(request).matches(".*/(new|edit)(/.*)?")
                    || request.getParameter("edit") != null;

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
    public SecurityFilterChain webSecurityFilterChain(HttpSecurity http, UserRepository users) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/login", "/register", "/css/**", "/js/**",
                                "/favicon.ico", "/favicon.svg", "/apple-touch-icon.png").permitAll()
                        .requestMatchers(HttpMethod.POST, "/login", "/register").anonymous()
                        .requestMatchers("/admin/**").hasAnyRole("SUPERUSER", "ADMIN")
                        // Reveals are reads with a server-generated audit entry, never record edits.
                        .requestMatchers(HttpMethod.POST, "/providers/{id}/ssn", "/owners/{id}/ssn",
                                "/providers/{id}/caqh-password").authenticated()
                        // Your own account, whatever your role; it asks for your current password.
                        .requestMatchers(HttpMethod.POST, "/account").authenticated()
                        .requestMatchers(WRITE_REQUEST).hasAnyRole(EDIT_ROLES)
                        .requestMatchers(EDIT_PAGE).hasAnyRole(EDIT_ROLES)
                        .anyRequest().authenticated())
                .addFilterBefore(new AccountStatusFilter(users), AuthorizationFilter.class)
                .addFilterAfter(new GroupViewFilter(), AccountStatusFilter.class)
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"));
        return http.build();
    }
}
