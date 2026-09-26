package dev.bryrich.credapp.application;

import dev.bryrich.credapp.provider.ProviderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.Map;

@Controller
public class PdfApplicationController {
    private final PdfApplicationService applications;
    private final ApplicationDataService data;
    private final ProviderService providers;
    private final PdfApplicationEngine engine;
    public PdfApplicationController(PdfApplicationService applications, ApplicationDataService data, ProviderService providers, PdfApplicationEngine engine) {
        this.applications = applications; this.data = data; this.providers = providers; this.engine = engine;
    }
    @ModelAttribute
    public void noStore(HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); }

    @GetMapping("/payers/{payerId}/templates")
    public String templates(@PathVariable long payerId, Model model) {
        model.addAttribute("payerId", payerId);
        model.addAttribute("payerName", applications.payerName(payerId));
        model.addAttribute("templates", applications.templates(payerId));
        return "application/templates";
    }
    @PostMapping("/payers/{payerId}/templates")
    public String upload(@PathVariable long payerId, @RequestParam String name,
                         @RequestParam(required = false) MultipartFile file, Model model) {
        try {
            long id = applications.upload(payerId, name, file == null ? null : file.getBytes());
            return "redirect:/application-templates/" + id + "/edit";
        } catch (IllegalArgumentException | IOException ex) {
            model.addAttribute("error", ex instanceof IOException ? "The upload did not come through. Try again." : ex.getMessage());
            model.addAttribute("name", name);
            return templates(payerId, model);
        }
    }
    @GetMapping("/application-templates/{id}/edit")
    public String mapping(@PathVariable long id, Model model) {
        var template = applications.template(id);
        model.addAttribute("template", template);
        model.addAttribute("pageCount", engine.pageCount(template.content()));
        model.addAttribute("sources", data.sources());
        model.addAttribute("formats", AnswerFormat.values());
        return "application/mapping";
    }
    @GetMapping(value = "/application-templates/{id}/preview/{page}", produces = "image/png")
    @ResponseBody
    public byte[] preview(@PathVariable long id, @PathVariable int page) {
        return engine.preview(applications.template(id).content(), page);
    }

    @PostMapping("/application-templates/{id}")
    public String saveMapping(@PathVariable long id, @RequestParam int revision, @RequestParam Map<String, String> params,
                              Model model, RedirectAttributes redirect) {
        try {
            applications.saveMappings(id, revision, params);
            redirect.addFlashAttribute("message", "Template saved. It is ready to use for a provider.");
            return "redirect:/payers/" + applications.template(id).summary().payerId() + "/templates";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("submitted", params);
            return mapping(id, model);
        }
    }
    @GetMapping("/providers/{providerId}/applications")
    public String provider(@PathVariable long providerId, Model model) {
        model.addAttribute("provider", providers.findById(providerId));
        model.addAttribute("templates", applications.templates(null).stream().filter(PdfApplicationService.TemplateSummary::configured).toList());
        model.addAttribute("groups", data.groups(PdfApplicationService.workspace(), providerId));
        model.addAttribute("locations", data.locations(PdfApplicationService.workspace(), providerId));
        model.addAttribute("runs", applications.runs(providerId));
        return "application/provider";
    }
    @PostMapping("/providers/{providerId}/applications")
    public String start(@PathVariable long providerId, @RequestParam long templateId,
                        @RequestParam(required = false) Long groupId, @RequestParam(required = false) Long locationId,
                        HttpServletRequest request, Model model) {
        try {
            long id = applications.start(providerId, templateId, groupId, locationId, request.getRemoteAddr());
            return "redirect:/applications/" + id;
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            return provider(providerId, model);
        }
    }
    @GetMapping("/applications/{id}")
    public String review(@PathVariable long id, Model model) {
        var run = applications.review(id);
        model.addAttribute("run", run);
        model.addAttribute("provider", providers.findById(run.providerId()));
        return "application/review";
    }
    @PostMapping("/applications/{id}")
    public String save(@PathVariable long id, @RequestParam Map<String, String> params, Model model, RedirectAttributes redirect) {
        try {
            applications.save(id, params, "generate".equals(params.get("action")));
            redirect.addFlashAttribute("message", "generate".equals(params.get("action")) ? "PDF generated. Open it to check the final layout before sending." : "Draft saved.");
            return "redirect:/applications/" + id;
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("submitted", params);
            return review(id, model);
        }
    }
}
