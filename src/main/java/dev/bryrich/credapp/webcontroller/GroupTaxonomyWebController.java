package dev.bryrich.credapp.webcontroller;

import dev.bryrich.credapp.dto.GroupTaxonomyForm;
import dev.bryrich.credapp.service.GroupService;
import dev.bryrich.credapp.service.GroupTaxonomyService;
import dev.bryrich.credapp.service.TaxonomyService;
import jakarta.validation.Valid;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** A group's specialties, replacing the free-text specialty field dropped in V5. */
@Controller
@RequestMapping("/groups/{groupId}/taxonomies")
public class GroupTaxonomyWebController {

    private final GroupTaxonomyService groupTaxonomyService;
    private final TaxonomyService taxonomyService;
    private final GroupService groupService;

    public GroupTaxonomyWebController(GroupTaxonomyService groupTaxonomyService,
                                      TaxonomyService taxonomyService,
                                      GroupService groupService) {
        this.groupTaxonomyService = groupTaxonomyService;
        this.taxonomyService = taxonomyService;
        this.groupService = groupService;
    }

    /** Blank text inputs submit "" — store null instead. */
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @GetMapping("/new")
    public String newTaxonomy(@PathVariable Long groupId, Model model) {
        addFormAttributes(groupId, model);
        model.addAttribute("form", new GroupTaxonomyForm());
        return "group/taxonomy-form";
    }

    @PostMapping
    public String create(@PathVariable Long groupId,
                         @Valid @ModelAttribute("form") GroupTaxonomyForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            addFormAttributes(groupId, model);
            return "group/taxonomy-form";
        }
        groupTaxonomyService.assign(groupId, form.getCode(), form.isPrimary());
        redirectAttributes.addFlashAttribute("message", "Specialty added.");
        return "redirect:/groups/" + groupId;
    }

    @PostMapping("/{code}/primary")
    public String makePrimary(@PathVariable Long groupId,
                              @PathVariable String code,
                              RedirectAttributes redirectAttributes) {
        groupTaxonomyService.assign(groupId, code, true);
        redirectAttributes.addFlashAttribute("message", "Primary specialty updated.");
        return "redirect:/groups/" + groupId;
    }

    @PostMapping("/{code}/delete")
    public String delete(@PathVariable Long groupId,
                         @PathVariable String code,
                         RedirectAttributes redirectAttributes) {
        groupTaxonomyService.unassign(groupId, code);
        redirectAttributes.addFlashAttribute("message", "Specialty removed.");
        return "redirect:/groups/" + groupId;
    }

    private void addFormAttributes(Long groupId, Model model) {
        model.addAttribute("group", groupService.findById(groupId));
        model.addAttribute("taxonomies", taxonomyService.findAllForSelect());
    }
}
