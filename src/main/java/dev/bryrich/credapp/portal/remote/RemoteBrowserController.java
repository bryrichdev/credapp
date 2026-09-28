package dev.bryrich.credapp.portal.remote;

import dev.bryrich.credapp.application.PdfApplicationService;
import dev.bryrich.credapp.portal.PortalTemplateService;
import dev.bryrich.credapp.security.CredAppUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE;

/**
 * The live view of a browser CredCloud runs for her. The page shows the browser as a stream of
 * pictures (server-sent events) and posts her clicks and keys back in small batches.
 */
@Controller
public class RemoteBrowserController {

    private static final Logger log = LoggerFactory.getLogger(RemoteBrowserController.class);
    private static final int MAX_EVENTS = 500;

    private final RemoteBrowsers browsers;
    private final PortalTemplateService portals;

    public RemoteBrowserController(RemoteBrowsers browsers, PortalTemplateService portals) {
        this.browsers = browsers;
        this.portals = portals;
    }

    /** Opens a portal template's start page in CredCloud's browser. */
    @PostMapping("/portal-templates/{id}/live")
    public String open(@AuthenticationPrincipal CredAppUserDetails principal, @PathVariable long id,
                       RedirectAttributes redirect) {
        var template = portals.openable(id);
        String back = "/payers/" + template.payerId() + "/portals";
        try {
            String session = browsers.open(owner(principal), template.startUrl(),
                    template.payerName() + " / " + template.name(), back, null);
            return "redirect:/live/" + session;
        } catch (RemoteBrowsers.BusyException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:" + back;
        }
    }

    /**
     * Opens a portal in CredCloud's browser with one provider's answers, ready for Fill this
     * page. The answers stay in memory with the browser; the job only records the fill.
     */
    @PostMapping("/providers/{providerId}/portal-fills/live")
    public String startFill(@AuthenticationPrincipal CredAppUserDetails principal, @PathVariable long providerId,
                            @RequestParam long templateId, @RequestParam(required = false) Long groupId,
                            @RequestParam(required = false) Long locationId, HttpServletRequest request,
                            RedirectAttributes redirect) {
        String back = "/providers/" + providerId + "/portal-fills";
        PortalTemplateService.LiveFillStart start;
        try {
            start = portals.startLiveFill(providerId, templateId, groupId, locationId, request.getRemoteAddr());
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:" + back;
        }
        Map<Integer, String> answers = new HashMap<>();
        for (PortalTemplateService.Answer answer : start.answers()) {
            answers.put(answer.field(), answer.value() == null ? "" : answer.value());
        }
        long workspace = start.workspace();
        long job = start.jobId();
        var template = start.template();
        LiveFill fill = new LiveFill(template.startUrl(), start.providerName(), start.fields(), answers,
                (filled, missed) -> portals.endLiveFill(workspace, job, filled, missed));
        try {
            String session = browsers.open(owner(principal), template.startUrl(),
                    template.payerName() + " / " + template.name(), back, fill);
            return "redirect:/live/" + session;
        } catch (RemoteBrowsers.BusyException e) {
            fill.end();
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:" + back;
        }
    }

