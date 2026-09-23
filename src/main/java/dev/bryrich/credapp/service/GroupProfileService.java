package dev.bryrich.credapp.service;

import dev.bryrich.credapp.dto.GroupForm;
import dev.bryrich.credapp.dto.GroupProfileForm;
import dev.bryrich.credapp.dto.GroupProfileForm.LocationRow;
import dev.bryrich.credapp.dto.GroupProfileForm.OwnerRow;
import dev.bryrich.credapp.dto.GroupProfileForm.PolicyRow;
import dev.bryrich.credapp.dto.GroupProfileForm.RelationRow;
import dev.bryrich.credapp.dto.GroupProviderForm;
import dev.bryrich.credapp.dto.GroupTaxonomyForm;
import dev.bryrich.credapp.entity.Group;
import dev.bryrich.credapp.entity.GroupOwner;
import dev.bryrich.credapp.entity.GroupOwnerRelation;
import dev.bryrich.credapp.entity.GroupOwnerRelationId;
import dev.bryrich.credapp.entity.GroupTaxonomy;
import dev.bryrich.credapp.entity.MalpracticePolicy;
import dev.bryrich.credapp.entity.Owner;
import dev.bryrich.credapp.entity.enums.Relationship;
import dev.bryrich.credapp.exception.MalpracticePolicyNotFoundException;
import dev.bryrich.credapp.exception.OwnerNotFoundException;
import dev.bryrich.credapp.exception.TaxonomyNotFoundException;
import dev.bryrich.credapp.repository.GroupOwnerRelationRepository;
import dev.bryrich.credapp.repository.GroupOwnerRepository;
import dev.bryrich.credapp.repository.GroupRepository;
import dev.bryrich.credapp.repository.GroupTaxonomyRepository;
import dev.bryrich.credapp.repository.OwnerRepository;
import dev.bryrich.credapp.repository.TaxonomyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.Errors;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Loads and saves a group together with every list attached to it, for the single group
 * form. A save is all-or-nothing: one transaction covers every section.
 */
@Service
public class GroupProfileService {

    private static final BigDecimal MAX_PERCENT = new BigDecimal("100");

    private final GroupRepository groupRepository;
    private final GroupService groupService;
    private final GroupLocationService locationService;
    private final GroupOwnershipService ownershipService;
    private final GroupOwnerRepository groupOwnerRepository;
    private final GroupOwnerRelationRepository relationRepository;
    private final OwnerRepository ownerRepository;
    private final OwnerService ownerService;
    private final GroupProviderService groupProviderService;
    private final GroupTaxonomyService taxonomyService;
    private final GroupTaxonomyRepository groupTaxonomyRepository;
    private final TaxonomyRepository taxonomyRepository;
    private final MalpracticePolicyService policyService;

    public GroupProfileService(GroupRepository groupRepository,
                               GroupService groupService,
                               GroupLocationService locationService,
                               GroupOwnershipService ownershipService,
                               GroupOwnerRepository groupOwnerRepository,
                               GroupOwnerRelationRepository relationRepository,
                               OwnerRepository ownerRepository,
                               OwnerService ownerService,
                               GroupProviderService groupProviderService,
                               GroupTaxonomyService taxonomyService,
                               GroupTaxonomyRepository groupTaxonomyRepository,
                               TaxonomyRepository taxonomyRepository,
                               MalpracticePolicyService policyService) {
        this.groupRepository = groupRepository;
        this.groupService = groupService;
        this.locationService = locationService;
        this.ownershipService = ownershipService;
        this.groupOwnerRepository = groupOwnerRepository;
        this.relationRepository = relationRepository;
        this.ownerRepository = ownerRepository;
        this.ownerService = ownerService;
        this.groupProviderService = groupProviderService;
        this.taxonomyService = taxonomyService;
        this.groupTaxonomyRepository = groupTaxonomyRepository;
        this.taxonomyRepository = taxonomyRepository;
        this.policyService = policyService;
    }

