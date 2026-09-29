package dev.bryrich.credapp.portal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CredCloud Helper's builds, one per kind of computer, which CredCloud serves for installing
 * and for the helper to update itself. The Docker image builds them into credapp.helper.dir
 * (see the Dockerfile); in development, helper/build.sh puts them in helper/dist. Each build's
 * SHA-256 is what a helper compares its own with, so it updates exactly when the build changes.
 */
@Component
public class HelperFiles {

    /** The helper's platform (Go's GOOS-GOARCH) and the file built for it. */
    static final Map<String, String> BUILDS = Map.of(
            "darwin-arm64", "credcloud-helper-darwin-arm64",
            "darwin-amd64", "credcloud-helper-darwin-amd64",
            "windows-amd64", "credcloud-helper-windows-amd64.exe");

    private record Checksum(FileTime modified, long size, String sha256) {
    }

    private final Path dir;
    private final Map<String, Checksum> checksums = new ConcurrentHashMap<>();

    public HelperFiles(@Value("${credapp.helper.dir:}") String dir) {
        this.dir = dir == null || dir.isBlank() ? null : Path.of(dir);
    }

    /** A build by its file name, if it's one of the helper's and it's here. */
    public Optional<Path> file(String name) {
        if (dir == null || !BUILDS.containsValue(name)) {
            return Optional.empty();
        }
        Path path = dir.resolve(name);
        return Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
    }

    /** The SHA-256 of the build for a platform, such as darwin-arm64, if there is one. */
    public Optional<String> sha256(String platform) {
        String name = platform == null ? null : BUILDS.get(platform);
        return name == null ? Optional.empty() : file(name).map(this::checksum);
    }

    public boolean available() {
        return BUILDS.values().stream().anyMatch(name -> file(name).isPresent());
    }

    private String checksum(Path path) {
        try {
            FileTime modified = Files.getLastModifiedTime(path);
            long size = Files.size(path);
            Checksum known = checksums.get(path.toString());
            if (known != null && known.modified().equals(modified) && known.size() == size) {
                return known.sha256();
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new DigestInputStream(Files.newInputStream(path), digest)) {
                in.transferTo(java.io.OutputStream.nullOutputStream());
            }
            String sha256 = HexFormat.of().formatHex(digest.digest());
            checksums.put(path.toString(), new Checksum(modified, size, sha256));
            return sha256;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
