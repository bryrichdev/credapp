package dev.bryrich.credapp.onboarding;

import dev.bryrich.credapp.usergroup.CurrentUserGroupResolver;
import dev.bryrich.credapp.usergroup.UserGroup;
import dev.bryrich.credapp.usergroup.UserGroupRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.UUID;

/**
 * Onboarding by spreadsheet: download the template, upload it filled in, check the preview,
 * then import. Under /admin, so only admins and superusers get here; a superuser viewing
 * another user group imports into that group (GroupViewFilter lets these posts through).
 */
@Controller
@RequestMapping("/admin/import")
public class OnboardingController {

    static final long MAX_UPLOAD_BYTES = 10L * 1024 * 1024;
    static final String TEMPLATE_FILE_NAME = "credcloud-onboarding-template.xlsx";
    static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final OnboardingImportService imports;
    private final UserGroupRepository userGroups;

    public OnboardingController(OnboardingImportService imports, UserGroupRepository userGroups) {
        this.imports = imports;
        this.userGroups = userGroups;
    }

    @GetMapping
    public String page(Model model) {
        model.addAttribute("targetGroup", targetGroupName());
        return "onboarding/import";
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(TEMPLATE_FILE_NAME).build().toString())
                .body(OnboardingTemplate.workbook());
    }

    @PostMapping("/preview")
    public String preview(@RequestParam(name = "file", required = false) MultipartFile file,
                          HttpSession session, Model model) throws IOException {
        session.removeAttribute(PendingImport.SESSION_KEY);
        model.addAttribute("targetGroup", targetGroupName());
        String problem = checkUpload(file);
        if (problem != null) {
            model.addAttribute("uploadError", problem);
            return "onboarding/import";
        }

        byte[] bytes = file.getBytes();
        ImportReport report = imports.preview(bytes);
        model.addAttribute("report", report);
        model.addAttribute("fileName", displayName(file));
        if (!report.hasProblems()) {
            PendingImport pending = new PendingImport(UUID.randomUUID().toString(), displayName(file), bytes,
                    currentUserGroupId());
            session.setAttribute(PendingImport.SESSION_KEY, pending);
            model.addAttribute("token", pending.token());
        }
        return "onboarding/import";
    }

    @PostMapping("/confirm")
    public String confirm(@RequestParam String token, HttpSession session, Model model,
                          RedirectAttributes redirectAttributes) {
        PendingImport pending = session.getAttribute(PendingImport.SESSION_KEY) instanceof PendingImport found
                && found.token().equals(token) ? found : null;
        if (pending == null || !pending.userGroupId().equals(currentUserGroupId())) {
            session.removeAttribute(PendingImport.SESSION_KEY);
            redirectAttributes.addFlashAttribute("uploadError",
                    "That preview has expired or was for another user group. Upload the file again.");
            return "redirect:/admin/import";
        }

        ImportReport report = imports.importFile(pending.bytes());
        session.removeAttribute(PendingImport.SESSION_KEY);
        if (!report.saved()) {
            // Something changed between the preview and now, such as a provider added by hand.
            model.addAttribute("targetGroup", targetGroupName());
            model.addAttribute("report", report);
            model.addAttribute("fileName", pending.fileName());
            model.addAttribute("changedSincePreview", true);
            return "onboarding/import";
        }
        redirectAttributes.addFlashAttribute("message", "Imported " + pending.fileName() + ": "
                + summary(report.groups().size(), "group") + " and "
                + summary(report.providers().size(), "provider") + ", with everything linked to them.");
        return report.providers().isEmpty() ? "redirect:/groups" : "redirect:/providers";
    }

    @PostMapping("/cancel")
    public String cancel(HttpSession session) {
        session.removeAttribute(PendingImport.SESSION_KEY);
        return "redirect:/admin/import";
    }

    // ============ helpers ============

    private static String checkUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return "Choose the filled-in workbook to upload.";
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            return "That file is over 10 MB. Split it into smaller workbooks and import them one at a time.";
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (name.endsWith(".xls") || name.endsWith(".csv") || name.endsWith(".numbers") || name.endsWith(".ods")) {
            return "Save the file as an Excel workbook (.xlsx) and upload that.";
        }
        return null;
    }

    private static String displayName(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            return "the workbook";
        }
        // Browsers on some systems send the whole path.
        return name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
    }

    private static String summary(int count, String noun) {
        return count + " " + noun + (count == 1 ? "" : "s");
    }

    private static Long currentUserGroupId() {
        return new CurrentUserGroupResolver().resolveCurrentTenantIdentifier();
    }

    private String targetGroupName() {
        return userGroups.findById(currentUserGroupId()).map(UserGroup::getName).orElse("your user group");
    }
}
