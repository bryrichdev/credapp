package dev.bryrich.credapp.group;

import dev.bryrich.credapp.group.location.GroupLocationService;
import dev.bryrich.credapp.group.membership.GroupProviderForm;
import dev.bryrich.credapp.group.membership.GroupProviderService;
import dev.bryrich.credapp.malpractice.CoverageScope;
import dev.bryrich.credapp.malpractice.MalpracticePolicyService;
import dev.bryrich.credapp.owner.GroupOwnershipService;
import dev.bryrich.credapp.owner.OwnerService;
import dev.bryrich.credapp.owner.Relationship;
import dev.bryrich.credapp.provider.ProviderService;
import dev.bryrich.credapp.taxonomy.GroupTaxonomyForm;
import dev.bryrich.credapp.taxonomy.GroupTaxonomyService;
import dev.bryrich.credapp.taxonomy.TaxonomyService;
import jakarta.validation.Valid;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/groups")
public class GroupWebController {

    private final GroupService groupService;
    private final GroupProfileService profileService;
    private final GroupLocationService locationService;
    private final GroupOwnershipService ownershipService;
    private final GroupProviderService groupProviderService;
    private final OwnerService ownerService;
    private final ProviderService providerService;
    private final GroupTaxonomyService groupTaxonomyService;
    private final TaxonomyService taxonomyService;
    private final MalpracticePolicyService policyService;

    public GroupWebController(GroupService groupService,
                              GroupProfileService profileService,
                              GroupLocationService locationService,
                              GroupOwnershipService ownershipService,
                              GroupProviderService groupProviderService,
                              OwnerService ownerService,
                              ProviderService providerService,
                              GroupTaxonomyService groupTaxonomyService,
                              TaxonomyService taxonomyService,
                              MalpracticePolicyService policyService) {
        this.groupService = groupService;
        this.profileService = profileService;
        this.locationService = locationService;
        this.ownershipService = ownershipService;
        this.groupProviderService = groupProviderService;
        this.ownerService = ownerService;
        this.providerService = providerService;
        this.groupTaxonomyService = groupTaxonomyService;
        this.taxonomyService = taxonomyService;
        this.policyService = policyService;
    }

    /** Blank text inputs submit "" — store null instead. */
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @GetMapping
    public String list(@RequestParam(required = false) String lbn,
                       Pageable pageable,
                       Model model) {
        Page<Group> page = (lbn == null || lbn.isBlank())
                ? groupService.findAll(pageable)
                : groupService.search(lbn, pageable);

        model.addAttribute("page", page);
        model.addAttribute("lbn", lbn);
        return "group/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("group", groupService.findById(id));
        model.addAttribute("locations", locationService.findByGroupId(id));
        model.addAttribute("owners", ownershipService.findOwners(id));
        model.addAttribute("relations", ownershipService.findRelations(id));
        model.addAttribute("providers", groupProviderService.findProviders(id));
        model.addAttribute("totalPercent", ownershipService.totalPercentOwned(id));
        model.addAttribute("remainingPercent", ownershipService.remainingPercent(id));
        model.addAttribute("taxonomies", groupTaxonomyService.findByGroupId(id));
        model.addAttribute("policies", policyService.findByGroupId(id));
        return "group/detail";
    }

    // ============ the one form: new and edit ============

    @GetMapping("/new")
    public String newGroup(Model model) {
        GroupProfileForm form = new GroupProfileForm();
        model.addAttribute("form", form);
        addFormOptions(null, form, model);
        return "group/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") GroupProfileForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        return save(null, form, binding, model, redirectAttributes);
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        GroupProfileForm form = profileService.load(id);
        model.addAttribute("form", form);
        addFormOptions(id, form, model);
        return "group/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("form") GroupProfileForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        groupService.findById(id);
        return save(id, form, binding, model, redirectAttributes);
    }

    private String save(Long id, GroupProfileForm form, BindingResult binding,
                        Model model, RedirectAttributes redirectAttributes) {
        profileService.validate(form, binding);
        if (binding.hasErrors()) {
            binding.reject("form.invalid", "Some fields need attention. They're marked below.");
            addFormOptions(id, form, model);
            return "group/form";
        }
        Group saved;
        try {
            saved = profileService.save(id, form);
        } catch (DataIntegrityViolationException ex) {
            binding.reject("save.conflict",
                    "Nothing was saved. The group NPI or a policy number is already on file, "
                            + "or an item being removed is still referenced elsewhere.");
            addFormOptions(id, form, model);
            return "group/form";
        }
        redirectAttributes.addFlashAttribute("message", id == null ? "Group added." : "Group saved.");
        return "redirect:/groups/" + saved.getId();
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        groupService.delete(id);
        redirectAttributes.addFlashAttribute("message", "Group deleted.");
        return "redirect:/groups";
    }

    /** Pick-lists for every section, plus a blank row of each kind for the "Add" buttons. */
    private void addFormOptions(Long groupId, GroupProfileForm form, Model model) {
        if (groupId != null) {
            model.addAttribute("group", groupService.findById(groupId));
        }
        model.addAttribute("people", ownerService.findAllForSelect());
        model.addAttribute("ownerLabels", profileService.ownerLabels(form));
        model.addAttribute("providerOptions", providerService.findAllForSelect());
        model.addAttribute("taxonomyOptions", taxonomyService.findAllForSelect());
        model.addAttribute("relationships", Relationship.values());
        model.addAttribute("scopes", CoverageScope.values());

        Map<String, Object> blank = new HashMap<>();
        blank.put("location", new GroupProfileForm.LocationRow());
        blank.put("owner", new GroupProfileForm.OwnerRow());
        blank.put("relation", new GroupProfileForm.RelationRow());
        blank.put("provider", new GroupProviderForm());
        blank.put("taxonomy", new GroupTaxonomyForm());
        blank.put("policy", new GroupProfileForm.PolicyRow());
        model.addAttribute("blank", blank);
    }
}
