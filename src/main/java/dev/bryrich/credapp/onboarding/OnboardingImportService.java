package dev.bryrich.credapp.onboarding;

import dev.bryrich.credapp.group.Group;
import dev.bryrich.credapp.group.GroupProfileForm;
import dev.bryrich.credapp.group.GroupProfileService;
import dev.bryrich.credapp.group.location.GroupLocationService;
import dev.bryrich.credapp.group.membership.ProviderGroupForm;
import dev.bryrich.credapp.malpractice.MalpracticePolicyRepository;
import dev.bryrich.credapp.onboarding.OnboardingPlan.Claim;
import dev.bryrich.credapp.onboarding.OnboardingPlan.GroupPlan;
import dev.bryrich.credapp.onboarding.OnboardingPlan.Item;
import dev.bryrich.credapp.onboarding.OnboardingPlan.LinkedPrivilege;
import dev.bryrich.credapp.onboarding.OnboardingPlan.Membership;
import dev.bryrich.credapp.onboarding.OnboardingPlan.OwnerPlan;
import dev.bryrich.credapp.onboarding.OnboardingPlan.PracticeLocation;
import dev.bryrich.credapp.onboarding.OnboardingPlan.ProviderPlan;
import dev.bryrich.credapp.onboarding.OnboardingTemplate.Sheet;
import dev.bryrich.credapp.onboarding.xlsx.XlsxException;
import dev.bryrich.credapp.onboarding.xlsx.XlsxReader;
import dev.bryrich.credapp.onboarding.xlsx.XlsxWorkbook;
import dev.bryrich.credapp.owner.OwnerService;
import dev.bryrich.credapp.provider.ProviderProfileForm;
import dev.bryrich.credapp.provider.ProviderProfileService;
import dev.bryrich.credapp.provider.location.ProviderLocationForm;
import dev.bryrich.credapp.provider.privilege.HospitalPrivilegeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static dev.bryrich.credapp.onboarding.OnboardingTemplate.ADMITTING_PROVIDER;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.GROUP;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.LOCATION;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.OWNER;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.POLICY;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.PROVIDER;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.RELATED_OWNER;

/**
 * Reads an onboarding workbook and creates everything in it for the signed-in user group.
 *
 * A preview and an import run the same steps: read the file, plan it, then save it through
 * the profile services inside one transaction. A preview always rolls that transaction back,
 * so it catches everything a real save would, database rules included, without keeping
 * anything. An import commits only if nothing at all went wrong.
 */
@Service
public class OnboardingImportService {

    private static final Logger log = LoggerFactory.getLogger(OnboardingImportService.class);

    /** "owners[2].percentOwned" from the profile services' validation. */
    private static final Pattern LIST_FIELD = Pattern.compile("^([a-zA-Z]+)\\[(\\d+)]\\.?(.*)$");

    /** Form properties the services report on that are link columns in the sheets. */
    private static final Map<String, String> LINK_COLUMNS = Map.of(
            "ownerId", OWNER,
            "ownerKey", OWNER,
            "relatedOwnerKey", RELATED_OWNER,
            "groupId", GROUP,
            "locationId", LOCATION,
            "policyKey", POLICY,
            "admittingPhysicianId", ADMITTING_PROVIDER,
            "providerId", PROVIDER);

    private final TransactionTemplate transactions;
    private final OnboardingPlanner planner;
    private final OwnerService ownerService;
    private final GroupProfileService groupProfiles;
    private final GroupLocationService groupLocations;
    private final ProviderProfileService providerProfiles;
    private final HospitalPrivilegeService privileges;
    private final MalpracticePolicyRepository policyRepository;

    public OnboardingImportService(PlatformTransactionManager transactionManager,
                                   OnboardingPlanner planner,
                                   OwnerService ownerService,
                                   GroupProfileService groupProfiles,
                                   GroupLocationService groupLocations,
                                   ProviderProfileService providerProfiles,
                                   HospitalPrivilegeService privileges,
                                   MalpracticePolicyRepository policyRepository) {
        this.transactions = new TransactionTemplate(transactionManager);
        this.planner = planner;
        this.ownerService = ownerService;
        this.groupProfiles = groupProfiles;
        this.groupLocations = groupLocations;
        this.providerProfiles = providerProfiles;
        this.privileges = privileges;
        this.policyRepository = policyRepository;
    }

    /** Everything an import would do, and every problem stopping it. Saves nothing. */
    public ImportReport preview(byte[] file) {
        return run(file, false);
    }

    /** Saves the whole file, or nothing if any problem turns up. */
    public ImportReport importFile(byte[] file) {
        return run(file, true);
    }

