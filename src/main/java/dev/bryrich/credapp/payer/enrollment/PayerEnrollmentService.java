package dev.bryrich.credapp.payer.enrollment;

import dev.bryrich.credapp.group.Group;
import dev.bryrich.credapp.payer.Payer;
import dev.bryrich.credapp.payer.PayerContact;
import dev.bryrich.credapp.payer.PayerContactRepository;
import dev.bryrich.credapp.payer.PayerNotFoundException;
import dev.bryrich.credapp.payer.PayerRepository;
import dev.bryrich.credapp.provider.Provider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.Errors;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Providers' and groups' enrollments with payers: where each application stands, and for
 * a group, the payer's account rep assigned to it. The provider and group forms save these
 * through their profile services, which call validate and sync here inside their own
 * transaction.
 */
@Service
public class PayerEnrollmentService {

    private final ProviderPayerRepository providerPayers;
    private final GroupPayerRepository groupPayers;
    private final PayerRepository payerRepository;
    private final PayerContactRepository contactRepository;

    public PayerEnrollmentService(ProviderPayerRepository providerPayers,
                                  GroupPayerRepository groupPayers,
                                  PayerRepository payerRepository,
                                  PayerContactRepository contactRepository) {
        this.providerPayers = providerPayers;
        this.groupPayers = groupPayers;
        this.payerRepository = payerRepository;
        this.contactRepository = contactRepository;
    }

    // ============ reading ============

    @Transactional(readOnly = true)
    public List<ProviderPayer> findForProvider(Long providerId) {
        return providerPayers.findByProviderId(providerId);
    }

    @Transactional(readOnly = true)
    public List<GroupPayer> findForGroup(Long groupId) {
        return groupPayers.findByGroupId(groupId);
    }

    @Transactional(readOnly = true)
    public List<ProviderPayer> findProvidersForPayer(Long payerId) {
        return providerPayers.findByPayerId(payerId);
    }

    @Transactional(readOnly = true)
    public List<GroupPayer> findGroupsForPayer(Long payerId) {
        return groupPayers.findByPayerId(payerId);
    }

    /** Contacts that can be picked as a group's rep; see PayerContactRepository.findRepCandidates. */
    @Transactional(readOnly = true)
    public List<PayerContact> repCandidates(Long groupId) {
        return contactRepository.findRepCandidates(groupId);
    }

    /**
     * For a provider's page: by payer, the account reps their groups have at that payer.
     * Providers don't get their own rep; they work through their group's.
     */
    @Transactional(readOnly = true)
    public Map<Long, List<GroupPayer>> groupRepsByPayer(Collection<Long> groupIds) {
        if (groupIds.isEmpty()) {
            return Map.of();
        }
        return groupPayers.findByGroupIdIn(groupIds).stream()
                .filter(enrollment -> enrollment.getAccountRep() != null)
                .collect(Collectors.groupingBy(enrollment -> enrollment.getPayer().getId(),
                        LinkedHashMap::new, Collectors.toList()));
    }

    // ============ checking a form's rows ============

    /**
     * Rules across a form's payer rows: each payer once, the payer still there, and a rep
     * who is one of that payer's contacts and not tied to another group or to a provider.
     * groupId is the group being edited, or null for providers and for a group not saved yet.
     */
    @Transactional(readOnly = true)
    public void validate(List<? extends EnrollmentForm> rows, String list, Long groupId, Errors errors) {
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            EnrollmentForm row = rows.get(i);
            if (row == null || row.getPayerId() == null) {
                continue;
            }
            String prefix = list + "[" + i + "]";
            if (!seen.add(row.getPayerId())) {
                errors.rejectValue(prefix + ".payerId", "duplicate", "This payer is already listed");
                continue;
            }
            if (!payerRepository.existsById(row.getPayerId())) {
                errors.rejectValue(prefix + ".payerId", "notFound", "That payer no longer exists");
                continue;
            }
            if (row instanceof GroupPayerForm groupRow && groupRow.getAccountRepId() != null) {
                String problem = repProblem(groupRow.getAccountRepId(), row.getPayerId(), groupId);
                if (problem != null) {
                    errors.rejectValue(prefix + ".accountRepId", "rep.invalid", problem);
                }
            }
        }
    }

    private String repProblem(Long contactId, Long payerId, Long groupId) {
        PayerContact contact = contactRepository.findById(contactId).orElse(null);
        if (contact == null) {
            return "That contact no longer exists";
        }
        if (!contact.getPayer().getId().equals(payerId)) {
            return "Pick one of this payer's contacts";
        }
        if (contact.getProvider() != null
                || (contact.getGroup() != null && !contact.getGroup().getId().equals(groupId))) {
            return "That contact is tied to someone else; pick a payer-wide contact or one for this group";
        }
        return null;
    }

    // ============ saving a form's rows ============

    /** Brings a provider's enrollments in line with the form's rows. Run validate first. */
    @Transactional
    public void syncProvider(Provider provider, List<ProviderPayerForm> rows) {
        sync(providerPayers.findByProviderId(provider.getId()), rows,
                (row, payer) -> new ProviderPayer(provider, payer),
                (row, enrollment) -> row.applyTo(enrollment),
                providerPayers::saveAll, providerPayers::deleteAll);
    }

    /** Brings a group's enrollments and reps in line with the form's rows. Run validate first. */
    @Transactional
    public void syncGroup(Group group, List<GroupPayerForm> rows) {
        sync(groupPayers.findByGroupId(group.getId()), rows,
                (row, payer) -> new GroupPayer(group, payer),
                (row, enrollment) -> {
                    row.applyTo(enrollment);
                    enrollment.setAccountRep(row.getAccountRepId() == null ? null
                            : contactRepository.findById(row.getAccountRepId()).orElse(null));
                },
                groupPayers::saveAll, groupPayers::deleteAll);
    }

    private <R extends EnrollmentForm, E extends Enrollment> void sync(
            List<E> existing, List<R> rows,
            BiFunction<R, Payer, E> create,
            BiConsumer<R, E> apply,
            Function<List<E>, List<E>> saveAll,
            Consumer<List<E>> deleteAll) {
        Map<Long, E> byPayer = new HashMap<>();
        existing.forEach(enrollment -> byPayer.put(enrollment.getPayer().getId(), enrollment));
        Set<Long> wanted = rows.stream().filter(Objects::nonNull).map(EnrollmentForm::getPayerId)
                .collect(Collectors.toSet());

        deleteAll.accept(existing.stream().filter(e -> !wanted.contains(e.getPayer().getId())).toList());

        List<E> toSave = new ArrayList<>();
        for (R row : rows) {
            if (row == null) {
                continue;
            }
            E enrollment = byPayer.get(row.getPayerId());
            if (enrollment == null) {
                Payer payer = payerRepository.findById(row.getPayerId())
                        .orElseThrow(() -> new PayerNotFoundException(row.getPayerId()));
                enrollment = create.apply(row, payer);
            }
            apply.accept(row, enrollment);
            toSave.add(enrollment);
        }
        saveAll.apply(toSave);
    }
}
