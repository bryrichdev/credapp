package dev.bryrich.credapp.webcontroller;

import dev.bryrich.credapp.dto.PayerContactForm;
import dev.bryrich.credapp.dto.PayerForm;
import dev.bryrich.credapp.entity.Payer;
import dev.bryrich.credapp.entity.PayerContact;
import dev.bryrich.credapp.service.GroupService;
import dev.bryrich.credapp.service.PayerContactService;
import dev.bryrich.credapp.service.PayerService;
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
@RequestMapping("/payers")
public class PayerWebController {

    private final PayerService payerService;
    private final PayerContactService contactService;
    private final GroupService groupService;
    private final ProviderService providerService;

    public PayerWebController(PayerService payerService,
                              PayerContactService contactService,
                              GroupService groupService,
                              ProviderService providerService) {
        this.payerService = payerService;
        this.contactService = contactService;
        this.groupService = groupService;
        this.providerService = providerService;
    }

    /** Blank text inputs submit "" — store null instead. */
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    // ============ payers ============

    @GetMapping
    public String list(@RequestParam(required = false) String name,
                       Pageable pageable,
                       Model model) {
        Page<Payer> page = (name == null || name.isBlank())
                ? payerService.findAll(pageable)
                : payerService.search(name, pageable);

        model.addAttribute("page", page);
        model.addAttribute("name", name);
        return "payer/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id,
                         @RequestParam(name = "edit", defaultValue = "false") boolean edit,
                         Model model) {
        Payer payer = payerService.findById(id);
        addDetailAttributes(id, payer, model);
        model.addAttribute("editing", edit);
        if (edit) {
            model.addAttribute("form", PayerForm.from(payer));
        }
        return "payer/detail";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("form") PayerForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            addDetailAttributes(id, payerService.findById(id), model);
            model.addAttribute("editing", true);
            return "payer/detail";
        }
        payerService.update(id, form::applyTo);
        redirectAttributes.addFlashAttribute("message", "Payer updated.");
        return "redirect:/payers/" + id;
    }

    @GetMapping("/new")
    public String newPayer(Model model) {
        model.addAttribute("form", new PayerForm());
        return "payer/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") PayerForm form,
                         BindingResult binding) {
        if (binding.hasErrors()) {
            return "payer/form";
        }
        Payer saved = payerService.create(form.toEntity());
        return "redirect:/payers/" + saved.getId();
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        payerService.delete(id);
        redirectAttributes.addFlashAttribute("message", "Payer deleted.");
        return "redirect:/payers";
    }

    // ============ contacts ============

    @GetMapping("/{id}/contacts/new")
    public String newContact(@PathVariable Long id, Model model) {
        model.addAttribute("form", new PayerContactForm());
        addContactFormAttributes(id, model);
        return "payer/contact-form";
    }

    @PostMapping("/{id}/contacts")
    public String createContact(@PathVariable Long id,
                                @Valid @ModelAttribute("form") PayerContactForm form,
                                BindingResult binding,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            addContactFormAttributes(id, model);
            return "payer/contact-form";
        }
        if (form.getGroupId() != null) {
            contactService.addGroupContact(id, form.getGroupId(), form.getRole(), form::applyTo);
        } else if (form.getProviderId() != null) {
            contactService.addProviderContact(id, form.getProviderId(), form.getRole(), form::applyTo);
        } else {
            contactService.addContact(id, form.getRole(), form::applyTo);
        }
        redirectAttributes.addFlashAttribute("message", "Contact added.");
        return "redirect:/payers/" + id;
    }

    @GetMapping("/{payerId}/contacts/{contactId}/edit")
    public String editContact(@PathVariable Long payerId,
                              @PathVariable Long contactId,
                              Model model) {
        PayerContact contact = contactService.findByIdAndPayerId(contactId, payerId);
        model.addAttribute("form", PayerContactForm.from(contact));
        model.addAttribute("contactId", contactId);
        addContactFormAttributes(payerId, model);
        return "payer/contact-form";
    }

    @PostMapping("/{payerId}/contacts/{contactId}/edit")
    public String updateContact(@PathVariable Long payerId,
                                @PathVariable Long contactId,
                                @Valid @ModelAttribute("form") PayerContactForm form,
                                BindingResult binding,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            model.addAttribute("contactId", contactId);
            addContactFormAttributes(payerId, model);
            return "payer/contact-form";
        }
        contactService.update(contactId, payerId, form::applyTo);
        contactService.rescope(contactId, payerId, form.getGroupId(), form.getProviderId());
        redirectAttributes.addFlashAttribute("message", "Contact updated.");
        return "redirect:/payers/" + payerId;
    }

    @PostMapping("/{payerId}/contacts/{contactId}/delete")
    public String deleteContact(@PathVariable Long payerId,
                                @PathVariable Long contactId,
                                RedirectAttributes redirectAttributes) {
        contactService.delete(contactId, payerId);
        redirectAttributes.addFlashAttribute("message", "Contact deleted.");
        return "redirect:/payers/" + payerId;
    }

    // ============ shared model attributes ============

    private void addDetailAttributes(Long id, Payer payer, Model model) {
        model.addAttribute("payer", payer);
        model.addAttribute("contacts", contactService.findByPayerId(id));
    }

    private void addContactFormAttributes(Long payerId, Model model) {
        model.addAttribute("payer", payerService.findById(payerId));
        model.addAttribute("groups", groupService.findAllForSelect());
        model.addAttribute("providers", providerService.findAllForSelect());
    }
}