    private ImportReport run(byte[] file, boolean commit) {
        Problems problems = new Problems();
        XlsxWorkbook workbook;
        try {
            workbook = XlsxReader.read(new ByteArrayInputStream(file));
        } catch (XlsxException ex) {
            problems.file(ex.getMessage());
            return report(false, problems, Map.of(), null);
        }

        Map<Sheet, List<ParsedRow>> rows = SheetParser.parse(workbook, problems);
        if (problems.isEmpty() && rows.values().stream().allMatch(List::isEmpty)) {
            problems.file("There's nothing to import. Fill in rows under the header on the template's sheets.");
            return report(false, problems, rows, null);
        }

        Saver saver = new Saver(problems);
        OnboardingPlan[] plan = new OnboardingPlan[1];
        try {
            transactions.executeWithoutResult(status -> {
                plan[0] = planner.plan(rows, problems);
                if (problems.isEmpty()) {
                    saver.save(plan[0]);
                }
                if (!commit || !problems.isEmpty()) {
                    status.setRollbackOnly();
                }
            });
        } catch (RuntimeException ex) {
            log.warn("Onboarding import stopped at {}", saver.current == null ? "the start" : saver.current.label(), ex);
            String message = describe(ex);
            if (saver.current == null) {
                problems.file(message);
            } else {
                problems.at(saver.current, null, message);
            }
        }
        return report(commit && problems.isEmpty(), problems, rows, plan[0]);
    }

    private static ImportReport report(boolean saved, Problems problems, Map<Sheet, List<ParsedRow>> rows,
                                       OnboardingPlan plan) {
        List<ImportReport.SheetCount> counts = rows.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .map(entry -> new ImportReport.SheetCount(entry.getKey().name(), entry.getValue().size()))
                .toList();
        return new ImportReport(saved, problems.sorted(), counts,
                plan == null ? List.of() : plan.groupLabels(),
                plan == null ? List.of() : plan.providerLabels());
    }

    private static String describe(RuntimeException ex) {
        if (ex instanceof DataIntegrityViolationException integrity) {
            String cause = integrity.getMostSpecificCause().getMessage();
            String firstLine = cause == null ? "" : cause.lines().findFirst().orElse("");
            return "CredApp couldn't save this row because it breaks a database rule: " + firstLine;
        }
        return ex.getMessage() == null
                ? "CredApp couldn't save this row (" + ex.getClass().getSimpleName() + ")"
                : ex.getMessage();
    }

    // ============ saving ============

    /**
     * Which sheet row each list entry on a profile form came from, so the services' own
     * validation errors ("owners[2].percentOwned") can point back at a cell.
     */
    private static final class Sources {
        private final ParsedRow details;
        private final Map<String, List<ParsedRow>> lists = new HashMap<>();

        Sources(ParsedRow details) {
            this.details = details;
        }

        <T> void add(String list, List<T> formList, T form, ParsedRow row) {
            formList.add(form);
            lists.computeIfAbsent(list, name -> new ArrayList<>()).add(row);
        }

        ParsedRow row(String list, int index) {
            List<ParsedRow> rows = lists.get(list);
            return rows == null || index >= rows.size() ? details : rows.get(index);
        }
    }

    private final class Saver {
        private final Problems problems;
        /** The row being saved, for pinning an unexpected failure to it. */
        private ParsedRow current;

        private final Map<String, Long> ownerIds = new HashMap<>();
        private final Map<String, Long> groupIds = new HashMap<>();
        private final Map<String, Long> locationIds = new HashMap<>();
        private final Map<String, Long> groupPolicyIds = new HashMap<>();
        private final Map<String, Long> providerIds = new HashMap<>();
        /** Groups that didn't pass; anything pointing at them is left off so it isn't reported twice. */
        private final Set<String> failedGroups = new HashSet<>();

        Saver(Problems problems) {
            this.problems = problems;
        }

        void save(OnboardingPlan plan) {
            for (OwnerPlan owner : plan.owners.values()) {
                current = owner.row;
                ownerIds.put(owner.key, ownerService.create(owner.form.toEntity()).getId());
            }
            for (GroupPlan group : plan.groups.values()) {
                saveGroup(group);
            }
            for (ProviderPlan provider : plan.providers.values()) {
                saveProvider(provider, plan);
            }
            for (ProviderPlan provider : plan.providers.values()) {
                for (LinkedPrivilege linked : provider.linkedPrivileges) {
                    Long providerId = providerIds.get(provider.key);
                    Long admittingId = providerIds.get(linked.admittingKey());
                    if (providerId != null && admittingId != null) {
                        current = linked.row();
                        privileges.addPrivilege(providerId, linked.form().toEntity(), admittingId);
                    }
                }
            }
            current = null;
        }

