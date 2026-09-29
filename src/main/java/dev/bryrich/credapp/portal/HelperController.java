package dev.bryrich.credapp.portal;

import dev.bryrich.credapp.security.CredAppUserDetails;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CredCloud Helper from CredCloud's side: the page to set it up and see connected computers,
 * the one-line install commands with their scripts, the builds, connecting an installed helper,
 * and the status CredCloud's pages poll while a portal job opens.
 * <p>
 * The install scripts and builds are open to anyone: Terminal and PowerShell fetch them with
 * no session. A script only works with a live one-time code, and a build is the same for
 * everyone.
 */
@Controller
public class HelperController {

    /** What CredCloud's pages need to know to start the helper: see helper-launch.js. */
    public record HelperView(boolean connected, boolean running, String openUrl) {
    }

    private static final String CODE = "[A-Za-z0-9_-]{43}";

    private final RunnerService runners;
    private final HelperFiles builds;
    private final String server;

    public HelperController(RunnerService runners, HelperFiles builds, @Value("${credapp.base-url}") String baseUrl) {
        this.runners = runners;
        this.builds = builds;
        this.server = baseUrl.replaceAll("/+$", "");
    }

    /** The credcloud:// link that starts the helper and points it at this CredCloud. */
    public String openUrl() {
        return "credcloud://open?server=" + URLEncoder.encode(server, StandardCharsets.UTF_8);
    }

    public HelperView view(CredAppUserDetails principal) {
        var status = runners.status(principal.getUser(), null);
        return new HelperView(status.connected(), status.running(), openUrl());
    }

    @ModelAttribute
    public void noStore(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
    }

    // --- Setting it up ---

    @GetMapping("/helper")
    public String page(@AuthenticationPrincipal CredAppUserDetails principal,
                       @RequestHeader(value = "User-Agent", required = false) String userAgent, Model model) {
        model.addAttribute("computers", runners.runners(principal.getUser()));
        model.addAttribute("system", system(userAgent));
        model.addAttribute("buildsAvailable", builds.available());
        return "helper/index";
    }

    /** The old address of the connected computers list. */
    @GetMapping("/account/browsers")
    public String oldPage() {
        return "redirect:/helper";
    }

    /** A one-time install command for this account, for a Mac and for Windows. */
    @PostMapping("/helper/setup")
    public String setup(@AuthenticationPrincipal CredAppUserDetails principal,
                        @RequestHeader(value = "User-Agent", required = false) String userAgent, Model model) {
        var code = runners.pairingCode(principal.getUser());
        model.addAttribute("macCommand", "curl -fsSL " + server + "/helper/install/" + code.code() + ".sh | sh");
        model.addAttribute("windowsCommand", "irm " + server + "/helper/install/" + code.code() + ".ps1 | iex");
        model.addAttribute("expiresAt", code.expiresAt());
        return page(principal, userAgent, model);
    }

    @PostMapping("/helper/computers/{id}/revoke")
    public String revoke(@AuthenticationPrincipal CredAppUserDetails principal, @PathVariable long id,
                         RedirectAttributes redirect) {
        runners.revoke(principal.getUser(), id);
        redirect.addFlashAttribute("message", "Disconnected. That computer can't fetch or fill anything now.");
        return "redirect:/helper";
    }

    // --- Connecting a helper that's installed ---

    /** Where a helper that isn't connected sends her, in her usual browser. */
    @GetMapping("/helper/connect")
    public String connectPage() {
        return "helper/connect";
    }

