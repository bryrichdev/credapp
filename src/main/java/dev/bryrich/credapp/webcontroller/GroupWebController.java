package dev.bryrich.credapp.webcontroller;

import dev.bryrich.credapp.dto.GroupForm;
import dev.bryrich.credapp.dto.GroupLocationForm;
import dev.bryrich.credapp.dto.GroupOwnerForm;
import dev.bryrich.credapp.dto.GroupProviderForm;
import dev.bryrich.credapp.dto.OwnerRelationForm;
import dev.bryrich.credapp.entity.Group;
import dev.bryrich.credapp.entity.GroupLocation;
import dev.bryrich.credapp.entity.GroupOwner;
import dev.bryrich.credapp.entity.enums.Relationship;
import dev.bryrich.credapp.exception.OwnershipPercentExceededException;
import dev.bryrich.credapp.service.GroupLocationService;
import dev.bryrich.credapp.service.GroupTaxonomyService;
import dev.bryrich.credapp.service.MalpracticePolicyService;
import dev.bryrich.credapp.service.GroupOwnershipService;
import dev.bryrich.credapp.service.GroupProviderService;
import dev.bryrich.credapp.service.GroupService;
import dev.bryrich.credapp.service.OwnerService;
import dev.bryrich.credapp.service.ProviderService;
import jakarta.validation.Valid;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/groups")
public class GroupWebController {

    private final GroupService groupService;
    private final GroupLocationService locationService;
    private final GroupOwnershipService ownershipService;
    private final GroupProviderService groupProviderService;
    private final OwnerService ownerService;
    private final ProviderService providerService;
    private final GroupTaxonomyService groupTaxonomyService;
    private final MalpracticePolicyService policyService;

    public GroupWebController(GroupService groupService,
                              GroupLocationService locationService,
                              GroupOwnershipService ownershipService,
                              GroupProviderService groupProviderService,
                              OwnerService ownerService,
                              ProviderService providerService,
                              GroupTaxonomyService groupTaxonomyService,
                              MalpracticePolicyService policyService) {
        this.groupService = groupService;
        this.locationService = locationService;
        this.ownershipService = ownershipService;
        this.groupProviderService = groupProviderService;
        this.ownerService = ownerService;
        this.providerService = providerService;
        this.groupTaxonomyService = groupTaxonomyService;
        this.policyService = policyService;
    }