        private void saveGroup(GroupPlan group) {
            GroupProfileForm form = new GroupProfileForm();
            form.setDetails(group.details);
            Sources sources = new Sources(group.row);
            for (Item<GroupProfileForm.OwnerRow> owner : group.owners) {
                owner.form().setOwnerId(ownerIds.get(owner.form().getKey()));
                sources.add("owners", form.getOwners(), owner.form(), owner.row());
            }
            group.relations.forEach(item -> sources.add("relations", form.getRelations(), item.form(), item.row()));
            group.taxonomies.forEach(item -> sources.add("taxonomies", form.getTaxonomies(), item.form(), item.row()));
            group.policies.values().forEach(item -> sources.add("policies", form.getPolicies(), item.form(), item.row()));

            if (!validates(form, sources, errors -> groupProfiles.validate(form, errors))) {
                failedGroups.add(group.key);
                return;
            }
            current = group.row;
            Group saved = groupProfiles.save(null, form);
            groupIds.put(group.key, saved.getId());

            group.locations.forEach((key, item) -> {
                current = item.row();
                locationIds.put(key, groupLocations.addLocation(saved.getId(), item.form().toEntity()).getId());
            });
            group.policies.forEach((key, item) -> {
                current = item.row();
                // Carrier and policy number are unique within a user group, so they find it.
                policyRepository.findByCarrierNameAndPolicyNumber(item.form().getCarrierName(),
                                item.form().getPolicyNumber())
                        .ifPresent(policy -> groupPolicyIds.put(key, policy.getId()));
            });
        }

        private void saveProvider(ProviderPlan provider, OnboardingPlan plan) {
            ProviderProfileForm form = new ProviderProfileForm();
            form.setDetails(provider.details);
            Sources sources = new Sources(provider.row);

            for (Membership membership : provider.memberships) {
                if (failedGroups.contains(membership.groupKey())) {
                    continue;
                }
                ProviderGroupForm row = new ProviderGroupForm();
                row.setGroupId(groupIds.get(membership.groupKey()));
                row.setEffectiveDate(membership.effectiveDate());
                sources.add("groups", form.getGroups(), row, membership.row());
            }
            for (PracticeLocation location : provider.locations) {
                if (failedGroups.contains(plan.locationGroups.get(location.locationKey()).key)) {
                    continue;
                }
                ProviderLocationForm row = new ProviderLocationForm();
                row.setLocationId(locationIds.get(location.locationKey()));
                row.setPcpScp(location.pcpScp());
                sources.add("locations", form.getLocations(), row, location.row());
            }
            provider.taxonomies.forEach(item -> sources.add("taxonomies", form.getTaxonomies(), item.form(), item.row()));
            provider.licenses.forEach(item -> sources.add("licenses", form.getLicenses(), item.form(), item.row()));
            provider.certifications.forEach(item ->
                    sources.add("certifications", form.getCertifications(), item.form(), item.row()));
            provider.privileges.forEach(item -> sources.add("privileges", form.getPrivileges(), item.form(), item.row()));
            provider.policies.values().forEach(item -> sources.add("policies", form.getPolicies(), item.form(), item.row()));
            for (Claim claim : provider.claims) {
                claim.form().setPolicyKey(claimPolicyKey(claim.policyKey(), provider));
                sources.add("claims", form.getClaims(), claim.form(), claim.row());
            }
            provider.references.forEach(item -> sources.add("references", form.getReferences(), item.form(), item.row()));
            provider.charges.forEach(item -> sources.add("charges", form.getCharges(), item.form(), item.row()));

            if (!validates(form, sources, errors -> providerProfiles.validate(null, form, errors))) {
                return;
            }
            current = provider.row;
            providerIds.put(provider.key, providerProfiles.save(null, form).getId());
        }

        /**
         * The claim form links a policy by key: the provider's own policy by its key on the
         * same form, anything already saved (a group's policy) as "p" plus its id.
         */
        private String claimPolicyKey(String policyKey, ProviderPlan provider) {
            if (policyKey == null) {
                return null;
            }
            if (provider.policies.containsKey(policyKey)) {
                return policyKey;
            }
            Long groupPolicyId = groupPolicyIds.get(policyKey);
            return groupPolicyId == null ? null : ProviderProfileForm.PolicyRow.keyFor(groupPolicyId);
        }

        private boolean validates(Object form, Sources sources, java.util.function.Consumer<Errors> validation) {
            BeanPropertyBindingResult errors = new BeanPropertyBindingResult(form, "form");
            validation.accept(errors);
            for (FieldError error : errors.getFieldErrors()) {
                report(error.getField(), error.getDefaultMessage(), sources);
            }
            for (ObjectError error : errors.getGlobalErrors()) {
                problems.at(sources.details, null, error.getDefaultMessage());
            }
            return !errors.hasErrors();
        }

        private void report(String field, String message, Sources sources) {
            Matcher list = LIST_FIELD.matcher(field);
            if (list.matches()) {
                ParsedRow row = sources.row(list.group(1), Integer.parseInt(list.group(2)));
                problems.at(row, columnFor(row, list.group(3)), message);
            } else {
                String property = field.startsWith("details.") ? field.substring("details.".length()) : field;
                problems.at(sources.details, columnFor(sources.details, property), message);
            }
        }

        private String columnFor(ParsedRow row, String property) {
            if (row.sheet().column(property).isPresent()) {
                return property;
            }
            String link = LINK_COLUMNS.get(property);
            return link != null && row.sheet().column(link).isPresent() ? link : null;
        }
    }
}
