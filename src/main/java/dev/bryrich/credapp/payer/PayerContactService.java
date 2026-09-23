package dev.bryrich.credapp.payer;

import dev.bryrich.credapp.group.Group;
import dev.bryrich.credapp.group.GroupNotFoundException;
import dev.bryrich.credapp.group.GroupRepository;
import dev.bryrich.credapp.provider.Provider;
import dev.bryrich.credapp.provider.ProviderNotFoundException;
import dev.bryrich.credapp.provider.ProviderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Consumer;

/**
 * Contacts at a payer. Each one is scoped to the payer as a whole, to one group, or to
 * one provider. The three add methods are the only way to set that scope, so nothing can
 * build a contact that payer_contacts_scope_exclusive would reject.
 */
@Service
public class PayerContactService {

    private final PayerContactRepository contactRepository;
    private final PayerRepository payerRepository;
    private final GroupRepository groupRepository;
    private final ProviderRepository providerRepository;

    public PayerContactService(PayerContactRepository contactRepository,
                               PayerRepository payerRepository,
                               GroupRepository groupRepository,
                               ProviderRepository providerRepository) {
        this.contactRepository = contactRepository;
        this.payerRepository = payerRepository;
        this.groupRepository = groupRepository;
        this.providerRepository = providerRepository;
    }

    @Transactional(readOnly = true)
    public List<PayerContact> findByPayerId(Long payerId) {
        requirePayer(payerId);
        return contactRepository.findByPayerIdWithScope(payerId);
    }

    /** Contacts tied to neither a group nor a provider. */
    @Transactional(readOnly = true)
    public List<PayerContact> findGeneralContacts(Long payerId) {
        requirePayer(payerId);
        return contactRepository.findByPayerIdAndGroupIsNullAndProviderIsNull(payerId);
    }

    @Transactional(readOnly = true)
    public List<PayerContact> findForGroup(Long groupId) {
        return contactRepository.findByGroupId(groupId);
    }

    @Transactional(readOnly = true)
    public List<PayerContact> findForProvider(Long providerId) {
        return contactRepository.findByProviderId(providerId);
    }

    @Transactional(readOnly = true)
    public PayerContact findByIdAndPayerId(Long id, Long payerId) {
        return contactRepository.findByIdAndPayerId(id, payerId)
                .orElseThrow(() -> new PayerContactNotFoundException(id, payerId));
    }

    /** A contact for the payer as a whole. */
    @Transactional
    public PayerContact addContact(Long payerId, String role, Consumer<PayerContact> details) {
        Payer payer = requirePayer(payerId);
        PayerContact contact = PayerContact.forPayer(payer, role);
        details.accept(contact);
        return contactRepository.save(contact);
    }

    /** A contact for one group's dealings with this payer. */
    @Transactional
    public PayerContact addGroupContact(Long payerId, Long groupId, String role,
                                        Consumer<PayerContact> details) {
        Payer payer = requirePayer(payerId);
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException(groupId));
        PayerContact contact = PayerContact.forGroup(payer, group, role);
        details.accept(contact);
        return contactRepository.save(contact);
    }

    /** A contact for one provider's dealings with this payer. */
    @Transactional
    public PayerContact addProviderContact(Long payerId, Long providerId, String role,
                                           Consumer<PayerContact> details) {
        Payer payer = requirePayer(payerId);
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ProviderNotFoundException(providerId));
        PayerContact contact = PayerContact.forProvider(payer, provider, role);
        details.accept(contact);
        return contactRepository.save(contact);
    }

    @Transactional
    public PayerContact update(Long id, Long payerId, Consumer<PayerContact> changes) {
        PayerContact contact = contactRepository.findByIdAndPayerId(id, payerId)
                .orElseThrow(() -> new PayerContactNotFoundException(id, payerId));
        changes.accept(contact);
        return contact;
    }

    /**
     * Moves a contact to a different scope. Passing both ids is rejected here rather than
     * left to the database.
     */
    @Transactional
    public PayerContact rescope(Long id, Long payerId, Long groupId, Long providerId) {
        if (groupId != null && providerId != null) {
            throw new IllegalArgumentException(
                    "A contact can be tied to a group or a provider, not both");
        }
        PayerContact contact = contactRepository.findByIdAndPayerId(id, payerId)
                .orElseThrow(() -> new PayerContactNotFoundException(id, payerId));

        if (groupId != null) {
            contact.setGroup(groupRepository.findById(groupId)
                    .orElseThrow(() -> new GroupNotFoundException(groupId)));
        } else if (providerId != null) {
            contact.setProvider(providerRepository.findById(providerId)
                    .orElseThrow(() -> new ProviderNotFoundException(providerId)));
        } else {
            contact.setGroup(null);
            contact.setProvider(null);
        }
        return contact;
    }

    @Transactional
    public void delete(Long id, Long payerId) {
        PayerContact contact = contactRepository.findByIdAndPayerId(id, payerId)
                .orElseThrow(() -> new PayerContactNotFoundException(id, payerId));
        contactRepository.delete(contact);
    }

    private Payer requirePayer(Long payerId) {
        return payerRepository.findById(payerId)
                .orElseThrow(() -> new PayerNotFoundException(payerId));
    }
}
