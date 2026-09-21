package dev.bryrich.credapp.webcontroller;

import dev.bryrich.credapp.dto.OwnerForm;
import dev.bryrich.credapp.entity.GroupOwner;
import dev.bryrich.credapp.entity.Owner;
import dev.bryrich.credapp.service.GroupOwnershipService;
import dev.bryrich.credapp.service.OwnerService;
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

import java.util.List;

/**
 * Owners are people, kept separate from any one group. The same person can own several,
 * which is why their stake lives on the group page rather than here.
 */
@Controller
@RequestMapping("/owners")
public class OwnerWebController {

    private final OwnerService ownerService;
    private final GroupOwnershipService ownershipService;

    public OwnerWebController(OwnerService ownerService,
                              GroupOwnershipService ownershipService) {
        this.ownerService = ownerService;
        this.ownershipService = ownershipService;
    }

    /** Blank text inputs submit "" — store null instead. */
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @GetMapping
    public String list(@RequestParam(required = false) String lastName,
                       Pageable pageable,
                       Model model) {
        Page<Owner> page = (lastName == null || lastName.isBlank())
                ? ownerService.findAll(pageable)
                : ownerService.search(lastName, pageable);

        model.addAttribute("page", page);
        model.addAttribute("lastName", lastName);
        return "owner/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id,
                         @RequestParam(name = "edit", defaultValue = "false") boolean edit,
                         Model model) {
        Owner owner = ownerService.findById(id);
        addDetailAttributes(id, owner, model);
        model.addAttribute("editing", edit);
        if (edit) {
            model.addAttribute("form", OwnerForm.from(owner));
        }
        return "owner/detail";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("form") OwnerForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            addDetailAttributes(id, ownerService.findById(id), model);
            model.addAttribute("editing", true);
            return "owner/detail";
        }
        ownerService.update(id, form::applyTo);
        redirectAttributes.addFlashAttribute("message", "Owner updated.");
        return "redirect:/owners/" + id;
    }

    @GetMapping("/new")
    public String newOwner(Model model) {
        model.addAttribute("form", new OwnerForm());
        return "owner/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") OwnerForm form,
                         BindingResult binding) {
        if (binding.hasErrors()) {
            return "owner/form";
        }
        Owner saved = ownerService.create(form.toEntity());
        return "redirect:/owners/" + saved.getId();
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        ownerService.delete(id);
        redirectAttributes.addFlashAttribute("message", "Owner deleted.");
        return "redirect:/owners";
    }

    private void addDetailAttributes(Long id, Owner owner, Model model) {
        // The groups come back JOIN FETCHed, so the template can read stake.group safely.
        List<GroupOwner> stakes = ownershipService.findGroupsOwnedBy(id);
        model.addAttribute("owner", owner);
        model.addAttribute("stakes", stakes);
    }
}
