package dev.bryrich.credapp.portal;

import dev.bryrich.credapp.application.ApplicationDataService;
import dev.bryrich.credapp.application.PdfApplicationService;
import dev.bryrich.credapp.provider.ProviderService;
import dev.bryrich.credapp.security.CredAppUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
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
 * Portal templates on a payer, portal fills on a provider, and connecting the CredCloud
 * extension. A page that queues a job carries a data-credcloud-job-ready marker; the extension
 * sees it and picks the job up at once.
 */
@Controller
public class PortalWebController {

    private final RunnerService browsers;
    private final PortalTemplateService portals;
    private final PdfApplicationService payers;
    private final ApplicationDataService data;
    private final ProviderService providers;
    private final String installUrl;
    private final String baseUrl;

    public PortalWebController(RunnerService browsers, PortalTemplateService portals, PdfApplicationService payers,
                               ApplicationDataService data, ProviderService providers,
                               @Value("${credapp.extension.install-url:}") String installUrl,
                               @Value("${credapp.base-url}") String baseUrl) {
        this.browsers = browsers;
        this.portals = portals;
        this.payers = payers;
        this.data = data;
        this.providers = providers;
        this.installUrl = installUrl;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    @ModelAttribute
    public void common(HttpServletResponse response, Model model) {
        response.setHeader("Cache-Control", "no-store");
        model.addAttribute("installUrl", installUrl);
    }

    // --- Connecting the extension ---

    @GetMapping("/extension/connect")
    public String connectPage() {
        return "portal/connect";
    }

    /** Makes a token for this browser. The page hands it to the extension and it's never shown. */
    @PostMapping("/extension/connect")
    public String connect(@AuthenticationPrincipal CredAppUserDetails principal, HttpServletRequest request,
                          Model model) {
        model.addAttribute("token", browsers.connect(principal.getUser(), browserName(request.getHeader("User-Agent"))));
        return "portal/connect";
    }

    // --- Connecting CredCloud Helper ---

    /** A one-time code that connects CredCloud Helper on a computer to this account. */
    @PostMapping("/helper/code")
    public String helperCode(@AuthenticationPrincipal CredAppUserDetails principal, Model model) {
        var code = browsers.pairingCode(principal.getUser());
        model.addAttribute("code", code.code());
        model.addAttribute("expiresAt", code.expiresAt());
        model.addAttribute("server", baseUrl);
        return "portal/helper-code";
    }

    @GetMapping("/account/browsers")
    public String connected(@AuthenticationPrincipal CredAppUserDetails principal, Model model) {
        model.addAttribute("browsers", browsers.runners(principal.getUser()));
        return "portal/browsers";
    }

    @PostMapping("/account/browsers/{id}/revoke")
    public String revoke(@AuthenticationPrincipal CredAppUserDetails principal, @PathVariable long id,
                         RedirectAttributes redirect) {
        browsers.revoke(principal.getUser(), id);
        redirect.addFlashAttribute("message", "Disconnected. That browser can't fetch or fill anything now.");
        return "redirect:/account/browsers";
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
            jobReady(redirect, LEARN_MESSAGE);
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
        jobReady(redirect, LEARN_MESSAGE);
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
            jobReady(redirect, "Opening the portal in a new tab. Sign in, go to the form, then press Fill this "
                    + "page in the CredCloud panel. It never submits: check each page and submit it yourself.");
            return "redirect:/providers/" + providerId + "/portal-fills";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return fills(providerId, model);
        }
    }

    /** The answers a fill would type, to copy by hand, for a browser without the extension. */
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

    private static void jobReady(RedirectAttributes redirect, String message) {
        redirect.addFlashAttribute("message", message);
        redirect.addFlashAttribute("jobReady", true);
    }

    /** "Chrome on macOS" and the like, so she can tell her browsers apart on the account page. */
    static String browserName(String userAgent) {
        String agent = userAgent == null ? "" : userAgent;
        String browser = agent.contains("Edg/") ? "Edge" : agent.contains("Chrome/") ? "Chrome" : "Browser";
        String system = agent.contains("Mac OS X") ? "macOS" : agent.contains("Windows") ? "Windows"
                : agent.contains("CrOS") ? "ChromeOS" : agent.contains("Linux") ? "Linux" : "";
        return system.isEmpty() ? browser : browser + " on " + system;
    }

    private static final String LEARN_MESSAGE = "Opening the portal in a new tab. Sign in, go to the form, press "
            + "Pick a box in the CredCloud panel, and click each box to choose what goes in it. Then press Save template.";
}
