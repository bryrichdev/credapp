package dev.bryrich.credapp.provider;

import dev.bryrich.credapp.group.location.GroupLocation;
import dev.bryrich.credapp.group.location.GroupLocationRepository;
import dev.bryrich.credapp.group.membership.GroupProviderService;
import dev.bryrich.credapp.group.membership.ProviderGroupForm;
import dev.bryrich.credapp.license.LicenseService;
import dev.bryrich.credapp.malpractice.MalpracticeClaim;
import dev.bryrich.credapp.malpractice.MalpracticeClaimService;
import dev.bryrich.credapp.malpractice.MalpracticePolicy;
import dev.bryrich.credapp.malpractice.MalpracticePolicyNotFoundException;
import dev.bryrich.credapp.malpractice.MalpracticePolicyRepository;
import dev.bryrich.credapp.malpractice.MalpracticePolicyService;
import dev.bryrich.credapp.provider.ProviderProfileForm.ClaimRow;
import dev.bryrich.credapp.provider.ProviderProfileForm.PolicyRow;
import dev.bryrich.credapp.provider.certification.CertificationService;
import dev.bryrich.credapp.provider.disclosure.CriminalChargeService;
import dev.bryrich.credapp.provider.location.ProviderLocationForm;
import dev.bryrich.credapp.provider.location.ProviderLocationService;
import dev.bryrich.credapp.provider.privilege.HospitalPrivilegeService;
import dev.bryrich.credapp.provider.reference.ProviderReferenceService;
import dev.bryrich.credapp.taxonomy.ProviderTaxonomy;
import dev.bryrich.credapp.taxonomy.ProviderTaxonomyForm;
import dev.bryrich.credapp.taxonomy.ProviderTaxonomyRepository;
import dev.bryrich.credapp.taxonomy.ProviderTaxonomyService;
import dev.bryrich.credapp.taxonomy.TaxonomyNotFoundException;
import dev.bryrich.credapp.taxonomy.TaxonomyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.Errors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Loads and saves a provider together with every list attached to them, for the single
 * provider form. A save is all-or-nothing: one transaction covers every section.
 *
 * The per-item services still do the work, so their ownership checks apply. This class
 * only decides what to add, change, or remove, and in what order.
 */
@Service
public class ProviderProfileService {

    private static final Pattern SAVED_POLICY_KEY = Pattern.compile("^p(\\d+)$");

    private final ProviderRepository providerRepository;
    private final ProviderService providerService;
    private final GroupProviderService groupProviderService;
    private final ProviderLocationService locationService;
    private final GroupLocationRepository groupLocationRepository;
    private final ProviderTaxonomyService taxonomyService;
    private final ProviderTaxonomyRepository providerTaxonomyRepository;
    private final TaxonomyRepository taxonomyRepository;
    private final LicenseService licenseService;
    private final CertificationService certificationService;
    private final HospitalPrivilegeService privilegeService;
    private final MalpracticePolicyService policyService;
    private final MalpracticePolicyRepository policyRepository;
    private final MalpracticeClaimService claimService;
    private final ProviderReferenceService referenceService;
    private final CriminalChargeService chargeService;

    public ProviderProfileService(ProviderRepository providerRepository,
                                  ProviderService providerService,
                                  GroupProviderService groupProviderService,
                                  ProviderLocationService locationService,
                                  GroupLocationRepository groupLocationRepository,
                                  ProviderTaxonomyService taxonomyService,
                                  ProviderTaxonomyRepository providerTaxonomyRepository,
                                  TaxonomyRepository taxonomyRepository,
                                  LicenseService licenseService,
                                  CertificationService certificationService,
                                  HospitalPrivilegeService privilegeService,
                                  MalpracticePolicyService policyService,
                                  MalpracticePolicyRepository policyRepository,
                                  MalpracticeClaimService claimService,
                                  ProviderReferenceService referenceService,
                                  CriminalChargeService chargeService) {
        this.providerRepository = providerRepository;
        this.providerService = providerService;
        this.groupProviderService = groupProviderService;
        this.locationService = locationService;
        this.groupLocationRepository = groupLocationRepository;
        this.taxonomyService = taxonomyService;
        this.providerTaxonomyRepository = providerTaxonomyRepository;
        this.taxonomyRepository = taxonomyRepository;
        this.licenseService = licenseService;
        this.certificationService = certificationService;
        this.privilegeService = privilegeService;
        this.policyService = policyService;
        this.policyRepository = policyRepository;
        this.claimService = claimService;
        this.referenceService = referenceService;
        this.chargeService = chargeService;
    }

