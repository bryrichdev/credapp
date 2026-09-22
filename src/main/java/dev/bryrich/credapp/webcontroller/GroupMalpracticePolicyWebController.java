package dev.bryrich.credapp.webcontroller;

import dev.bryrich.credapp.dto.MalpracticePolicyForm;
import dev.bryrich.credapp.entity.MalpracticePolicy;
import dev.bryrich.credapp.entity.enums.CoverageScope;
import dev.bryrich.credapp.service.GroupService;
import dev.bryrich.credapp.service.MalpracticePolicyService;
import jakarta.validation.Valid;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Malpractice policies covering a whole group rather than one provider. */
@Controller
@RequestMapping("/groups/{groupId}/policies")
public class GroupMalpracticePolicyWebController {

    private final MalpracticePolicyService policyService;
    private final GroupService groupService;

    public GroupMalpracticePolicyWebController(MalpracticePolicyService policyService,
                                               GroupService groupService) {
        this.policyService = policyService;
        this.groupService = groupService;
    }

    /** Blank text inputs submit "" — store null instead. */
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @GetMapping("/new")
    public String newPolicy(@PathVariable Long groupId, Model model) {
        addFormAttributes(groupId, model);
        MalpracticePolicyForm form = new MalpracticePolicyForm();
        form.setGroupId(groupId);
        model.addAttribute("form", form);
        return "group/policy-form";
    }

    @PostMapping
    public String create(@PathVariable Long groupId,
                         @Valid @ModelAttribute("form") MalpracticePolicyForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            addFormAttributes(groupId, model);
            return "group/policy-form";
        }
        MalpracticePolicy saved = policyService.addForGroup(groupId,
                form.getPolicyNumber(), form.getCarrierName(), form.getTypeOfCoverage(),
                form.getEffectiveDate(), form.getSharedIndividual());
        policyService.update(saved.getId(), form::applyTo);
        redirectAttributes.addFlashAttribute("message", "Policy added.");
        return "redirect:/groups/" + groupId;
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long groupId, @PathVariable Long id, Model model) {
        MalpracticePolicy policy = policyService.findById(id);
        addFormAttributes(groupId, model);
        model.addAttribute("form", MalpracticePolicyForm.from(policy));
        model.addAttribute("policyId", id);
        return "group/policy-form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long groupId,
                         @PathVariable Long id,
                         @Valid @ModelAttribute("form") MalpracticePolicyForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            addFormAttributes(groupId, model);
            model.addAttribute("policyId", id);
            return "group/policy-form";
        }
        policyService.update(id, form::applyTo);
        redirectAttributes.addFlashAttribute("message", "Policy updated.");
        return "redirect:/groups/" + groupId;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long groupId,
                         @PathVariable Long id,
                         RedirectAttributes redirectAttributes) {
        policyService.delete(id);
        redirectAttributes.addFlashAttribute("message", "Policy deleted.");
        return "redirect:/groups/" + groupId;
    }

    private void addFormAttributes(Long groupId, Model model) {
        model.addAttribute("group", groupService.findById(groupId));
        model.addAttribute("scopes", CoverageScope.values());
    }
}
