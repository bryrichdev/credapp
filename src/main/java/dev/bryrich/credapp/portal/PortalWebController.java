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

/** Runners on the account page, portal templates on a payer, and portal fills on a provider. */
@Controller
public class PortalWebController {

    private final RunnerService runners;
    private final PortalTemplateService portals;
    private final PdfApplicationService payers;
    private final ApplicationDataService data;
    private final ProviderService providers;

    public PortalWebController(RunnerService runners, PortalTemplateService portals, PdfApplicationService payers,
                               ApplicationDataService data, ProviderService providers) {
        this.runners = runners;
        this.portals = portals;
        this.payers = payers;
        this.data = data;
        this.providers = providers;
    }

    @ModelAttribute
    public void noStore(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
    }

    // --- Runners ---

    @GetMapping("/account/runners")
    public String runners(@AuthenticationPrincipal CredAppUserDetails principal, Model model) {
        model.addAttribute("runners", runners.runners(principal.getUser()));
        return "portal/runners";
    }

    @PostMapping("/account/runners")
    public String connect(@AuthenticationPrincipal CredAppUserDetails principal, @RequestParam String name,
                          Model model) {
        try {
            model.addAttribute("pairing", runners.create(principal.getUser(), name));
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
        }
        model.addAttribute("minutes", RunnerService.PAIRING_MINUTES);
        return runners(principal, model);
    }

    @PostMapping("/account/runners/{id}/revoke")
    public String revoke(@AuthenticationPrincipal CredAppUserDetails principal, @PathVariable long id,
                         RedirectAttributes redirect) {
        runners.revoke(principal.getUser(), id);
        redirect.addFlashAttribute("message", "Runner revoked. It can't fetch or fill anything now.");
        return "redirect:/account/runners";
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
            portals.create(payerId, name, startUrl);
            redirect.addFlashAttribute("message", LEARN_MESSAGE);
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
        portals.teach(id);
        redirect.addFlashAttribute("message", LEARN_MESSAGE);
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
            portals.startFill(providerId, templateId, groupId, locationId, request.getRemoteAddr());
            redirect.addFlashAttribute("message", "Sent to your runner. It opens the portal in Chrome; sign in, "
                    + "go to the form, then press Fill this page. It never submits: check the page and submit it yourself.");
            return "redirect:/providers/" + providerId + "/portal-fills";
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

    private static final String LEARN_MESSAGE = "Sent to your runner. It opens the portal in Chrome; sign in, go to "
            + "the form, click each box and choose what goes in it, then press Save template.";
}
