package dev.bryrich.credapp.portal.remote;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The browsers CredCloud runs for people, one each at most. The server is small, so only a
 * few run at once (credapp.remote-browser.max-sessions); a browser nobody has clicked or typed
 * in for a while, or that has been open too long, is closed.
 */
@Service
public class RemoteBrowsers {

    /** Who a browser belongs to: one person, in the workspace they opened it from. */
    public record Owner(long userId, long workspace) {
    }

    /**
     * A running browser and where it came from.
     *
     * @param label    what it's for, such as "Aetna / Provider enrollment"
     * @param returnTo the CredCloud page to go back to when she's done
     */
    public record Live(RemoteSession session, Owner owner, String label, String returnTo, Instant openedAt) {
    }

    /** No room for another browser right now. */
    public static class BusyException extends RuntimeException {
        BusyException(String message) {
            super(message);
        }
    }

    private static final Logger log = LoggerFactory.getLogger(RemoteBrowsers.class);

    private final Map<String, Live> open = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final int maxSessions;
    private final Duration idle;
    private final Duration longest;
    private final String timezone;
    private final Path chromium;
    private final boolean direct;
    private EgressProxy proxy;

    public RemoteBrowsers(@Value("${credapp.remote-browser.max-sessions:1}") int maxSessions,
                          @Value("${credapp.remote-browser.idle-minutes:15}") long idleMinutes,
                          @Value("${credapp.remote-browser.longest-minutes:120}") long longestMinutes,
                          @Value("${credapp.remote-browser.timezone:America/New_York}") String timezone,
                          @Value("${credapp.remote-browser.chromium:}") String chromium,
                          @Value("${credapp.remote-browser.direct:false}") boolean direct) {
        this.maxSessions = maxSessions;
        this.idle = Duration.ofMinutes(idleMinutes);
        this.longest = Duration.ofMinutes(longestMinutes);
        this.timezone = timezone;
        this.chromium = chromium.isBlank() ? null : Path.of(chromium);
        this.direct = direct;
    }

    /**
     * Starts a browser on the given https page. Anything she already had open closes first.
     *
     * @return the new browser's id, which only she can use
     * @throws BusyException when every browser is taken by someone else
     */
    public synchronized String open(Owner owner, String startUrl, String label, String returnTo) {
        open.values().stream().filter(live -> live.owner().equals(owner)).toList()
                .forEach(live -> end(live.session().id()));
        if (open.size() >= maxSessions) {
            throw new BusyException("CredCloud's browser is being used by someone else right now. "
                    + "Try again in a few minutes.");
        }
        String id = newId();
        RemoteSession session = new RemoteSession(id, startUrl,
                new RemoteSession.Options(1280, 800, timezone, chromium, direct ? null : proxy()));
        open.put(id, new Live(session, owner, label, returnTo, Instant.now()));
        session.start();
        log.info("Opened a remote browser for user {} in workspace {}", owner.userId(), owner.workspace());
        return id;
    }

    /** Her browser with this id. Anyone else's, or one that's gone, is empty. */
    public Optional<Live> find(Owner owner, String id) {
        return Optional.ofNullable(id == null ? null : open.get(id)).filter(live -> live.owner().equals(owner));
    }

    public void close(Owner owner, String id) {
        find(owner, id).ifPresent(live -> end(id));
    }

    /** Closes browsers that ended on their own, sat unused, or have been open too long. */
    @Scheduled(fixedDelay = 60_000)
    public void closeIdle() {
        Instant now = Instant.now();
        for (Live live : List.copyOf(open.values())) {
            RemoteSession session = live.session();
            if (session.status().over() || session.lastUsed().plus(idle).isBefore(now)
                    || live.openedAt().plus(longest).isBefore(now)) {
                end(session.id());
            }
        }
    }

    @PreDestroy
    public synchronized void closeAll() throws IOException {
        List.copyOf(open.keySet()).forEach(this::end);
        if (proxy != null) {
            proxy.close();
            proxy = null;
        }
    }

    private void end(String id) {
        Live live = open.remove(id);
        if (live != null) {
            live.session().close();
        }
    }

    private synchronized String proxy() {
        if (proxy == null) {
            try {
                proxy = EgressProxy.start();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return proxy.address();
    }

    private String newId() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
