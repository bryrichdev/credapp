package dev.bryrich.credapp.owner;

import dev.bryrich.credapp.group.GroupService;
import dev.bryrich.credapp.security.CredAppUserDetails;
import dev.bryrich.credapp.ssn.SsnAccessService;
import dev.bryrich.credapp.ssn.SsnSubjectType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Owners are people, kept separate from any one group. The same person can own several,
 * which is why their stake lives on the group page rather than here.
 */
@Controller
@RequestMapping("/owners")
public class OwnerWebController {

    private final OwnerService ownerService;
    private final GroupOwnershipService ownershipService;
    private final GroupService groupService;
    private final SsnAccessService ssnAccessService;

    public OwnerWebController(OwnerService ownerService,
                              GroupOwnershipService ownershipService,
                              GroupService groupService,
                              SsnAccessService ssnAccessService) {
        this.ownerService = ownerService;
        this.ownershipService = ownershipService;
        this.groupService = groupService;
        this.ssnAccessService = ssnAccessService;
    }

    /**
     * Hands back one decrypted SSN and logs the fact. Answers JSON rather than rendering
     * the number into the page, so it never reaches page source, browser history or a
     * cached response.
     */
    @PostMapping("/{id}/ssn")
    @ResponseBody
    public Map<String, String> revealSsn(@AuthenticationPrincipal CredAppUserDetails principal,
                                         @PathVariable Long id,
                                         HttpServletRequest request) {
        String ssn = ssnAccessService.revealOwnerSsn(principal.getUser(), id, clientIp(request));
        Map<String, String> body = new HashMap<>();
        body.put("ssn", ssn);
        return body;
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
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
        model.addAttribute("groups", groupService.findAllForSelect());
        return "owner/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") OwnerForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            model.addAttribute("groups", groupService.findAllForSelect());
            return "owner/form";
        }
        Owner saved = ownerService.create(form.toEntity());
        if (form.getGroupId() != null) {
            try {
                ownershipService.addOwner(form.getGroupId(), saved.getId(),
                        form.getPercentOwned(), null);
            } catch (OwnershipPercentExceededException ex) {
                // The person is saved; only the stake was refused. Keep the record they
                // just typed in and say why the group did not stick.
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Owner saved, but the group was not added: " + ex.getMessage());
            }
        }
        return "redirect:/owners/" + saved.getId();
    }

    // ============ groups this owner holds a stake in ============

    @GetMapping("/{id}/groups/new")
    public String newOwnerGroup(@PathVariable Long id, Model model) {
        model.addAttribute("form", new OwnerGroupForm());
        addGroupFormAttributes(id, model);
        return "owner/group-form";
    }

    @PostMapping("/{id}/groups")
    public String addOwnerGroup(@PathVariable Long id,
                                @Valid @ModelAttribute("form") OwnerGroupForm form,
                                BindingResult binding,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            addGroupFormAttributes(id, model);
            return "owner/group-form";
        }
        try {
            ownershipService.addOwner(form.getGroupId(), id, form.getPercentOwned(),
                    form.getEffectiveDate());
        } catch (OwnershipPercentExceededException ex) {
            binding.rejectValue("percentOwned", "percent.exceeded", ex.getMessage());
            addGroupFormAttributes(id, model);
            return "owner/group-form";
        }
        redirectAttributes.addFlashAttribute("message", "Group added.");
        return "redirect:/owners/" + id;
    }

    @PostMapping("/{ownerId}/groups/{groupId}/delete")
    public String removeOwnerGroup(@PathVariable Long ownerId,
                                   @PathVariable Long groupId,
                                   RedirectAttributes redirectAttributes) {
        ownershipService.removeOwner(groupId, ownerId);
        redirectAttributes.addFlashAttribute("message", "Group removed.");
        return "redirect:/owners/" + ownerId;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        ownerService.delete(id);
        redirectAttributes.addFlashAttribute("message", "Owner deleted.");
        return "redirect:/owners";
    }

    private void addGroupFormAttributes(Long ownerId, Model model) {
        model.addAttribute("owner", ownerService.findById(ownerId));
        model.addAttribute("groups", groupService.findAllForSelect());
    }

    private void addDetailAttributes(Long id, Owner owner, Model model) {
        // The groups come back JOIN FETCHed, so the template can read stake.group safely.
        List<GroupOwner> stakes = ownershipService.findGroupsOwnedBy(id);
        model.addAttribute("owner", owner);
        model.addAttribute("stakes", stakes);
        model.addAttribute("ssnOnFile", ssnAccessService.ownerSsnOnFile(id));
        model.addAttribute("ssnAccess",
                ssnAccessService.recentAccess(SsnSubjectType.OWNER, id));
    }
}
