package dev.bryrich.credapp.portal;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * What the CredCloud extension calls. Under /runner/api, not /api: Cloudflare blocks /api/*,
 * which is the app's own JSON API. Only GET and POST, which Cloudflare lets through. See
 * SecurityConfig for how requests here are signed in.
 */
@RestController
@RequestMapping("/runner/api")
public class RunnerApiController {

    public record FillResult(List<String> filled, List<String> missed) {
    }

    public record LearnResult(String startUrl, List<PortalField> fields) {
    }

    private final RunnerService runners;

    public RunnerApiController(RunnerService runners) {
        this.runners = runners;
    }

    /** 204 when there's nothing to do. The runner asks again every few seconds. */
    @GetMapping("/jobs/next")
    public ResponseEntity<RunnerService.JobForRunner> next(@AuthenticationPrincipal RunnerService.Identity runner) {
        return runners.next(runner).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/jobs/{id}/filled")
    public ResponseEntity<Void> filled(@AuthenticationPrincipal RunnerService.Identity runner, @PathVariable long id,
                                       @RequestBody FillResult result) {
        runners.finishFill(runner, id, result.filled(), result.missed());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/jobs/{id}/learned")
    public Map<String, Integer> learned(@AuthenticationPrincipal RunnerService.Identity runner, @PathVariable long id,
                                        @RequestBody LearnResult result) {
        return Map.of("revision", runners.finishLearn(runner, id, result.startUrl(), result.fields()));
    }

    @PostMapping("/jobs/{id}/cancel")
    public ResponseEntity<Void> cancel(@AuthenticationPrincipal RunnerService.Identity runner, @PathVariable long id) {
        runners.cancel(runner, id);
        return ResponseEntity.noContent().build();
    }

    /** The coordinator reads this in the extension's panel, so it's written for her. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalid(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
