package dev.bryrich.credapp.tracking;

import dev.bryrich.credapp.group.GroupRepository;
import dev.bryrich.credapp.onboarding.xlsx.XlsxWriter;
import dev.bryrich.credapp.onboarding.xlsx.XlsxWriter.Column;
import dev.bryrich.credapp.onboarding.xlsx.XlsxWriter.Format;
import dev.bryrich.credapp.tracking.TrackedItem.SubjectType;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The tracking report: everything expired, coming due or stalled, filtered and exported;
 * and the settings page where an admin sets how far ahead their group hears about it.
 */
@Controller
public class TrackingController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final TrackingService tracking;
    private final TrackingSettingsService settings;
    private final GroupRepository groups;

    public TrackingController(TrackingService tracking, TrackingSettingsService settings, GroupRepository groups) {
        this.tracking = tracking;
        this.settings = settings;
        this.groups = groups;
    }

    /** The filters a page and its export share. Blank means "all". */
    public record Filter(TrackedState state, TrackedCategory category, Long group) {
        public boolean isEmpty() {
            return state == null && category == null && group == null;
        }
    }

    @GetMapping("/tracking")
    public String page(@RequestParam(required = false) TrackedState state,
                       @RequestParam(required = false) TrackedCategory category,
                       @RequestParam(required = false) Long group,
                       Model model) {
        Filter filter = new Filter(state, category, group);
        TrackingService.Report report = tracking.currentReport();
        Predicate<TrackedItem> inGroup = groupFilter(group);

        List<TrackedItem> items = report.items().stream()
                .filter(inGroup)
                .filter(item -> category == null || item.kind().getCategory() == category)
                .filter(item -> state == null || item.state() == state)
                .toList();

        // Each tile counts what the other filters leave, so it says what clicking it shows.
        Map<TrackedState, Long> stateCounts = new EnumMap<>(TrackedState.class);
        for (TrackedState each : TrackedState.values()) {
            stateCounts.put(each, 0L);
        }
        report.items().stream().filter(inGroup)
                .filter(item -> category == null || item.kind().getCategory() == category)
                .forEach(item -> stateCounts.merge(item.state(), 1L, Long::sum));

        model.addAttribute("filter", filter);
        model.addAttribute("items", items);
        model.addAttribute("stateCounts", stateCounts);
        model.addAttribute("settings", report.settings());
        model.addAttribute("total", report.items().size());
        model.addAttribute("states", TrackedState.values());
        model.addAttribute("categories", TrackedCategory.values());
        model.addAttribute("groupOptions", groups.findAll(Sort.by("lbn")));
        return "tracking/list";
    }

    @GetMapping("/tracking/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) TrackedState state,
                                         @RequestParam(required = false) TrackedCategory category,
                                         @RequestParam(required = false) Long group) {
        Predicate<TrackedItem> inGroup = groupFilter(group);
        List<List<String>> rows = tracking.currentReport().items().stream()
                .filter(inGroup)
                .filter(item -> category == null || item.kind().getCategory() == category)
                .filter(item -> state == null || item.state() == state)
                .map(item -> List.of(
                        item.stateLabel(),
                        item.date().toString(),
                        item.when(),
                        item.kind().getLabel(),
                        item.what(),
                        item.subject().name(),
                        item.subject().type() == SubjectType.PROVIDER ? "Provider" : "Group",
                        item.kind().getCategory().getLabel()))
                .toList();
        byte[] workbook = XlsxWriter.write(List.of(new XlsxWriter.TableSheet("Tracking", List.of(
                new Column("Status", 12, false, Format.GENERAL, List.of()),
                new Column("Date", 12, false, Format.TEXT, List.of()),
                new Column("When", 16, false, Format.GENERAL, List.of()),
                new Column("Item", 22, false, Format.GENERAL, List.of()),
                new Column("Details", 34, false, Format.GENERAL, List.of()),
                new Column("For", 28, false, Format.GENERAL, List.of()),
                new Column("Provider or group", 16, false, Format.GENERAL, List.of()),
                new Column("Category", 22, false, Format.GENERAL, List.of())), rows)));
        String name = "credcloud-tracking-" + LocalDate.now() + ".xlsx";
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .body(workbook);
    }

    // ============ settings: admins only (the /admin/** rule) ============

    @GetMapping("/admin/tracking-settings")
    public String settingsPage(Model model) {
        model.addAttribute("form", TrackingSettingsForm.from(settings.forGroup(TrackingService.currentGroup())));
        return "tracking/settings";
    }

    @PostMapping("/admin/tracking-settings")
    public String saveSettings(@Valid @ModelAttribute("form") TrackingSettingsForm form, BindingResult errors,
                               RedirectAttributes redirectAttributes) {
        if (errors.hasErrors()) {
            return "tracking/settings";
        }
        settings.save(TrackingService.currentGroup(), form);
        redirectAttributes.addFlashAttribute("message", "Tracking settings saved.");
        return "redirect:/tracking";
    }

    /** A practice group's own items, and its providers'. */
    private Predicate<TrackedItem> groupFilter(Long group) {
        if (group == null) {
            return item -> true;
        }
        Set<Long> providers = tracking.providersInGroup(TrackingService.currentGroup(), group);
        return item -> item.subject().type() == SubjectType.GROUP
                ? item.subject().id().equals(group)
                : providers.contains(item.subject().id());
    }
}
