package dev.bryrich.credcloud.runner;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Locale;

/**
 * Where the runner keeps its device token and its Chrome profile. The token lets this computer
 * fetch its owner's jobs until it's revoked in CredCloud; the file is readable only by her.
 * The Chrome profile holds her portal sign-ins, which never leave this computer.
 */
final class Config {

    private static final Gson GSON = new Gson();

    private final Path file;
    private String server;
    private String token;

    private Config(Path file, String server, String token) {
        this.file = file;
        this.server = server;
        this.token = token;
    }

    /** ~/Library/Application Support/CredCloud Runner on a Mac, ~/.credcloud-runner elsewhere. */
    static Path home() {
        String user = System.getProperty("user.home");
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")
                ? Path.of(user, "Library", "Application Support", "CredCloud Runner")
                : Path.of(user, ".credcloud-runner");
    }

    static Config load(Path dir, String server) throws IOException {
        Files.createDirectories(dir);
        Path file = dir.resolve("config.json");
        String token = null;
        if (Files.exists(file)) {
            JsonObject saved = GSON.fromJson(Files.readString(file), JsonObject.class);
            // A token only works on the server that issued it.
            if (saved != null && saved.has("server") && saved.get("server").getAsString().equals(server)
                    && saved.has("token")) {
                token = saved.get("token").getAsString();
            }
        }
        return new Config(file, server, token);
    }

    /** For tests: nothing written to disk. */
    static Config inMemory(String server) {
        return new Config(null, server, null);
    }

    String server() {
        return server;
    }

    String token() {
        return token;
    }

    boolean paired() {
        return token != null && !token.isBlank();
    }

    void token(String value) {
        token = value;
        if (file == null) {
            return;
        }
        try {
            JsonObject saved = new JsonObject();
            saved.addProperty("server", server);
            if (value != null) {
                saved.addProperty("token", value);
            }
            Path temp = file.resolveSibling("config.json.tmp");
            Files.writeString(temp, GSON.toJson(saved), StandardCharsets.UTF_8);
            try {
                Files.setPosixFilePermissions(temp, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException windows) {
                // Not a POSIX file system; the user's profile folder protects it instead.
            }
            Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new IllegalStateException("Couldn't save the runner's settings in " + file, e);
        }
    }
}
