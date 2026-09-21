package dev.bryrich.credapp.webcontroller;


import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/users")
public class UserWebController {
    @GetMapping
    public String usersList() {
        return "admin/users";
    }
}