    /**
     * A one-time code in a credcloud:// link. The page opens the link straight away, while her
     * click still counts, so the helper on this computer gets the code.
     */
    @PostMapping(path = "/helper/connect", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, String> connect(@AuthenticationPrincipal CredAppUserDetails principal) {
        var code = runners.pairingCode(principal.getUser());
        return Map.of("link", "credcloud://connect?server=" + URLEncoder.encode(server, StandardCharsets.UTF_8)
                + "&code=" + code.code());
    }

    // --- What CredCloud's pages poll ---

    @GetMapping(path = "/helper/status", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> status(@AuthenticationPrincipal CredAppUserDetails principal,
                                      @RequestParam(required = false) Long job) {
        var status = runners.status(principal.getUser(), job);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("connected", status.connected());
        body.put("running", status.running());
        body.put("lastPairedAt", status.lastPairedAt() == null ? null : status.lastPairedAt().toEpochMilli());
        if (status.job() != null) {
            Map<String, Object> jobStatus = new LinkedHashMap<>();
            jobStatus.put("status", status.job().status());
            jobStatus.put("error", status.job().error());
            body.put("job", jobStatus);
        }
        return body;
    }

    // --- Installing: open to anyone ---

    @GetMapping(path = "/helper/install/{code:[A-Za-z0-9_-]+}.sh", produces = "text/plain; charset=utf-8")
    @ResponseBody
    public String macScript(@PathVariable String code) {
        if (!code.matches(CODE) || !runners.pairingCodeLive(code)) {
            return """
                    #!/bin/sh
                    echo "This install command has expired or was already used."
                    echo "In CredCloud, go to My account > CredCloud Helper and get a new one."
                    exit 1
                    """;
        }
        return """
                #!/bin/sh
                # Installs CredCloud Helper on this Mac and connects it to your CredCloud account.
                # It goes in ~/Applications and needs no administrator password.
                set -eu
                SERVER='%s'
                CODE='%s'
                if [ "$(uname -s)" != Darwin ]; then
                  echo "This command is for a Mac. On Windows, use the PowerShell command on the same CredCloud page."
                  exit 1
                fi
                ARCH=amd64
                if [ "$(uname -m)" = arm64 ] || [ "$(sysctl -in hw.optional.arm64 2>/dev/null)" = 1 ]; then ARCH=arm64; fi
                TMP=$(mktemp -d)
                trap 'rm -rf "$TMP"' EXIT
                echo "Downloading CredCloud Helper..."
                curl -fsSL "$SERVER/helper/download/credcloud-helper-darwin-$ARCH" -o "$TMP/credcloud-helper"
                chmod +x "$TMP/credcloud-helper"
                "$TMP/credcloud-helper" install --server "$SERVER" --code "$CODE"
                """.formatted(server, code);
    }

    @GetMapping(path = "/helper/install/{code:[A-Za-z0-9_-]+}.ps1", produces = "text/plain; charset=utf-8")
    @ResponseBody
    public String windowsScript(@PathVariable String code) {
        if (!code.matches(CODE) || !runners.pairingCodeLive(code)) {
            return """
                    Write-Host 'This install command has expired or was already used.'
                    Write-Host 'In CredCloud, go to My account > CredCloud Helper and get a new one.'
                    """;
        }
        return """
                # Installs CredCloud Helper for this Windows account and connects it to CredCloud.
                # It needs no administrator.
                & {
                  $ErrorActionPreference = 'Stop'
                  $ProgressPreference = 'SilentlyContinue'
                  [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
                  $server = '%s'
                  $code = '%s'
                  $dir = Join-Path $env:TEMP ('credcloud-' + [guid]::NewGuid())
                  New-Item -ItemType Directory -Path $dir | Out-Null
                  $exe = Join-Path $dir 'credcloud-helper.exe'
                  $report = Join-Path $dir 'report.txt'
                  Write-Host 'Downloading CredCloud Helper...'
                  Invoke-WebRequest -UseBasicParsing -Uri "$server/helper/download/credcloud-helper-windows-amd64.exe" -OutFile $exe
                  Start-Process -FilePath $exe -ArgumentList @('install', '--server', $server, '--code', $code, '--report', ('"' + $report + '"')) -Wait | Out-Null
                  if (Test-Path $report) { Get-Content $report | ForEach-Object { Write-Host $_ } }
                  Remove-Item -Recurse -Force $dir -ErrorAction SilentlyContinue
                }
                """.formatted(server, code);
    }

    @GetMapping("/helper/download/{name:[A-Za-z0-9._-]+}")
    public ResponseEntity<Resource> download(@PathVariable String name) {
        String file = name.equals("windows") ? HelperFiles.BUILDS.get("windows-amd64") : name;
        String saveAs = name.equals("windows") ? "CredCloud Helper.exe" : file;
        return builds.file(file)
                .<ResponseEntity<Resource>>map(path -> ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(saveAs).build().toString())
                        .body(new FileSystemResource(path)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** "mac", "windows" or "other", to show her computer's steps first. */
    static String system(String userAgent) {
        String agent = userAgent == null ? "" : userAgent;
        if (agent.contains("Windows")) {
            return "windows";
        }
        if (agent.contains("Macintosh") || agent.contains("Mac OS X")) {
            return "mac";
        }
        return "other";
    }
}
