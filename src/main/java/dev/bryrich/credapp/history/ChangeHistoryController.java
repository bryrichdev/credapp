package dev.bryrich.credapp.history;

import dev.bryrich.credapp.group.Group;
import dev.bryrich.credapp.group.GroupService;
import dev.bryrich.credapp.history.ChangeHistoryService.Subject;
import dev.bryrich.credapp.provider.Provider;
import dev.bryrich.credapp.provider.ProviderService;
import dev.bryrich.credapp.tracking.TrackingService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/** The change history pages for a provider and a group. Anyone who can see the record can see its history. */
@Controller
public class ChangeHistoryController {

    private final ChangeHistoryService history;
    private final ProviderService providers;
    private final GroupService groups;

    public ChangeHistoryController(ChangeHistoryService history, ProviderService providers, GroupService groups) {
        this.history = history;
        this.providers = providers;
        this.groups = groups;
    }

    @GetMapping("/providers/{id}/history")
    public String provider(@PathVariable Long id, @RequestParam(required = false) Long before, Model model) {
        // Loading it first turns another user group's provider into a not-found.
        Provider provider = providers.findById(id);
        model.addAttribute("title", provider.getFirstName() + " " + provider.getLastName());
        model.addAttribute("section", "providers");
        model.addAttribute("backPath", "/providers/" + id);
        model.addAttribute("page", history.history(TrackingService.currentGroup(), Subject.PROVIDER, id, before));
        return "history/page";
    }

    @GetMapping("/groups/{id}/history")
    public String group(@PathVariable Long id, @RequestParam(required = false) Long before, Model model) {
        Group group = groups.findById(id);
        model.addAttribute("title", group.getLbn());
        model.addAttribute("section", "groups");
        model.addAttribute("backPath", "/groups/" + id);
        model.addAttribute("page", history.history(TrackingService.currentGroup(), Subject.GROUP, id, before));
        return "history/page";
    }
}