    /** Fills what it can of the page she's on, and says what it did. */
    @PostMapping("/live/{id}/fill")
    @ResponseBody
    public Map<String, Object> fillPage(@AuthenticationPrincipal CredAppUserDetails principal,
                                        @PathVariable String id) throws InterruptedException {
        RemoteBrowsers.Live live = live(principal, id);
        LiveFill fill = live.fill();
        if (fill == null) {
            throw new ResponseStatusException(NOT_FOUND);
        }
        live.session().touch();
        LiveFill.Outcome outcome;
        try {
            outcome = live.session().call(fill::fillOn).get(30, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            outcome = new LiveFill.Outcome("The portal is slow to answer. Let the page finish loading, then press "
                    + "Fill this page again.", true);
        } catch (ExecutionException e) {
            log.info("A portal fill didn't go through", e.getCause());
            outcome = new LiveFill.Outcome("CredCloud couldn't fill this page. Let it finish loading, then press "
                    + "Fill this page again.", true);
        }
        return Map.of("message", outcome.message(), "problem", outcome.problem(),
                "filled", fill.filledIndexes(), "done", fill.filledCount(), "total", fill.total());
    }

    @GetMapping("/live/{id}")
    public String page(@AuthenticationPrincipal CredAppUserDetails principal, @PathVariable String id,
                       HttpServletResponse response, Model model, RedirectAttributes redirect) {
        var live = browsers.find(owner(principal), id);
        if (live.isEmpty()) {
            redirect.addFlashAttribute("message", "That browser has closed. Open the portal again to start a new one.");
            return "redirect:/";
        }
        response.setHeader("Cache-Control", "no-store");
        model.addAttribute("sessionId", id);
        model.addAttribute("label", live.get().label());
        model.addAttribute("returnTo", live.get().returnTo());
        model.addAttribute("fill", live.get().fill());
        model.addAttribute("width", 1280);
        model.addAttribute("height", 800);
        return "portal/live";
    }

    /** The picture and status as they change, until the browser ends or she leaves. */
    @GetMapping(path = "/live/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal CredAppUserDetails principal, @PathVariable String id,
                             HttpServletResponse response) {
        RemoteSession session = live(principal, id).session();
        response.setHeader("Cache-Control", "no-store");
        // Tells a proxy in front (nginx and the like) not to hold events back.
        response.setHeader("X-Accel-Buffering", "no");
        SseEmitter emitter = new SseEmitter(Duration.ofMinutes(30).toMillis());
        Thread.ofVirtual().name("remote-browser-stream-" + id).start(() -> {
            long frame = -1;
            long status = -1;
            try {
                while (true) {
                    RemoteSession.Update update = session.awaitUpdate(frame, status, Duration.ofSeconds(15));
                    boolean sent = false;
                    if (update.statusVersion() != status) {
                        emitter.send(SseEmitter.event().name("status").data(update.status(), MediaType.APPLICATION_JSON));
                        status = update.statusVersion();
                        sent = true;
                    }
                    if (update.frameVersion() != frame && update.frame() != null) {
                        emitter.send(SseEmitter.event().name("frame").data(update.frame()));
                        frame = update.frameVersion();
                        sent = true;
                    }
                    if (update.status().over()) {
                        emitter.complete();
                        return;
                    }
                    if (!sent) {
                        emitter.send(SseEmitter.event().comment("still here"));
                    }
                }
            } catch (IOException | IllegalStateException e) {
                // She closed the tab or the connection dropped; the page reconnects if it's still open.
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                log.warn("Remote browser stream stopped", e);
            }
            try {
                emitter.complete();
            } catch (RuntimeException ignored) {
                // already done
            }
        });
        return emitter;
    }

    @PostMapping(path = "/live/{id}/input", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> input(@AuthenticationPrincipal CredAppUserDetails principal, @PathVariable String id,
                                      @RequestBody List<RemoteInput> events) {
        if (events.size() > MAX_EVENTS) {
            throw new ResponseStatusException(PAYLOAD_TOO_LARGE);
        }
        live(principal, id).session().input(events);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/live/{id}/back")
    public ResponseEntity<Void> back(@AuthenticationPrincipal CredAppUserDetails principal, @PathVariable String id) {
        live(principal, id).session().back();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/live/{id}/reload")
    public ResponseEntity<Void> reload(@AuthenticationPrincipal CredAppUserDetails principal, @PathVariable String id) {
        live(principal, id).session().reload();
        return ResponseEntity.noContent().build();
    }

    /** Ends the browser and goes back to where she came from. */
    @PostMapping("/live/{id}/close")
    public String close(@AuthenticationPrincipal CredAppUserDetails principal, @PathVariable String id,
                        RedirectAttributes redirect) {
        var live = browsers.find(owner(principal), id);
        browsers.close(owner(principal), id);
        LiveFill fill = live.map(RemoteBrowsers.Live::fill).orElse(null);
        redirect.addFlashAttribute("message", fill == null
                ? "Closed CredCloud's browser. It kept nothing from the portal."
                : "Closed CredCloud's browser. It filled " + fill.filledCount() + " of " + fill.total()
                + " boxes for " + fill.providerName() + ", and kept nothing from the portal.");
        return "redirect:" + live.map(RemoteBrowsers.Live::returnTo).orElse("/");
    }

    private RemoteBrowsers.Live live(CredAppUserDetails principal, String id) {
        return browsers.find(owner(principal), id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
    }

    private static RemoteBrowsers.Owner owner(CredAppUserDetails principal) {
        return new RemoteBrowsers.Owner(principal.getUser().getId(), PdfApplicationService.workspace());
    }
}
