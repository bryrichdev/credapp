package dev.bryrich.credapp.common;

import dev.bryrich.credapp.dashboard.DashboardService;
import dev.bryrich.credapp.payer.enrollment.EnrollmentStatus;
import dev.bryrich.credapp.security.CredAppUserDetails;
import dev.bryrich.credapp.tracking.TrackedItem;
import dev.bryrich.credapp.tracking.TrackingService;
import dev.bryrich.credapp.usergroup.UserGroup;
import dev.bryrich.credapp.usergroup.UserGroupRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Controller
public class HomeController {

    /** How many of the most pressing items the home page lists before "See all". */
    static final int ATTENTION_ITEMS = 6;
    static final int RECENT_ITEMS = 6;

    private static final DateTimeFormatter TODAY = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US);

    private final DashboardService dashboard;
    private final TrackingService tracking;
    private final UserGroupRepository userGroups;

    public HomeController(DashboardService dashboard, TrackingService tracking, UserGroupRepository userGroups) {
        this.dashboard = dashboard;
        this.tracking = tracking;
        this.userGroups = userGroups;
    }

    /** One enrollment status and how many provider and group enrollments are in it. */
    public record PipelineStep(EnrollmentStatus status, long count) {
    }

    /**
     * The dashboard for the group being worked in: what needs doing, what's on file, where
     * payer applications stand, and what changed lately. An empty group gets a way to start.
     */
    @GetMapping("/")
    public String home(@AuthenticationPrincipal CredAppUserDetails principal, Model model) {
        Long group = TrackingService.currentGroup();
        TrackingService.Report report = tracking.report(group, LocalDate.now());
        List<TrackedItem> attention = report.items().stream()
                .filter(item -> item.state().needsAction()).toList();
        Map<String, Long> enrollments = dashboard.enrollmentsByStatus(group);

        model.addAttribute("groupName", userGroups.findById(group).map(UserGroup::getName).orElse(null));
        model.addAttribute("firstName", firstName(principal));
        model.addAttribute("today", LocalDate.now().format(TODAY));
        model.addAttribute("counts", dashboard.counts(group));
        model.addAttribute("stateCounts", report.countsByState());
        model.addAttribute("settings", report.settings());
        model.addAttribute("attention", attention.stream().limit(ATTENTION_ITEMS).toList());
        model.addAttribute("attentionTotal", attention.size());
        model.addAttribute("comingUp", report.items().size() - attention.size());
        model.addAttribute("pipeline", Arrays.stream(EnrollmentStatus.values())
                .map(status -> new PipelineStep(status, enrollments.getOrDefault(status.getValue(), 0L)))
                .toList());
        model.addAttribute("enrollmentTotal", enrollments.values().stream().mapToLong(Long::longValue).sum());
        model.addAttribute("recent", dashboard.recentlyUpdated(group, RECENT_ITEMS));
        model.addAttribute("pendingApprovals", dashboard.pendingApprovals(group));
        return "index";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    private static String firstName(CredAppUserDetails principal) {
        String fullName = principal.getUser().getFullName();
        if (fullName == null || fullName.isBlank()) {
            return null;
        }
        return fullName.trim().split("\\s+")[0];
    }
}
