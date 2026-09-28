package dev.bryrich.credapp.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** The privacy policy, for CredCloud and CredCloud for Chrome. Open to everyone. */
@Controller
public class PrivacyController {

    private final String contactEmail;

    public PrivacyController(@Value("${credapp.contact-email:}") String contactEmail) {
        this.contactEmail = contactEmail;
    }

    @GetMapping("/privacy")
    public String privacy(Model model) {
        model.addAttribute("contactEmail", contactEmail);
        return "privacy";
    }
}
