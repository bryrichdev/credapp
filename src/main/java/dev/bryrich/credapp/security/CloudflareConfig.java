package dev.bryrich.credapp.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/** Settings for running behind Cloudflare; see CloudflareClientIpFilter. */
@Configuration
public class CloudflareConfig {

    @Bean
    @ConditionalOnProperty(name = "credapp.cloudflare.trust-client-ip", havingValue = "true")
    public FilterRegistrationBean<CloudflareClientIpFilter> cloudflareClientIpFilter() {
        FilterRegistrationBean<CloudflareClientIpFilter> registration =
                new FilterRegistrationBean<>(new CloudflareClientIpFilter());
        // Right after Spring's forwarded-header handling, ahead of security and the app.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }
}
