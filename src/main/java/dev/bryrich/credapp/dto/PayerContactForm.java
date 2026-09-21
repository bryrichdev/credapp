package dev.bryrich.credapp.dto;

import dev.bryrich.credapp.entity.PayerContact;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * A contact is scoped to the payer as a whole, to one group, or to one provider, never to
 * both a group and a provider. That is the payer_contacts_scope_exclusive constraint, and
 * scopeIsExclusive below rejects it in the browser rather than at the database.
 */
public class PayerContactForm {

    @NotBlank(message = "Role is required")
    private String role;

    private Long groupId;
    private Long providerId;

    private String phoneNumber;
    private String faxNumber;

    @Email(message = "Enter a valid email address")
    private String emailAddress;

    private String address;

    public PayerContactForm() {
    }

    public static PayerContactForm from(PayerContact contact) {
        PayerContactForm form = new PayerContactForm();
        form.role = contact.getRole();
        form.groupId = contact.getGroup() == null ? null : contact.getGroup().getId();
        form.providerId = contact.getProvider() == null ? null : contact.getProvider().getId();
        form.phoneNumber = contact.getPhoneNumber();
        form.faxNumber = contact.getFaxNumber();
        form.emailAddress = contact.getEmailAddress();
        form.address = contact.getAddress();
        return form;
    }

    @AssertTrue(message = "A contact can be tied to a group or a provider, not both")
    public boolean isScopeExclusive() {
        return groupId == null || providerId == null;
    }

    /** Copies the plain fields onto a contact. Scope is set by the service. */
    public void applyTo(PayerContact contact) {
        contact.setRole(role);
        contact.setPhoneNumber(blankToNull(phoneNumber));
        contact.setFaxNumber(blankToNull(faxNumber));
        contact.setEmailAddress(blankToNull(emailAddress));
        contact.setAddress(blankToNull(address));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public Long getProviderId() {
        return providerId;
    }

    public void setProviderId(Long providerId) {
        this.providerId = providerId;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getFaxNumber() {
        return faxNumber;
    }

    public void setFaxNumber(String faxNumber) {
        this.faxNumber = faxNumber;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public void setEmailAddress(String emailAddress) {
        this.emailAddress = emailAddress;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}
