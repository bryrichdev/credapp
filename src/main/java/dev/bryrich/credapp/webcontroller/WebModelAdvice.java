package dev.bryrich.credapp.webcontroller;

import dev.bryrich.credapp.entity.User;
import dev.bryrich.credapp.security.CredAppUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(basePackages = "dev.bryrich.credapp.webcontroller")
public class WebModelAdvice {

    @ModelAttribute("user")
    public User currentUser(@AuthenticationPrincipal CredAppUserDetails principal) {
        return principal == null ? null : principal.getUser();
    }
}