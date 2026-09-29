package dev.bryrich.credapp.portal;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What CredCloud Helper calls. Under /runner/api, not /api: Cloudflare blocks /api/*, which is
 * the app's own JSON API. Only GET and POST, which Cloudflare lets through. Everything but
 * pairing needs the helper's device token; see SecurityConfig.
 */
@RestController
@RequestMapping("/runner/api")
public class RunnerApiController {

    public record FillResult(List<String> filled, List<String> missed) {
    }

    public record LearnResult(String startUrl, List<PortalField> fields) {
    }

    public record PairRequest(String code, String name) {
    }

    public record CancelRequest(String reason) {
    }

    private final RunnerService runners;
    private final HelperFiles builds;

    public RunnerApiController(RunnerService runners, HelperFiles builds) {
        this.runners = runners;
        this.builds = builds;
    }

    /**
     * Connects a helper with a one-time code from CredCloud. Open to anyone: the code is what
     * proves who it's for.
     */
    @PostMapping("/pair")
    public RunnerService.Paired pair(@RequestBody PairRequest request) {
        return runners.pair(request.code(), request.name());
    }

    /**
     * 204 when there's nothing to do. The helper asks again every couple of seconds, listing the
     * jobs it already has open in skip (such as skip=12,15). X-Helper-Sha256 is the checksum of
     * the current build for its kind of computer: when its own differs, it updates itself.
     */
    @GetMapping("/jobs/next")
    public ResponseEntity<RunnerService.JobForRunner> next(@AuthenticationPrincipal RunnerService.Identity runner,
                                                           @RequestParam(required = false) String skip,
                                                           @RequestHeader(value = "X-CredCloud-Helper", required = false)
                                                           String platform) {
        HttpHeaders headers = new HttpHeaders();
        builds.sha256(platform).ifPresent(sha256 -> headers.set("X-Helper-Sha256", sha256));
        return runners.next(runner, open(skip))
                .map(job -> ResponseEntity.ok().headers(headers).body(job))
                .orElseGet(() -> ResponseEntity.noContent().headers(headers).build());
    }

    /** At most 50 job ids; anything that isn't one is ignored. */
    static Set<Long> open(String skip) {
        if (skip == null || skip.isBlank()) {
            return Set.of();
        }
        Set<Long> ids = new HashSet<>();
        for (String part : skip.split(",", 51)) {
            try {
                ids.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException ignored) {
                // not a job id
            }
            if (ids.size() == 50) {
                break;
            }
        }
        return ids;
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

    /** The body is optional: {"reason": "…"} says why, for the page she started the job from. */
    @PostMapping("/jobs/{id}/cancel")
    public ResponseEntity<Void> cancel(@AuthenticationPrincipal RunnerService.Identity runner, @PathVariable long id,
                                       @RequestBody(required = false) CancelRequest request) {
        runners.cancel(runner, id, request == null ? null : request.reason());
        return ResponseEntity.noContent().build();
    }

    /** The coordinator reads this in the helper's panel, so it's written for her. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalid(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