    // ============ load ============

    /** The edit screen's starting point: everything on file, as form rows. */
    @Transactional(readOnly = true)
    public ProviderProfileForm load(Long providerId) {
        Provider provider = providerService.findById(providerId);
        ProviderProfileForm form = new ProviderProfileForm();
        form.setDetails(ProviderForm.from(provider));
        form.setGroups(mapAll(groupProviderService.findGroups(providerId), ProviderGroupForm::from));
        form.setLocations(mapAll(locationService.findByProviderId(providerId), ProviderLocationForm::from));
        form.setTaxonomies(mapAll(taxonomyService.findByProviderId(providerId), ProviderTaxonomyForm::from));
        form.setLicenses(mapAll(licenseService.findByProviderId(providerId), ProviderProfileForm.LicenseRow::from));
        form.setCertifications(mapAll(certificationService.findByProviderId(providerId),
                ProviderProfileForm.CertificationRow::from));
        form.setPrivileges(mapAll(privilegeService.findByProviderId(providerId), ProviderProfileForm.PrivilegeRow::from));
        form.setPolicies(mapAll(policyService.findByProviderId(providerId), PolicyRow::from));
        form.setClaims(mapAll(claimService.findByProviderId(providerId), ClaimRow::from));
        form.setReferences(mapAll(referenceService.findByProviderId(providerId), ProviderProfileForm.ReferenceRow::from));
        form.setCharges(mapAll(chargeService.findByProviderId(providerId), ProviderProfileForm.ChargeRow::from));
        return form;
    }

    /**
     * Policies this provider's claims point at that the provider doesn't hold, such as a
     * group's policy. The claim picker lists them so an edit doesn't quietly unlink them.
     */
    @Transactional(readOnly = true)
    public List<MalpracticePolicy> findOutsidePolicies(Long providerId) {
        if (providerId == null) {
            return List.of();
        }
        return claimService.findByProviderId(providerId).stream()
                .map(MalpracticeClaim::getPolicy)
                .filter(Objects::nonNull)
                .filter(policy -> policy.getProvider() == null
                        || !providerId.equals(policy.getProvider().getId()))
                .distinct()
                .toList();
    }

    // ============ validate ============

    /**
     * Rules that span rows or need the database. Field rules on each row are handled by
     * bean validation before this runs. Rows can be null if the page skipped an index, so
     * every check steps over them, and errors keep the row's submitted index.
     */
    @Transactional(readOnly = true)
    public void validate(Long providerId, ProviderProfileForm form, Errors errors) {
        Set<Long> groupIds = checkGroups(form, errors);
        checkLocations(form, groupIds, errors);
        checkTaxonomies(form, errors);
        checkPrivileges(providerId, form, errors);
        Set<String> policyKeys = checkPolicies(form, errors);
        checkClaims(providerId, form, policyKeys, errors);
    }

    private Set<Long> checkGroups(ProviderProfileForm form, Errors errors) {
        Set<Long> seen = new HashSet<>();
        List<ProviderGroupForm> rows = form.getGroups();
        for (int i = 0; i < rows.size(); i++) {
            ProviderGroupForm row = rows.get(i);
            if (row == null || row.getGroupId() == null) {
                continue;
            }
            if (!seen.add(row.getGroupId())) {
                errors.rejectValue("groups[" + i + "].groupId", "duplicate", "This group is already listed");
            }
        }
        return seen;
    }

    private void checkLocations(ProviderProfileForm form, Set<Long> groupIds, Errors errors) {
        Set<Long> seen = new HashSet<>();
        List<ProviderLocationForm> rows = form.getLocations();
        for (int i = 0; i < rows.size(); i++) {
            ProviderLocationForm row = rows.get(i);
            if (row == null || row.getLocationId() == null) {
                continue;
            }
            String field = "locations[" + i + "].locationId";
            if (!seen.add(row.getLocationId())) {
                errors.rejectValue(field, "duplicate", "This location is already listed");
                continue;
            }
            GroupLocation location = groupLocationRepository.findById(row.getLocationId()).orElse(null);
            if (location == null) {
                errors.rejectValue(field, "notFound", "That location no longer exists");
            } else if (!groupIds.contains(location.getGroup().getId())) {
                errors.rejectValue(field, "group.missing",
                        "Add " + location.getGroup().getLbn() + " under Groups to use this location");
            }
        }
    }

