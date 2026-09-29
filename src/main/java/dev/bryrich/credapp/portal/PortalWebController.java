package dev.bryrich.credapp.portal;

import dev.bryrich.credapp.application.ApplicationDataService;
import dev.bryrich.credapp.application.PdfApplicationService;
import dev.bryrich.credapp.provider.ProviderService;
import dev.bryrich.credapp.security.CredAppUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Portal templates on a payer, and portal fills on a provider. Teaching or filling queues a
 * job for CredCloud Helper on the coordinator's computer; the page it lands on starts the
 * helper if it isn't running and follows the job (helper-launch.js).
 */
@Controller
public class PortalWebController {

    private final PortalTemplateService portals;
    private final PdfApplicationService payers;
    private final ApplicationDataService data;
    private final ProviderService providers;
    private final HelperController helper;

    public PortalWebController(PortalTemplateService portals, PdfApplicationService payers,
                               ApplicationDataService data, ProviderService providers, HelperController helper) {
        this.portals = portals;
        this.payers = payers;
        this.data = data;
        this.providers = providers;
        this.helper = helper;
    }

    @ModelAttribute
    public void common(@AuthenticationPrincipal CredAppUserDetails principal, HttpServletResponse response,
                       Model model) {
        response.setHeader("Cache-Control", "no-store");
        if (principal != null) {
            model.addAttribute("helper", helper.view(principal));
        }
    }

    // --- Portal templates on a payer ---

    @GetMapping("/payers/{payerId}/portals")
    public String templates(@PathVariable long payerId, Model model) {
        model.addAttribute("payerId", payerId);
        model.addAttribute("payerName", payers.payerName(payerId));
        model.addAttribute("templates", portals.templates(payerId));
        return "portal/templates";
    }

    @PostMapping("/payers/{payerId}/portals")
    public String create(@PathVariable long payerId, @RequestParam String name, @RequestParam String startUrl,
                         Model model, RedirectAttributes redirect) {
        try {
            jobReady(redirect, portals.create(payerId, name, startUrl), LEARN_MESSAGE);
            return "redirect:/payers/" + payerId + "/portals";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("name", name);
            model.addAttribute("startUrl", startUrl);
            return templates(payerId, model);
        }
    }

    @PostMapping("/portal-templates/{id}/teach")
    public String teach(@PathVariable long id, RedirectAttributes redirect) {
        jobReady(redirect, portals.teach(id), LEARN_MESSAGE);
        return "redirect:/payers/" + portals.template(id).summary().payerId() + "/portals";
    }

    // --- Portal fills on a provider ---

    @GetMapping("/providers/{providerId}/portal-fills")
    public String fills(@PathVariable long providerId, Model model) {
        model.addAttribute("provider", providers.findById(providerId));
        model.addAttribute("templates", portals.templates(null).stream()
                .filter(PortalTemplateService.TemplateSummary::ready).toList());
        model.addAttribute("groups", data.groups(PdfApplicationService.workspace(), providerId));
        model.addAttribute("locations", data.locations(PdfApplicationService.workspace(), providerId));
        model.addAttribute("fills", portals.fills(providerId));
        return "portal/provider";
    }

    @PostMapping("/providers/{providerId}/portal-fills")
    public String fill(@PathVariable long providerId, @RequestParam long templateId,
                       @RequestParam(required = false) Long groupId, @RequestParam(required = false) Long locationId,
                       HttpServletRequest request, Model model, RedirectAttributes redirect) {
        try {
            jobReady(redirect, portals.startFill(providerId, templateId, groupId, locationId, request.getRemoteAddr()),
                    "Sign in to the portal, open the form, then press Fill this page in the CredCloud panel. "
                            + "It never submits: check each page and submit it yourself.");
            return "redirect:/providers/" + providerId + "/portal-fills";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return fills(providerId, model);
        }
    }

    /** The answers a fill would type, to copy by hand, without CredCloud Helper. */
    @PostMapping("/providers/{providerId}/portal-fills/copy")
    public String copy(@PathVariable long providerId, @RequestParam long templateId,
                       @RequestParam(required = false) Long groupId, @RequestParam(required = false) Long locationId,
                       HttpServletRequest request, Model model) {
        try {
            model.addAttribute("provider", providers.findById(providerId));
            model.addAttribute("copy", portals.copy(providerId, templateId, groupId, locationId, request.getRemoteAddr()));
            return "portal/copy";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return fills(providerId, model);
        }
    }

    @PostMapping("/portal-fills/{id}/cancel")
    public String cancel(@PathVariable long id, @RequestParam long providerId, RedirectAttributes redirect) {
        portals.cancel(id);
        redirect.addFlashAttribute("message", "Cancelled. The answers were cleared.");
        return "redirect:/providers/" + providerId + "/portal-fills";
    }

    /** The page follows this job while CredCloud Helper opens it. */
    private static void jobReady(RedirectAttributes redirect, long jobId, String message) {
        redirect.addFlashAttribute("message", message);
        redirect.addFlashAttribute("helperJob", jobId);
    }

    private static final String LEARN_MESSAGE = "Sign in to the portal and open the form. Press Pick a box in the "
            + "CredCloud panel, click each box, and choose what goes in it. Then press Save template.";
}
