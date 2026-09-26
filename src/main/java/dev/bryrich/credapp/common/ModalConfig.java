package dev.bryrich.credapp.common;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ModalConfig {

    /** Outside Spring Security (order -100), so its redirects, like to sign-in, are caught too. */
    @Bean
    public FilterRegistrationBean<ModalRequestFilter> modalRequestFilter() {
        FilterRegistrationBean<ModalRequestFilter> registration = new FilterRegistrationBean<>(new ModalRequestFilter());
        registration.setOrder(-105);
        return registration;
    }
}
