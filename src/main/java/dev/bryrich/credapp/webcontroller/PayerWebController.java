package dev.bryrich.credapp.webcontroller;


import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/payers")
public class PayerWebController {
    @GetMapping
    public String payersList() {
        return "payer/list";
    }
}