    // ============ load ============

    @Transactional(readOnly = true)
    public GroupProfileForm load(Long groupId) {
        Group group = groupService.findById(groupId);
        GroupProfileForm form = new GroupProfileForm();
        form.setDetails(GroupForm.from(group));
        form.setLocations(mapAll(locationService.findByGroupId(groupId), LocationRow::from));
        form.setOwners(mapAll(ownershipService.findOwners(groupId), OwnerRow::from));
        form.setRelations(mapAll(ownershipService.findRelations(groupId), RelationRow::from));
        form.setProviders(mapAll(groupProviderService.findProviders(groupId), GroupProviderForm::from));
        form.setTaxonomies(mapAll(taxonomyService.findByGroupId(groupId), GroupTaxonomyForm::from));
        form.setPolicies(mapAll(policyService.findByGroupId(groupId), PolicyRow::from));
        return form;
    }

    /**
     * What each owner row is called in the relationship pickers, keyed by row key. The page
     * script rebuilds these as rows change; this is what the server-rendered page starts with.
     */
    @Transactional(readOnly = true)
    public Map<String, String> ownerLabels(GroupProfileForm form) {
        Set<Long> ids = form.getOwners().stream()
                .filter(Objects::nonNull)
                .map(OwnerRow::getOwnerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Owner> people = ownerRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Owner::getId, owner -> owner));
        Map<String, String> labels = new HashMap<>();
        for (OwnerRow row : form.getOwners()) {
            if (row == null || row.getKey() == null) {
                continue;
            }
            String label;
            if (row.createsNewOwner()) {
                label = row.getNewOwner() == null ? "New owner"
                        : (nullToEmpty(row.getNewOwner().getFirstName()) + " "
                        + nullToEmpty(row.getNewOwner().getLastName())).trim();
            } else {
                Owner owner = people.get(row.getOwnerId());
                label = owner == null ? "" : owner.getLastName() + ", " + owner.getFirstName();
            }
            labels.put(row.getKey(), label.isEmpty() ? "New owner (not saved yet)" : label);
        }
        return labels;
    }

    // ============ validate ============

    /**
     * Rules that span rows or need the database. Rows can be null if the page skipped an
     * index, so every check steps over them, and errors keep the row's submitted index.
     */
    @Transactional(readOnly = true)
    public void validate(GroupProfileForm form, Errors errors) {
        Set<String> ownerKeys = checkOwners(form, errors);
        checkRelations(form, ownerKeys, errors);
        checkProviders(form, errors);
        checkTaxonomies(form, errors);
        checkPolicies(form, errors);
    }

    /** Gives rows a key if the page didn't, and returns the keys in use. */
    private Set<String> checkOwners(GroupProfileForm form, Errors errors) {
        Set<String> keys = new HashSet<>();
        Set<Long> people = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        List<OwnerRow> rows = form.getOwners();
        for (int i = 0; i < rows.size(); i++) {
            OwnerRow row = rows.get(i);
            if (row == null) {
                continue;
            }
            String prefix = "owners[" + i + "]";
            if (row.getKey() == null || row.getKey().isBlank() || keys.contains(row.getKey())) {
                row.setKey("row" + i);
            }
            keys.add(row.getKey());

            if (row.createsNewOwner()) {
                if (row.getNewOwner() == null) {
                    errors.rejectValue(prefix + ".mode", "owner.missing", "Enter the new owner's name");
                }
            } else if (row.getOwnerId() == null) {
                errors.rejectValue(prefix + ".ownerId", "owner.missing", "Pick an owner, or switch to a new owner");
            } else if (!people.add(row.getOwnerId())) {
                errors.rejectValue(prefix + ".ownerId", "duplicate", "This owner is already listed");
            } else if (!ownerRepository.existsById(row.getOwnerId())) {
                errors.rejectValue(prefix + ".ownerId", "notFound", "That owner no longer exists");
            }

            if (row.getPercentOwned() != null) {
                total = total.add(row.getPercentOwned());
                if (total.compareTo(MAX_PERCENT) > 0) {
                    errors.rejectValue(prefix + ".percentOwned", "percent.exceeded",
                            "Owners add up to " + total.stripTrailingZeros().toPlainString()
                                    + "%; the total can't pass 100%");
                }
            }
        }
        return keys;
    }

    private void checkRelations(GroupProfileForm form, Set<String> ownerKeys, Errors errors) {
        Map<String, Long> savedOwnerByKey = new HashMap<>();
        form.getOwners().stream()
                .filter(Objects::nonNull)
                .filter(row -> !row.createsNewOwner() && row.getOwnerId() != null)
                .forEach(row -> savedOwnerByKey.put(row.getKey(), row.getOwnerId()));

        Set<String> pairs = new HashSet<>();
        List<RelationRow> rows = form.getRelations();
        for (int i = 0; i < rows.size(); i++) {
            RelationRow row = rows.get(i);
            if (row == null || row.getOwnerKey() == null || row.getRelatedOwnerKey() == null) {
                continue;
            }
            String prefix = "relations[" + i + "]";
            if (!ownerKeys.contains(row.getOwnerKey())) {
                errors.rejectValue(prefix + ".ownerKey", "owner.removed", "That owner was removed above");
                continue;
            }
            if (!ownerKeys.contains(row.getRelatedOwnerKey())) {
                errors.rejectValue(prefix + ".relatedOwnerKey", "owner.removed", "That owner was removed above");
                continue;
            }
            Long a = savedOwnerByKey.get(row.getOwnerKey());
            Long b = savedOwnerByKey.get(row.getRelatedOwnerKey());
            if (row.getOwnerKey().equals(row.getRelatedOwnerKey()) || (a != null && a.equals(b))) {
                errors.rejectValue(prefix + ".relatedOwnerKey", "relation.self", "Pick two different owners");
                continue;
            }
            String pair = row.getOwnerKey().compareTo(row.getRelatedOwnerKey()) < 0
                    ? row.getOwnerKey() + "|" + row.getRelatedOwnerKey()
                    : row.getRelatedOwnerKey() + "|" + row.getOwnerKey();
            if (!pairs.add(pair)) {
                errors.rejectValue(prefix + ".relatedOwnerKey", "duplicate",
                        "These two owners already have a relationship listed");
            }
        }
    }

    private void checkProviders(GroupProfileForm form, Errors errors) {
        Set<Long> seen = new HashSet<>();
        List<GroupProviderForm> rows = form.getProviders();
        for (int i = 0; i < rows.size(); i++) {
            GroupProviderForm row = rows.get(i);
            if (row != null && row.getProviderId() != null && !seen.add(row.getProviderId())) {
                errors.rejectValue("providers[" + i + "].providerId", "duplicate", "This provider is already listed");
            }
        }
    }

    private void checkTaxonomies(GroupProfileForm form, Errors errors) {
        Set<String> seen = new HashSet<>();
        boolean primaryTaken = false;
        List<GroupTaxonomyForm> rows = form.getTaxonomies();
        for (int i = 0; i < rows.size(); i++) {
            GroupTaxonomyForm row = rows.get(i);
            if (row == null) {
                continue;
            }
            if (row.getCode() != null && !seen.add(row.getCode())) {
                errors.rejectValue("taxonomies[" + i + "].code", "duplicate", "This specialty is already listed");
            }
            if (row.isPrimary()) {
                if (primaryTaken) {
                    errors.rejectValue("taxonomies[" + i + "].primary", "primary.multiple",
                            "Only one specialty can be primary");
                }
                primaryTaken = true;
            }
        }
    }

    private void checkPolicies(GroupProfileForm form, Errors errors) {
        Set<String> numbers = new HashSet<>();
        List<PolicyRow> rows = form.getPolicies();
        for (int i = 0; i < rows.size(); i++) {
            PolicyRow row = rows.get(i);
            if (row != null && row.getCarrierName() != null && row.getPolicyNumber() != null
                    && !numbers.add(row.getCarrierName() + "\u0000" + row.getPolicyNumber())) {
                errors.rejectValue("policies[" + i + "].policyNumber", "duplicate",
                        "This carrier and policy number are already listed");
            }
        }
    }

    // ============ save ============

    /**
     * Creates the group when groupId is null, otherwise updates it. Returns the saved group.
     * Run validate first; this assumes the rows already passed it.
     */
    @Transactional
    public Group save(Long groupId, GroupProfileForm form) {
        form.compact();
        Group group = groupId == null
                ? groupService.create(form.getDetails().toEntity())
                : groupService.update(groupId, form.getDetails()::applyTo);
        Long id = group.getId();

        saveLocations(id, form.getLocations());
        saveProviders(id, form.getProviders());
        saveTaxonomies(group, form.getTaxonomies());
        saveOwnersAndRelations(group, form.getOwners(), form.getRelations());
        savePolicies(id, form.getPolicies());

        groupRepository.flush();
        return group;
    }

    private void saveLocations(Long groupId, List<LocationRow> rows) {
        Set<Long> kept = rows.stream().map(LocationRow::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        locationService.findByGroupId(groupId).stream()
                .filter(existing -> !kept.contains(existing.getId()))
                .forEach(existing -> locationService.delete(existing.getId(), groupId));
        groupRepository.flush();
        for (LocationRow row : rows) {
            if (row.getId() == null) {
                locationService.addLocation(groupId, row.toEntity());
            } else {
                locationService.update(row.getId(), groupId, row::applyTo);
            }
        }
    }

    private void saveProviders(Long groupId, List<GroupProviderForm> rows) {
        Set<Long> wanted = rows.stream().map(GroupProviderForm::getProviderId).collect(Collectors.toSet());
        groupProviderService.findProviders(groupId).stream()
                .filter(existing -> !wanted.contains(existing.getProvider().getId()))
                .forEach(existing -> groupProviderService.unassign(groupId, existing.getProvider().getId()));
        groupRepository.flush();
        for (GroupProviderForm row : rows) {
            groupProviderService.assign(groupId, row.getProviderId(), row.getEffectiveDate());
        }
    }

    /** Same approach as the provider side: clear every kept primary and flush, then set one. */
    private void saveTaxonomies(Group group, List<GroupTaxonomyForm> rows) {
        Set<String> wanted = rows.stream().map(GroupTaxonomyForm::getCode).collect(Collectors.toSet());
        Map<String, GroupTaxonomy> kept = new HashMap<>();
        for (GroupTaxonomy existing : groupTaxonomyRepository.findByGroupId(group.getId())) {
            String code = existing.getTaxonomy().getCode();
            if (wanted.contains(code)) {
                existing.setPrimary(false);
                kept.put(code, existing);
            } else {
                groupTaxonomyRepository.delete(existing);
            }
        }
        groupRepository.flush();

        for (GroupTaxonomyForm row : rows) {
            GroupTaxonomy existing = kept.get(row.getCode());
            if (existing != null) {
                existing.setPrimary(row.isPrimary());
            } else {
                groupTaxonomyRepository.save(new GroupTaxonomy(group,
                        taxonomyRepository.findById(row.getCode())
                                .orElseThrow(() -> new TaxonomyNotFoundException(row.getCode())),
                        row.isPrimary()));
            }
        }
        groupRepository.flush();
    }

    /**
     * New owners are created first so every row has an owner id. Relationships reference
     * two stakes in this group, so stale relationships and removed stakes go out before the
     * stakes are written, and new relationships go in after.
     */
    private void saveOwnersAndRelations(Group group, List<OwnerRow> ownerRows, List<RelationRow> relationRows) {
        Long groupId = group.getId();

        Map<String, Long> ownerIdByKey = new HashMap<>();
        for (OwnerRow row : ownerRows) {
            Long ownerId = row.createsNewOwner()
                    ? ownerService.create(row.getNewOwner().toEntity()).getId()
                    : row.getOwnerId();
            ownerIdByKey.put(row.getKey(), ownerId);
        }

        Map<GroupOwnerRelationId, Relationship> wantedRelations = new HashMap<>();
        for (RelationRow row : relationRows) {
            GroupOwnerRelation relation = GroupOwnerRelation.of(groupId,
                    ownerIdByKey.get(row.getOwnerKey()), ownerIdByKey.get(row.getRelatedOwnerKey()),
                    row.getRelationship());
            wantedRelations.put(relation.getId(), relation.getRelationship());
        }
        Set<GroupOwnerRelationId> keptRelations = new HashSet<>();
        for (GroupOwnerRelation existing : relationRepository.findByIdGroupId(groupId)) {
            if (existing.getRelationship() == wantedRelations.get(existing.getId())) {
                keptRelations.add(existing.getId());
            } else {
                relationRepository.delete(existing);
            }
        }

        Set<Long> wantedOwners = new HashSet<>(ownerIdByKey.values());
        Map<Long, GroupOwner> stakes = new HashMap<>();
        for (GroupOwner existing : groupOwnerRepository.findByGroupId(groupId)) {
            if (wantedOwners.contains(existing.getOwner().getId())) {
                stakes.put(existing.getOwner().getId(), existing);
            } else {
                groupOwnerRepository.delete(existing);
            }
        }
        groupRepository.flush();

        for (OwnerRow row : ownerRows) {
            Long ownerId = ownerIdByKey.get(row.getKey());
            GroupOwner stake = stakes.get(ownerId);
            if (stake != null) {
                stake.setPercentOwned(row.getPercentOwned());
                stake.setEffectiveDate(row.getEffectiveDate());
            } else {
                Owner owner = ownerRepository.findById(ownerId)
                        .orElseThrow(() -> new OwnerNotFoundException(ownerId));
                groupOwnerRepository.save(new GroupOwner(group, owner, row.getPercentOwned(), row.getEffectiveDate()));
            }
        }
        groupRepository.flush();

        wantedRelations.forEach((relationId, relationship) -> {
            if (!keptRelations.contains(relationId)) {
                relationRepository.save(GroupOwnerRelation.of(groupId,
                        relationId.getOwnerId(), relationId.getRelatedOwnerId(), relationship));
            }
        });
        groupRepository.flush();
    }

    private void savePolicies(Long groupId, List<PolicyRow> rows) {
        List<MalpracticePolicy> existing = policyService.findByGroupId(groupId);
        Set<Long> own = existing.stream().map(MalpracticePolicy::getId).collect(Collectors.toSet());
        Set<Long> kept = rows.stream().map(PolicyRow::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        existing.stream()
                .filter(policy -> !kept.contains(policy.getId()))
                .forEach(policy -> policyService.delete(policy.getId()));
        groupRepository.flush();
        for (PolicyRow row : rows) {
            if (row.getId() == null) {
                MalpracticePolicy policy = policyService.addForGroup(groupId, row.getPolicyNumber(),
                        row.getCarrierName(), row.getTypeOfCoverage(), row.getEffectiveDate(),
                        row.getSharedIndividual());
                row.applyTo(policy);
            } else {
                if (!own.contains(row.getId())) {
                    throw new MalpracticePolicyNotFoundException(row.getId());
                }
                policyService.update(row.getId(), row::applyTo);
            }
        }
    }

    // ============ helpers ============

    private static <E, R> List<R> mapAll(List<E> items, Function<E, R> mapper) {
        return items.stream().map(mapper).collect(Collectors.toCollection(ArrayList::new));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