    private void checkTaxonomies(ProviderProfileForm form, Errors errors) {
        Set<String> seen = new HashSet<>();
        boolean primaryTaken = false;
        List<ProviderTaxonomyForm> rows = form.getTaxonomies();
        for (int i = 0; i < rows.size(); i++) {
            ProviderTaxonomyForm row = rows.get(i);
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

    private void checkPrivileges(Long providerId, ProviderProfileForm form, Errors errors) {
        if (providerId == null) {
            return;
        }
        List<ProviderProfileForm.PrivilegeRow> rows = form.getPrivileges();
        for (int i = 0; i < rows.size(); i++) {
            ProviderProfileForm.PrivilegeRow row = rows.get(i);
            if (row != null && providerId.equals(row.getAdmittingPhysicianId())) {
                errors.rejectValue("privileges[" + i + "].admittingPhysicianId", "self",
                        "A provider cannot admit for themselves");
            }
        }
    }

    /** Gives new rows a key if the page didn't, flags repeats, and returns the keys in use. */
    private Set<String> checkPolicies(ProviderProfileForm form, Errors errors) {
        Set<String> keys = new HashSet<>();
        Set<String> numbers = new HashSet<>();
        List<PolicyRow> rows = form.getPolicies();
        for (int i = 0; i < rows.size(); i++) {
            PolicyRow row = rows.get(i);
            if (row == null) {
                continue;
            }
            if (row.getId() != null) {
                row.setKey(PolicyRow.keyFor(row.getId()));
            } else if (row.getKey() == null || row.getKey().isBlank()
                    || SAVED_POLICY_KEY.matcher(row.getKey()).matches() || keys.contains(row.getKey())) {
                row.setKey("new" + i);
            }
            keys.add(row.getKey());
            if (row.getCarrierName() != null && row.getPolicyNumber() != null
                    && !numbers.add(row.getCarrierName() + "\u0000" + row.getPolicyNumber())) {
                errors.rejectValue("policies[" + i + "].policyNumber", "duplicate",
                        "This carrier and policy number are already listed");
            }
        }
        return keys;
    }

    private void checkClaims(Long providerId, ProviderProfileForm form, Set<String> policyKeys, Errors errors) {
        Set<Long> ownPolicyIds = providerId == null ? Set.of()
                : policyService.findByProviderId(providerId).stream()
                .map(MalpracticePolicy::getId)
                .collect(Collectors.toSet());
        Set<String> numbers = new HashSet<>();
        List<ClaimRow> rows = form.getClaims();
        for (int i = 0; i < rows.size(); i++) {
            ClaimRow row = rows.get(i);
            if (row == null) {
                continue;
            }
            if (row.getCarrierName() != null && row.getClaimNumber() != null
                    && !numbers.add(row.getCarrierName() + "\u0000" + row.getClaimNumber())) {
                errors.rejectValue("claims[" + i + "].claimNumber", "duplicate",
                        "This carrier and claim number are already listed");
            }
            String key = blankToNull(row.getPolicyKey());
            if (key == null || policyKeys.contains(key)) {
                continue;
            }
            String field = "claims[" + i + "].policyKey";
            Matcher saved = SAVED_POLICY_KEY.matcher(key);
            if (!saved.matches()) {
                errors.rejectValue(field, "policy.missing", "That policy was removed from this form");
                continue;
            }
            Long policyId = Long.valueOf(saved.group(1));
            if (ownPolicyIds.contains(policyId)) {
                errors.rejectValue(field, "policy.removed",
                        "That policy is being removed; unlink the claim or keep the policy");
            } else if (!policyRepository.existsById(policyId)) {
                errors.rejectValue(field, "policy.missing", "That policy no longer exists");
            }
        }
    }

    // ============ save ============

    /**
     * Creates the provider when providerId is null, otherwise updates them. Returns the
     * saved provider. Run validate first; this assumes the rows already passed it.
     */
    @Transactional
    public Provider save(Long providerId, ProviderProfileForm form) {
        form.compact();
        Provider provider = providerId == null
                ? providerService.create(form.getDetails().toEntity())
                : providerService.update(providerId, form.getDetails()::applyTo);
        Long id = provider.getId();

        saveGroupsAndLocations(id, form);
        saveTaxonomies(provider, form.getTaxonomies());

        sync(licenseService.findByProviderId(id), l -> l.getId(),
                form.getLicenses(), ProviderProfileForm.LicenseRow::getId,
                l -> licenseService.delete(l.getId(), id),
                row -> licenseService.addLicense(id, row.toEntity()),
                row -> licenseService.update(row.getId(), id, row::applyTo));

        sync(certificationService.findByProviderId(id), c -> c.getId(),
                form.getCertifications(), ProviderProfileForm.CertificationRow::getId,
                c -> certificationService.delete(c.getId(), id),
                row -> certificationService.addCertification(id, row.toEntity()),
                row -> certificationService.update(row.getId(), id, row::applyTo));

        sync(referenceService.findByProviderId(id), r -> r.getId(),
                form.getReferences(), ProviderProfileForm.ReferenceRow::getId,
                r -> referenceService.delete(r.getId(), id),
                row -> referenceService.addReference(id, row.toEntity()),
                row -> referenceService.update(row.getId(), id, row::applyTo));

        sync(chargeService.findByProviderId(id), c -> c.getId(),
                form.getCharges(), ProviderProfileForm.ChargeRow::getId,
                c -> chargeService.delete(c.getId(), id),
                row -> chargeService.addCharge(id, row.toEntity()),
                row -> chargeService.update(row.getId(), id, row::applyTo));

        sync(privilegeService.findByProviderId(id), p -> p.getId(),
                form.getPrivileges(), ProviderProfileForm.PrivilegeRow::getId,
                p -> privilegeService.delete(p.getId(), id),
                row -> privilegeService.addPrivilege(id, row.toEntity(), row.getAdmittingPhysicianId()),
                row -> privilegeService.update(row.getId(), id, row.getAdmittingPhysicianId(), row::applyTo));

        savePoliciesAndClaims(id, form.getPolicies(), form.getClaims());

        providerRepository.flush();
        return provider;
    }

    /**
     * Group memberships first, since a practice location is only allowed at a group the
     * provider belongs to. Removals go out before additions.
     */
    private void saveGroupsAndLocations(Long providerId, ProviderProfileForm form) {
        Set<Long> wantedLocations = form.getLocations().stream()
                .map(ProviderLocationForm::getLocationId)
                .collect(Collectors.toSet());
        locationService.findByProviderId(providerId).stream()
                .filter(existing -> !wantedLocations.contains(existing.getLocation().getId()))
                .forEach(existing -> locationService.unassign(existing.getLocation().getId(), providerId));

        Set<Long> wantedGroups = form.getGroups().stream()
                .map(ProviderGroupForm::getGroupId)
                .collect(Collectors.toSet());
        groupProviderService.findGroups(providerId).stream()
                .filter(existing -> !wantedGroups.contains(existing.getGroup().getId()))
                .forEach(existing -> groupProviderService.unassign(existing.getGroup().getId(), providerId));
        providerRepository.flush();

        for (ProviderGroupForm row : form.getGroups()) {
            groupProviderService.assign(row.getGroupId(), providerId, row.getEffectiveDate());
        }
        providerRepository.flush();

        for (ProviderLocationForm row : form.getLocations()) {
            locationService.assign(row.getLocationId(), providerId, row.getPcpScp());
        }
    }

    /**
     * One primary per provider is enforced by a partial unique index, so every kept row is
     * cleared and flushed before the chosen one is set.
     */
    private void saveTaxonomies(Provider provider, List<ProviderTaxonomyForm> rows) {
        Map<String, ProviderTaxonomyForm> wanted = new HashMap<>();
        rows.forEach(row -> wanted.put(row.getCode(), row));

        Map<String, ProviderTaxonomy> kept = new HashMap<>();
        for (ProviderTaxonomy existing : providerTaxonomyRepository.findByProviderId(provider.getId())) {
            String code = existing.getTaxonomy().getCode();
            if (wanted.containsKey(code)) {
                existing.setPrimary(false);
                kept.put(code, existing);
            } else {
                providerTaxonomyRepository.delete(existing);
            }
        }
        providerRepository.flush();

        for (ProviderTaxonomyForm row : rows) {
            ProviderTaxonomy existing = kept.get(row.getCode());
            if (existing != null) {
                existing.setPrimary(row.isPrimary());
            } else {
                providerTaxonomyRepository.save(new ProviderTaxonomy(provider,
                        taxonomyRepository.findById(row.getCode())
                                .orElseThrow(() -> new TaxonomyNotFoundException(row.getCode())),
                        row.isPrimary()));
            }
        }
        providerRepository.flush();
    }

    /**
     * Claims can point at a policy added on the same page, and a removed policy can't go
     * while a claim still points at it. So: drop removed claims, save policies, save claims
     * against them, then drop removed policies.
     */
    private void savePoliciesAndClaims(Long providerId, List<PolicyRow> policyRows, List<ClaimRow> claimRows) {
        Set<Long> keptClaims = idsOf(claimRows, ClaimRow::getId);
        claimService.findByProviderId(providerId).stream()
                .filter(existing -> !keptClaims.contains(existing.getId()))
                .forEach(existing -> claimService.delete(existing.getId(), providerId));
        providerRepository.flush();

        List<MalpracticePolicy> existingPolicies = policyService.findByProviderId(providerId);
        Set<Long> ownPolicyIds = existingPolicies.stream()
                .map(MalpracticePolicy::getId)
                .collect(Collectors.toSet());
        Map<String, MalpracticePolicy> byKey = new HashMap<>();
        for (PolicyRow row : policyRows) {
            MalpracticePolicy policy;
            if (row.getId() != null) {
                if (!ownPolicyIds.contains(row.getId())) {
                    throw new MalpracticePolicyNotFoundException(row.getId());
                }
                policy = policyService.update(row.getId(), row::applyTo);
            } else {
                policy = policyService.addForProvider(providerId, row.getPolicyNumber(),
                        row.getCarrierName(), row.getTypeOfCoverage(), row.getEffectiveDate(),
                        row.getSharedIndividual());
                row.applyTo(policy);
            }
            byKey.put(row.getKey(), policy);
        }
        providerRepository.flush();

        for (ClaimRow row : claimRows) {
            MalpracticePolicy policy = resolvePolicy(row.getPolicyKey(), byKey);
            if (row.getId() != null) {
                claimService.update(row.getId(), providerId, claim -> {
                    row.applyTo(claim);
                    claim.setPolicy(policy);
                });
            } else {
                claimService.addClaim(providerId, row.toEntity(), policy == null ? null : policy.getId());
            }
        }
        providerRepository.flush();

        Set<Long> keptPolicies = idsOf(policyRows, PolicyRow::getId);
        existingPolicies.stream()
                .filter(existing -> !keptPolicies.contains(existing.getId()))
                .forEach(existing -> policyService.delete(existing.getId()));
    }

    private MalpracticePolicy resolvePolicy(String key, Map<String, MalpracticePolicy> byKey) {
        String trimmed = blankToNull(key);
        if (trimmed == null) {
            return null;
        }
        MalpracticePolicy onPage = byKey.get(trimmed);
        if (onPage != null) {
            return onPage;
        }
        Matcher saved = SAVED_POLICY_KEY.matcher(trimmed);
        if (!saved.matches()) {
            return null;
        }
        return policyService.findById(Long.valueOf(saved.group(1)));
    }

    // ============ helpers ============

    /**
     * Brings a list on file in line with the rows submitted: saved items with no matching
     * row are deleted, rows with an id update that item, rows without one are created.
     * Deletes are flushed first so a re-used unique value doesn't collide.
     */
    private <E, R> void sync(List<E> existing, Function<E, Long> entityId,
                             List<R> rows, Function<R, Long> rowId,
                             Consumer<E> delete, Consumer<R> create, Consumer<R> update) {
        Set<Long> kept = idsOf(rows, rowId);
        existing.stream()
                .filter(item -> !kept.contains(entityId.apply(item)))
                .forEach(delete);
        providerRepository.flush();
        for (R row : rows) {
            if (rowId.apply(row) == null) {
                create.accept(row);
            } else {
                update.accept(row);
            }
        }
    }

    private static <R> Set<Long> idsOf(List<R> rows, Function<R, Long> rowId) {
        return rows.stream()
                .map(rowId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private static <E, R> List<R> mapAll(List<E> items, Function<E, R> mapper) {
        return items.stream().map(mapper).collect(Collectors.toCollection(ArrayList::new));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