    /** Blank text inputs submit "" — store null instead. */
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    // ============ groups ============

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
    public String detail(@PathVariable Long id,
                         @RequestParam(name = "edit", defaultValue = "false") boolean edit,
                         Model model) {
        Group group = groupService.findById(id);
        addDetailAttributes(id, group, model);
        model.addAttribute("editing", edit);
        if (edit) {
            model.addAttribute("form", GroupForm.from(group));
        }
        return "group/detail";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("form") GroupForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            addDetailAttributes(id, groupService.findById(id), model);
            model.addAttribute("editing", true);
            return "group/detail";
        }
        groupService.update(id, form::applyTo);
        redirectAttributes.addFlashAttribute("message", "Group updated.");
        return "redirect:/groups/" + id;
    }

    @GetMapping("/new")
    public String newGroup(Model model) {
        model.addAttribute("form", new GroupForm());
        return "group/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") GroupForm form,
                         BindingResult binding) {
        if (binding.hasErrors()) {
            return "group/form";
        }
        Group saved = groupService.create(form.toEntity());
        return "redirect:/groups/" + saved.getId();
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        groupService.delete(id);
        redirectAttributes.addFlashAttribute("message", "Group deleted.");
        return "redirect:/groups";
    }

    // ============ locations ============

    @GetMapping("/{id}/locations/new")
    public String newLocation(@PathVariable Long id, Model model) {
        model.addAttribute("group", groupService.findById(id));
        model.addAttribute("form", new GroupLocationForm());
        return "group/location-form";
    }

    @PostMapping("/{id}/locations")
    public String createLocation(@PathVariable Long id,
                                 @Valid @ModelAttribute("form") GroupLocationForm form,
                                 BindingResult binding,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            model.addAttribute("group", groupService.findById(id));
            return "group/location-form";
        }
        locationService.addLocation(id, form.toEntity());
        redirectAttributes.addFlashAttribute("message", "Location added.");
        return "redirect:/groups/" + id;
    }

    @GetMapping("/{groupId}/locations/{locationId}/edit")
    public String editLocation(@PathVariable Long groupId,
                               @PathVariable Long locationId,
                               Model model) {
        GroupLocation location = locationService.findByIdAndGroupId(locationId, groupId);
        model.addAttribute("group", groupService.findById(groupId));
        model.addAttribute("form", GroupLocationForm.from(location));
        model.addAttribute("locationId", locationId);
        return "group/location-form";
    }

    @PostMapping("/{groupId}/locations/{locationId}/edit")
    public String updateLocation(@PathVariable Long groupId,
                                 @PathVariable Long locationId,
                                 @Valid @ModelAttribute("form") GroupLocationForm form,
                                 BindingResult binding,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            model.addAttribute("group", groupService.findById(groupId));
            model.addAttribute("locationId", locationId);
            return "group/location-form";
        }
        locationService.update(locationId, groupId, form::applyTo);
        redirectAttributes.addFlashAttribute("message", "Location updated.");
        return "redirect:/groups/" + groupId;
    }

    @PostMapping("/{groupId}/locations/{locationId}/delete")
    public String deleteLocation(@PathVariable Long groupId,
                                 @PathVariable Long locationId,
                                 RedirectAttributes redirectAttributes) {
        locationService.delete(locationId, groupId);
        redirectAttributes.addFlashAttribute("message", "Location deleted.");
        return "redirect:/groups/" + groupId;
    }

    // ============ owners ============

    @GetMapping("/{id}/owners/new")
    public String newOwner(@PathVariable Long id, Model model) {
        model.addAttribute("form", new GroupOwnerForm());
        addOwnerFormAttributes(id, model);
        return "group/owner-form";
    }

    @PostMapping("/{id}/owners")
    public String addOwner(@PathVariable Long id,
                           @Valid @ModelAttribute("form") GroupOwnerForm form,
                           BindingResult binding,
                           Model model,
                           RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            addOwnerFormAttributes(id, model);
            return "group/owner-form";
        }
        try {
            ownershipService.addOwner(id, form.getOwnerId(), form.getPercentOwned(),
                    form.getEffectiveDate());
        } catch (OwnershipPercentExceededException ex) {
            binding.rejectValue("percentOwned", "percent.exceeded", ex.getMessage());
            addOwnerFormAttributes(id, model);
            return "group/owner-form";
        }
        redirectAttributes.addFlashAttribute("message", "Owner added.");
        return "redirect:/groups/" + id;
    }

    @GetMapping("/{groupId}/owners/{ownerId}/edit")
    public String editOwner(@PathVariable Long groupId,
                            @PathVariable Long ownerId,
                            Model model) {
        GroupOwner groupOwner = ownershipService.findOwner(groupId, ownerId);
        model.addAttribute("form", GroupOwnerForm.from(groupOwner));
        model.addAttribute("ownerId", ownerId);
        addOwnerFormAttributes(groupId, model);
        return "group/owner-form";
    }

    @PostMapping("/{groupId}/owners/{ownerId}/edit")
    public String updateOwner(@PathVariable Long groupId,
                              @PathVariable Long ownerId,
                              @Valid @ModelAttribute("form") GroupOwnerForm form,
                              BindingResult binding,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            model.addAttribute("ownerId", ownerId);
            addOwnerFormAttributes(groupId, model);
            return "group/owner-form";
        }
        try {
            ownershipService.updateOwner(groupId, ownerId, form.getPercentOwned(),
                    form.getEffectiveDate());
        } catch (OwnershipPercentExceededException ex) {
            binding.rejectValue("percentOwned", "percent.exceeded", ex.getMessage());
            model.addAttribute("ownerId", ownerId);
            addOwnerFormAttributes(groupId, model);
            return "group/owner-form";
        }
        redirectAttributes.addFlashAttribute("message", "Ownership updated.");
        return "redirect:/groups/" + groupId;
    }

    @PostMapping("/{groupId}/owners/{ownerId}/delete")
    public String removeOwner(@PathVariable Long groupId,
                              @PathVariable Long ownerId,
                              RedirectAttributes redirectAttributes) {
        ownershipService.removeOwner(groupId, ownerId);
        redirectAttributes.addFlashAttribute("message", "Owner removed from this group.");
        return "redirect:/groups/" + groupId;
    }

    // ============ relationships between owners ============

    @GetMapping("/{id}/relations/new")
    public String newRelation(@PathVariable Long id, Model model) {
        model.addAttribute("form", new OwnerRelationForm());
        addRelationFormAttributes(id, model);
        return "group/relation-form";
    }

    @PostMapping("/{id}/relations")
    public String addRelation(@PathVariable Long id,
                              @Valid @ModelAttribute("form") OwnerRelationForm form,
                              BindingResult binding,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        if (!binding.hasErrors() && form.getOwnerId().equals(form.getRelatedOwnerId())) {
            binding.rejectValue("relatedOwnerId", "relation.self",
                    "Pick two different owners");
        }
        if (binding.hasErrors()) {
            addRelationFormAttributes(id, model);
            return "group/relation-form";
        }
        ownershipService.addRelation(id, form.getOwnerId(), form.getRelatedOwnerId(),
                form.getRelationship());
        redirectAttributes.addFlashAttribute("message", "Relationship saved.");
        return "redirect:/groups/" + id;
    }

    @PostMapping("/{groupId}/relations/{ownerId}/{relatedOwnerId}/delete")
    public String removeRelation(@PathVariable Long groupId,
                                 @PathVariable Long ownerId,
                                 @PathVariable Long relatedOwnerId,
                                 RedirectAttributes redirectAttributes) {
        ownershipService.removeRelation(groupId, ownerId, relatedOwnerId);
        redirectAttributes.addFlashAttribute("message", "Relationship removed.");
        return "redirect:/groups/" + groupId;
    }

    // ============ providers ============

    @GetMapping("/{id}/providers/new")
    public String newGroupProvider(@PathVariable Long id, Model model) {
        model.addAttribute("form", new GroupProviderForm());
        addProviderFormAttributes(id, model);
        return "group/provider-form";
    }

    @PostMapping("/{id}/providers")
    public String assignProvider(@PathVariable Long id,
                                 @Valid @ModelAttribute("form") GroupProviderForm form,
                                 BindingResult binding,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            addProviderFormAttributes(id, model);
            return "group/provider-form";
        }
        groupProviderService.assign(id, form.getProviderId(), form.getEffectiveDate());
        redirectAttributes.addFlashAttribute("message", "Provider assigned.");
        return "redirect:/groups/" + id;
    }

    @PostMapping("/{groupId}/providers/{providerId}/delete")
    public String unassignProvider(@PathVariable Long groupId,
                                   @PathVariable Long providerId,
                                   RedirectAttributes redirectAttributes) {
        groupProviderService.unassign(groupId, providerId);
        redirectAttributes.addFlashAttribute("message", "Provider unassigned.");
        return "redirect:/groups/" + groupId;
    }

    // ============ shared model attributes ============

    private void addDetailAttributes(Long id, Group group, Model model) {
        model.addAttribute("group", group);
        model.addAttribute("locations", locationService.findByGroupId(id));
        model.addAttribute("owners", ownershipService.findOwners(id));
        model.addAttribute("relations", ownershipService.findRelations(id));
        model.addAttribute("providers", groupProviderService.findProviders(id));
        model.addAttribute("totalPercent", ownershipService.totalPercentOwned(id));
        model.addAttribute("remainingPercent", ownershipService.remainingPercent(id));
        model.addAttribute("taxonomies", groupTaxonomyService.findByGroupId(id));
        model.addAttribute("policies", policyService.findByGroupId(id));
    }

    private void addOwnerFormAttributes(Long groupId, Model model) {
        model.addAttribute("group", groupService.findById(groupId));
        model.addAttribute("people", ownerService.findAllForSelect());
        model.addAttribute("remainingPercent", ownershipService.remainingPercent(groupId));
    }

    private void addRelationFormAttributes(Long groupId, Model model) {
        model.addAttribute("group", groupService.findById(groupId));
        model.addAttribute("owners", ownershipService.findOwners(groupId));
        model.addAttribute("relationships", Relationship.values());
    }

    private void addProviderFormAttributes(Long groupId, Model model) {
        model.addAttribute("group", groupService.findById(groupId));
        model.addAttribute("providers", providerService.findAllForSelect());
    }
}
