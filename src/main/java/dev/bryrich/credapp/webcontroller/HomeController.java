package dev.bryrich.credapp.webcontroller;

import dev.bryrich.credapp.security.CredAppUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home(@AuthenticationPrincipal CredAppUserDetails principal, Model model) {
        model.addAttribute("user", principal.getUser());
        return "index";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}