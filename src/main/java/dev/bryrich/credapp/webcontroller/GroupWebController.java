package dev.bryrich.credapp.webcontroller;


import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/groups")
public class GroupWebController {
    @GetMapping
    public String groupsList() {
        return "group/list";
    }
}
