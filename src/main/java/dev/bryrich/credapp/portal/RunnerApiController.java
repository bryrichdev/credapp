package dev.bryrich.credapp.portal;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the runner calls. Under /runner/api, not /api: Cloudflare blocks /api/*, which is the
 * app's own JSON API. Only GET and POST, which Cloudflare lets through. See SecurityConfig for
 * how requests here are signed in.
 */
@RestController
@RequestMapping("/runner/api")
public class RunnerApiController {

    public record PairRequest(String code) {
    }

    public record PairResponse(String token) {
    }

    public record FillResult(List<String> filled, List<String> missed) {
    }

    public record LearnResult(String startUrl, List<PortalField> fields) {
    }

    /** Pairing attempts allowed per address in {@link #WINDOW}. A code is 50 random bits anyway. */
    static final int PAIR_ATTEMPTS = 10;
    static final java.time.Duration WINDOW = java.time.Duration.ofMinutes(10);

    private final RunnerService runners;
    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

    public RunnerApiController(RunnerService runners) {
        this.runners = runners;
    }

    @PostMapping("/pair")
    public ResponseEntity<?> pair(@RequestBody PairRequest request, HttpServletRequest http) {
        if (!allowed(http.getRemoteAddr())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("error", "Too many tries. Wait 10 minutes."));
        }
        return runners.pair(request.code())
                .<ResponseEntity<?>>map(token -> ResponseEntity.ok(new PairResponse(token)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "That code is wrong, used or expired. Make a new one in CredCloud.")));
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

    /** The coordinator reads this in the runner's panel, so it's written for her. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalid(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    private boolean allowed(String address) {
        Instant now = Instant.now();
        Deque<Instant> recent = attempts.computeIfAbsent(address, a -> new ArrayDeque<>());
        synchronized (recent) {
            while (!recent.isEmpty() && recent.peekFirst().isBefore(now.minus(WINDOW))) {
                recent.pollFirst();
            }
            if (recent.size() >= PAIR_ATTEMPTS) {
                return false;
            }
            recent.addLast(now);
            return true;
        }
    }
}
