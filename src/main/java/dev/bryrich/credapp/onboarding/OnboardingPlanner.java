package dev.bryrich.credapp.onboarding;

import dev.bryrich.credapp.group.GroupForm;
import dev.bryrich.credapp.group.GroupProfileForm;
import dev.bryrich.credapp.group.GroupRepository;
import dev.bryrich.credapp.group.location.GroupLocationForm;
import dev.bryrich.credapp.malpractice.MalpracticeClaimRepository;
import dev.bryrich.credapp.malpractice.MalpracticePolicyRepository;
import dev.bryrich.credapp.onboarding.OnboardingPlan.Claim;
import dev.bryrich.credapp.onboarding.OnboardingPlan.ContactPlan;
import dev.bryrich.credapp.onboarding.OnboardingPlan.GroupEnrollment;
import dev.bryrich.credapp.onboarding.OnboardingPlan.PayerPlan;
import dev.bryrich.credapp.onboarding.OnboardingPlan.ProviderEnrollment;
import dev.bryrich.credapp.onboarding.OnboardingPlan.GroupPlan;
import dev.bryrich.credapp.onboarding.OnboardingPlan.Item;
import dev.bryrich.credapp.onboarding.OnboardingPlan.LinkedPrivilege;
import dev.bryrich.credapp.onboarding.OnboardingPlan.Membership;
import dev.bryrich.credapp.onboarding.OnboardingPlan.OwnerPlan;
import dev.bryrich.credapp.onboarding.OnboardingPlan.PracticeLocation;
import dev.bryrich.credapp.onboarding.OnboardingPlan.ProviderPlan;
import dev.bryrich.credapp.onboarding.OnboardingTemplate.Sheet;
import dev.bryrich.credapp.owner.OwnerForm;
import dev.bryrich.credapp.owner.OwnerRepository;
import dev.bryrich.credapp.payer.Payer;
import dev.bryrich.credapp.payer.PayerContactForm;
import dev.bryrich.credapp.payer.PayerForm;
import dev.bryrich.credapp.payer.PayerRepository;
import dev.bryrich.credapp.payer.enrollment.GroupPayerForm;
import dev.bryrich.credapp.payer.enrollment.ProviderPayerForm;
import dev.bryrich.credapp.provider.ProviderForm;
import dev.bryrich.credapp.provider.ProviderProfileForm;
import dev.bryrich.credapp.provider.ProviderRepository;
import dev.bryrich.credapp.provider.location.PcpScp;
import dev.bryrich.credapp.taxonomy.GroupTaxonomyForm;
import dev.bryrich.credapp.taxonomy.ProviderTaxonomyForm;
import dev.bryrich.credapp.taxonomy.TaxonomyRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.BeansException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import static dev.bryrich.credapp.onboarding.OnboardingTemplate.ACCOUNT_REP;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.ADMITTING_PROVIDER;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.GROUP;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.ID;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.LOCATION;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.OWNER;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.PAYER;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.POLICY;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.PROVIDER;
import static dev.bryrich.credapp.onboarding.OnboardingTemplate.RELATED_OWNER;

/**
 * Turns parsed rows into an OnboardingPlan. Each row is bound onto the same form class the
 * web page uses and checked with that form's rules, so a sheet row and a form row pass or
 * fail alike. On top of that it resolves the IDs that link sheets together and looks for
 * records that are already in CredCloud, since onboarding only ever creates.
 *
 * Nothing here writes. Rules that depend on saved rows (ownership totals, one primary
 * specialty and so on) run later, through the profile services, when the import saves.
 */
@Component
public class OnboardingPlanner {

    private final Validator validator;
    private final GroupRepository groupRepository;
    private final ProviderRepository providerRepository;
    private final OwnerRepository ownerRepository;
    private final MalpracticePolicyRepository policyRepository;
    private final MalpracticeClaimRepository claimRepository;
    private final TaxonomyRepository taxonomyRepository;
    private final PayerRepository payerRepository;

    public OnboardingPlanner(Validator validator,
                             GroupRepository groupRepository,
                             ProviderRepository providerRepository,
                             OwnerRepository ownerRepository,
                             MalpracticePolicyRepository policyRepository,
                             MalpracticeClaimRepository claimRepository,
                             TaxonomyRepository taxonomyRepository,
                             PayerRepository payerRepository) {
        this.validator = validator;
        this.groupRepository = groupRepository;
        this.providerRepository = providerRepository;
        this.ownerRepository = ownerRepository;
        this.policyRepository = policyRepository;
        this.claimRepository = claimRepository;
        this.taxonomyRepository = taxonomyRepository;
        this.payerRepository = payerRepository;
    }

    /** Stands in for a payer id on enrollment rows until the payer is saved. */
    static final Long PENDING_ID = -1L;

    public OnboardingPlan plan(Map<Sheet, List<ParsedRow>> rows, Problems problems) {
        Run run = new Run(new OnboardingPlan(), problems);
        run.groups(rows.get(OnboardingTemplate.GROUPS));
        run.groupLocations(rows.get(OnboardingTemplate.GROUP_LOCATIONS));
        run.owners(rows.get(OnboardingTemplate.OWNERS));
        run.ownership(rows.get(OnboardingTemplate.OWNERSHIP));
        run.relationships(rows.get(OnboardingTemplate.OWNER_RELATIONSHIPS));
        run.groupSpecialties(rows.get(OnboardingTemplate.GROUP_SPECIALTIES));
        run.providers(rows.get(OnboardingTemplate.PROVIDERS));
        run.memberships(rows.get(OnboardingTemplate.GROUP_MEMBERS));
        run.practiceLocations(rows.get(OnboardingTemplate.PRACTICE_LOCATIONS));
        run.providerSpecialties(rows.get(OnboardingTemplate.PROVIDER_SPECIALTIES));
        run.providerRows(rows.get(OnboardingTemplate.LICENSES), ProviderProfileForm.LicenseRow::new,
                (provider, item) -> provider.licenses.add(item));
        run.providerRows(rows.get(OnboardingTemplate.CERTIFICATIONS), ProviderProfileForm.CertificationRow::new,
                (provider, item) -> provider.certifications.add(item));
        run.privileges(rows.get(OnboardingTemplate.HOSPITAL_PRIVILEGES));
        run.policies(rows.get(OnboardingTemplate.POLICIES));
        run.claims(rows.get(OnboardingTemplate.CLAIMS));
        run.providerRows(rows.get(OnboardingTemplate.REFERENCES), ProviderProfileForm.ReferenceRow::new,
                (provider, item) -> provider.references.add(item));
        run.providerRows(rows.get(OnboardingTemplate.DISCLOSURES), ProviderProfileForm.ChargeRow::new,
                (provider, item) -> provider.charges.add(item));
        run.payers(rows.get(OnboardingTemplate.PAYERS));
        run.payerContacts(rows.get(OnboardingTemplate.PAYER_CONTACTS));
        run.groupPayers(rows.get(OnboardingTemplate.GROUP_PAYERS));
        run.providerPayers(rows.get(OnboardingTemplate.PROVIDER_PAYERS));
        run.licenseDuplicates();
        return run.plan;
    }

    /** One planning pass; holds the plan being built and what's been seen so far. */
    private final class Run {
        private final OnboardingPlan plan;
        private final Problems problems;
        private final Map<String, Boolean> taxonomyCodes = new HashMap<>();

        Run(OnboardingPlan plan, Problems problems) {
            this.plan = plan;
            this.problems = problems;
        }

        // ============ groups ============

        void groups(List<ParsedRow> rows) {
            Map<String, ParsedRow> npis = new HashMap<>();
            Map<String, ParsedRow> names = new HashMap<>();
            Map<String, ParsedRow> ids = new HashMap<>();
            for (ParsedRow row : rows) {
                String key = ownId(row, ids);
                GroupForm form = bindAndCheck(row, new GroupForm());
                if (form.getNpi() != null) {
                    ParsedRow earlier = npis.putIfAbsent(form.getNpi(), row);
                    if (earlier != null) {
                        problems.at(row, "npi", "Same NPI as row " + earlier.number());
                    } else if (groupRepository.existsByNpi(form.getNpi())) {
                        problems.at(row, "npi", "A group with NPI " + form.getNpi() + " is already in CredCloud");
                    }
                }
                if (form.getTaxId() != null && form.getLbn() != null) {
                    String name = form.getTaxId() + "|" + form.getLbn().trim().toLowerCase(Locale.ROOT);
                    ParsedRow earlier = names.putIfAbsent(name, row);
                    if (earlier != null) {
                        problems.at(row, "lbn", "Same group as row " + earlier.number() + " (same name and tax ID)");
                    } else if (groupRepository.findByTaxId(form.getTaxId()).stream()
                            .anyMatch(group -> group.getLbn().equalsIgnoreCase(form.getLbn().trim()))) {
                        problems.at(row, "lbn", form.getLbn() + " with this tax ID is already in CredCloud");
                    }
                }
                if (key != null) {
                    plan.groups.put(key, new GroupPlan(key, row, form));
                }
            }
        }

        void groupLocations(List<ParsedRow> rows) {
            Map<String, ParsedRow> ids = new HashMap<>();
            for (ParsedRow row : rows) {
                String key = ownId(row, ids);
                GroupPlan group = link(row, GROUP, plan.groups, OnboardingTemplate.GROUPS);
                GroupLocationForm form = bindAndCheck(row, new GroupLocationForm());
                if (key != null && group != null) {
                    group.locations.put(key, new Item<>(row, form));
                    plan.locationGroups.put(key, group);
                }
            }
        }

        void owners(List<ParsedRow> rows) {
            Map<String, ParsedRow> ids = new HashMap<>();
            Map<String, ParsedRow> people = new HashMap<>();
            for (ParsedRow row : rows) {
                String key = ownId(row, ids);
                OwnerForm form = bindAndCheck(row, new OwnerForm());
                if (form.getFirstName() != null && form.getLastName() != null && form.getDob() != null) {
                    String person = (form.getLastName() + "|" + form.getFirstName()).toLowerCase(Locale.ROOT)
                            + "|" + form.getDob();
                    ParsedRow earlier = people.putIfAbsent(person, row);
                    if (earlier != null) {
                        problems.at(row, null, "Same person as row " + earlier.number()
                                + ". List each owner once and give them a row per group on Ownership.");
                    } else if (ownerRepository.findByLastNameIgnoreCaseAndFirstNameIgnoreCase(
                                    form.getLastName().trim(), form.getFirstName().trim()).stream()
                            .anyMatch(owner -> form.getDob().equals(owner.getDob()))) {
                        problems.at(row, null, form.getFirstName() + " " + form.getLastName()
                                + ", born " + form.getDob() + ", is already in CredCloud. Take them off this file "
                                + "and add their stake in CredCloud after the import.");
                    }
                }
                if (key != null) {
                    plan.owners.put(key, new OwnerPlan(key, row, form));
                }
            }
        }

        void ownership(List<ParsedRow> rows) {
            Map<String, ParsedRow> pairs = new HashMap<>();
            for (ParsedRow row : rows) {
                GroupPlan group = link(row, GROUP, plan.groups, OnboardingTemplate.GROUPS);
                OwnerPlan owner = link(row, OWNER, plan.owners, OnboardingTemplate.OWNERS);
                GroupProfileForm.OwnerRow form = bindAndCheck(row, new GroupProfileForm.OwnerRow());
                if (group == null || owner == null) {
                    continue;
                }
                ParsedRow earlier = pairs.putIfAbsent(group.key + "|" + owner.key, row);
                if (earlier != null) {
                    problems.at(row, OWNER, owner.key + " is already listed for " + group.key
                            + " on row " + earlier.number());
                    continue;
                }
                form.setMode(GroupProfileForm.OwnerRow.EXISTING);
                form.setKey(owner.key);
                group.owners.add(new Item<>(row, form));
            }
        }

        void relationships(List<ParsedRow> rows) {
            for (ParsedRow row : rows) {
                GroupPlan group = link(row, GROUP, plan.groups, OnboardingTemplate.GROUPS);
                OwnerPlan owner = link(row, OWNER, plan.owners, OnboardingTemplate.OWNERS);
                OwnerPlan related = link(row, RELATED_OWNER, plan.owners, OnboardingTemplate.OWNERS);
                GroupProfileForm.RelationRow form = bind(row, new GroupProfileForm.RelationRow());
                if (group == null || owner == null || related == null) {
                    continue;
                }
                boolean ok = ownsPartOf(row, OWNER, owner, group) & ownsPartOf(row, RELATED_OWNER, related, group);
                form.setOwnerKey(owner.key);
                form.setRelatedOwnerKey(related.key);
                check(row, form);
                if (ok) {
                    group.relations.add(new Item<>(row, form));
                }
            }
        }

        private boolean ownsPartOf(ParsedRow row, String column, OwnerPlan owner, GroupPlan group) {
            boolean owns = group.owners.stream().anyMatch(item -> owner.key.equals(item.form().getKey()));
            if (!owns) {
                problems.at(row, column, owner.key + " has no row for " + group.key + " on the Ownership sheet");
            }
            return owns;
        }

        void groupSpecialties(List<ParsedRow> rows) {
            for (ParsedRow row : rows) {
                GroupPlan group = link(row, GROUP, plan.groups, OnboardingTemplate.GROUPS);
                GroupTaxonomyForm form = bindAndCheck(row, new GroupTaxonomyForm());
                if (knownTaxonomy(row, form.getCode()) && group != null) {
                    group.taxonomies.add(new Item<>(row, form));
                }
            }
        }

        // ============ providers ============

        void providers(List<ParsedRow> rows) {
            Map<String, ParsedRow> ids = new HashMap<>();
            Map<String, ParsedRow> npis = new HashMap<>();
            for (ParsedRow row : rows) {
                String key = ownId(row, ids);
                ProviderForm form = bindAndCheck(row, new ProviderForm());
                if (form.getNpi() != null) {
                    ParsedRow earlier = npis.putIfAbsent(form.getNpi(), row);
                    if (earlier != null) {
                        problems.at(row, "npi", "Same NPI as row " + earlier.number());
                    } else if (providerRepository.findByNpi(form.getNpi()).isPresent()) {
                        problems.at(row, "npi", "A provider with NPI " + form.getNpi() + " is already in CredCloud");
                    }
                }
                if (key != null) {
                    plan.providers.put(key, new ProviderPlan(key, row, form));
                }
            }
        }

        void memberships(List<ParsedRow> rows) {
            Map<String, ParsedRow> pairs = new HashMap<>();
            for (ParsedRow row : rows) {
                ProviderPlan provider = link(row, PROVIDER, plan.providers, OnboardingTemplate.PROVIDERS);
                GroupPlan group = link(row, GROUP, plan.groups, OnboardingTemplate.GROUPS);
                if (provider == null || group == null) {
                    continue;
                }
                ParsedRow earlier = pairs.putIfAbsent(provider.key + "|" + group.key, row);
                if (earlier != null) {
                    problems.at(row, GROUP, provider.key + " is already in " + group.key + " on row " + earlier.number());
                    continue;
                }
                provider.memberships.add(new Membership(row, group.key, (LocalDate) row.get("effectiveDate")));
            }
        }

        void practiceLocations(List<ParsedRow> rows) {
            Map<String, ParsedRow> pairs = new HashMap<>();
            for (ParsedRow row : rows) {
                ProviderPlan provider = link(row, PROVIDER, plan.providers, OnboardingTemplate.PROVIDERS);
                GroupPlan group = link(row, LOCATION, plan.locationGroups, OnboardingTemplate.GROUP_LOCATIONS);
                if (provider == null || group == null) {
                    continue;
                }
                String location = row.key(LOCATION);
                if (!isMember(provider, group)) {
                    problems.at(row, LOCATION, location + " belongs to " + group.key + ", and " + provider.key
                            + " isn't in " + group.key + " on the Group Members sheet");
                    continue;
                }
                ParsedRow earlier = pairs.putIfAbsent(provider.key + "|" + location, row);
                if (earlier != null) {
                    problems.at(row, LOCATION, provider.key + " is already at " + location + " on row " + earlier.number());
                    continue;
                }
                provider.locations.add(new PracticeLocation(row, location, (PcpScp) row.get("pcpScp")));
            }
        }

        void providerSpecialties(List<ParsedRow> rows) {
            for (ParsedRow row : rows) {
                ProviderPlan provider = link(row, PROVIDER, plan.providers, OnboardingTemplate.PROVIDERS);
                ProviderTaxonomyForm form = bindAndCheck(row, new ProviderTaxonomyForm());
                if (knownTaxonomy(row, form.getCode()) && provider != null) {
                    provider.taxonomies.add(new Item<>(row, form));
                }
            }
        }

        /** Rows that only need their provider: licenses, certifications, references, disclosures. */
        <T> void providerRows(List<ParsedRow> rows, Supplier<T> blank,
                              java.util.function.BiConsumer<ProviderPlan, Item<T>> add) {
            for (ParsedRow row : rows) {
                ProviderPlan provider = link(row, PROVIDER, plan.providers, OnboardingTemplate.PROVIDERS);
                T form = bindAndCheck(row, blank.get());
                if (provider != null) {
                    add.accept(provider, new Item<>(row, form));
                }
            }
        }

        /** The database allows one license per state and number for a provider. */
        void licenseDuplicates() {
            for (ProviderPlan provider : plan.providers.values()) {
                Map<String, ParsedRow> seen = new HashMap<>();
                for (Item<ProviderProfileForm.LicenseRow> item : provider.licenses) {
                    ProviderProfileForm.LicenseRow license = item.form();
                    if (license.getState() == null || license.getLicenseNumber() == null) {
                        continue;
                    }
                    ParsedRow earlier = seen.putIfAbsent(license.getState() + "|" + license.getLicenseNumber(), item.row());
                    if (earlier != null) {
                        problems.at(item.row(), "licenseNumber", "Same state and license number as row "
                                + earlier.number() + " for " + provider.key);
                    }
                }
            }
        }

        void privileges(List<ParsedRow> rows) {
            for (ParsedRow row : rows) {
                ProviderPlan provider = link(row, PROVIDER, plan.providers, OnboardingTemplate.PROVIDERS);
                ProviderProfileForm.PrivilegeRow form = bindAndCheck(row, new ProviderProfileForm.PrivilegeRow());
                if (!row.has(ADMITTING_PROVIDER)) {
                    if (provider != null) {
                        provider.privileges.add(new Item<>(row, form));
                    }
                    continue;
                }
                ProviderPlan admitting = link(row, ADMITTING_PROVIDER, plan.providers, OnboardingTemplate.PROVIDERS);
                if (provider == null || admitting == null) {
                    continue;
                }
                if (admitting == provider) {
                    problems.at(row, ADMITTING_PROVIDER, "A provider can't admit for themselves");
                    continue;
                }
                provider.linkedPrivileges.add(new LinkedPrivilege(row, form, admitting.key));
            }
        }

        // ============ malpractice ============

        void policies(List<ParsedRow> rows) {
            Map<String, ParsedRow> ids = new HashMap<>();
            Map<String, ParsedRow> numbers = new HashMap<>();
            for (ParsedRow row : rows) {
                String key = row.has(ID) ? ownId(row, ids) : null;
                boolean forProvider = row.has(PROVIDER);
                boolean forGroup = row.has(GROUP);
                if (forProvider == forGroup) {
                    problems.at(row, null, forProvider
                            ? "Fill in Provider ID or Group ID, not both"
                            : "Fill in the Provider ID or the Group ID this policy belongs to");
                }
                ProviderPlan provider = forProvider && !forGroup
                        ? link(row, PROVIDER, plan.providers, OnboardingTemplate.PROVIDERS) : null;
                GroupPlan group = forGroup && !forProvider
                        ? link(row, GROUP, plan.groups, OnboardingTemplate.GROUPS) : null;

                String carrier = (String) row.get("carrierName");
                String number = (String) row.get("policyNumber");
                if (carrier != null && number != null) {
                    ParsedRow earlier = numbers.putIfAbsent(carrier.toLowerCase(Locale.ROOT) + "|" + number, row);
                    if (earlier != null) {
                        problems.at(row, "policyNumber", "Same carrier and policy number as row " + earlier.number());
                    } else if (policyRepository.findByCarrierNameAndPolicyNumber(carrier, number).isPresent()) {
                        problems.at(row, "policyNumber", carrier + " policy " + number + " is already in CredCloud");
                    }
                }

                String planKey = key != null ? key : "#row" + row.number();
                if (group != null) {
                    GroupProfileForm.PolicyRow form = bindAndCheck(row, new GroupProfileForm.PolicyRow());
                    group.policies.put(planKey, new Item<>(row, form));
                    if (key != null) {
                        plan.groupPolicyOwners.put(key, group);
                    }
                } else if (provider != null) {
                    ProviderProfileForm.PolicyRow form = bindAndCheck(row, new ProviderProfileForm.PolicyRow());
                    form.setKey(planKey);
                    provider.policies.put(planKey, new Item<>(row, form));
                    if (key != null) {
                        plan.providerPolicyOwners.put(key, provider);
                    }
                } else {
                    bindAndCheck(row, new GroupProfileForm.PolicyRow());
                }
            }
        }

        void claims(List<ParsedRow> rows) {
            Map<String, ParsedRow> numbers = new HashMap<>();
            for (ParsedRow row : rows) {
                ProviderPlan provider = link(row, PROVIDER, plan.providers, OnboardingTemplate.PROVIDERS);
                ProviderProfileForm.ClaimRow form = bindAndCheck(row, new ProviderProfileForm.ClaimRow());

                String carrier = form.getCarrierName();
                String number = form.getClaimNumber();
                if (carrier != null && number != null) {
                    ParsedRow earlier = numbers.putIfAbsent(carrier.toLowerCase(Locale.ROOT) + "|" + number, row);
                    if (earlier != null) {
                        problems.at(row, "claimNumber", "Same carrier and claim number as row " + earlier.number());
                    } else if (claimRepository.findByCarrierNameAndClaimNumber(carrier, number).isPresent()) {
                        problems.at(row, "claimNumber", carrier + " claim " + number + " is already in CredCloud");
                    }
                }
                if (provider == null) {
                    continue;
                }
                String policy = row.key(POLICY);
                if (policy != null && !policyUsable(row, policy, provider)) {
                    continue;
                }
                provider.claims.add(new Claim(row, form, policy));
            }
        }

        /** A claim can sit under the provider's own policy or a policy of one of their groups. */
        private boolean policyUsable(ParsedRow row, String policy, ProviderPlan provider) {
            ProviderPlan holder = plan.providerPolicyOwners.get(policy);
            if (holder == provider) {
                return true;
            }
            if (holder != null) {
                problems.at(row, POLICY, policy + " is " + holder.key + "'s policy, not " + provider.key + "'s");
                return false;
            }
            GroupPlan group = plan.groupPolicyOwners.get(policy);
            if (group == null) {
                problems.at(row, POLICY, "\"" + policy + "\" isn't a Policy ID on the Malpractice Policies sheet");
                return false;
            }
            if (!isMember(provider, group)) {
                problems.at(row, POLICY, policy + " belongs to " + group.key + ", and " + provider.key
                        + " isn't in " + group.key + " on the Group Members sheet");
                return false;
            }
            return true;
        }

        // ============ payers ============

        void payers(List<ParsedRow> rows) {
            Map<String, ParsedRow> ids = new HashMap<>();
            Map<String, ParsedRow> names = new HashMap<>();
            for (ParsedRow row : rows) {
                String key = ownId(row, ids);
                PayerForm form = bindAndCheck(row, new PayerForm());
                Long existingId = null;
                if (form.getName() != null) {
                    String name = form.getName().trim();
                    ParsedRow earlier = names.putIfAbsent(name.toLowerCase(Locale.ROOT), row);
                    if (earlier != null) {
                        problems.at(row, "name", "Same payer as row " + earlier.number()
                                + ". List each payer once; other sheets point at its Payer ID.");
                        continue;
                    }
                    // Payers are shared reference data: one already on file is used, not duplicated.
                    existingId = payerRepository.findByNameIgnoreCase(name).map(Payer::getId).orElse(null);
                }
                if (key != null) {
                    plan.payers.put(key, new PayerPlan(key, row, form, existingId));
                }
            }
        }

        void payerContacts(List<ParsedRow> rows) {
            Map<String, ParsedRow> ids = new HashMap<>();
            for (ParsedRow row : rows) {
                String key = row.has(ID) ? ownId(row, ids) : null;
                PayerPlan payer = link(row, PAYER, plan.payers, OnboardingTemplate.PAYERS);
                GroupPlan group = row.has(GROUP) ? link(row, GROUP, plan.groups, OnboardingTemplate.GROUPS) : null;
                ProviderPlan provider = row.has(PROVIDER)
                        ? link(row, PROVIDER, plan.providers, OnboardingTemplate.PROVIDERS) : null;
                if (row.has(GROUP) && row.has(PROVIDER)) {
                    problems.at(row, PROVIDER, "A contact can be tied to a group or a provider, not both");
                    continue;
                }
                PayerContactForm form = bindAndCheck(row, new PayerContactForm());
                if (payer == null || (row.has(GROUP) && group == null) || (row.has(PROVIDER) && provider == null)) {
                    continue;
                }
                ContactPlan contact = new ContactPlan(key, row, form, payer.key,
                        group == null ? null : group.key, provider == null ? null : provider.key);
                plan.contacts.add(contact);
                if (key != null) {
                    plan.contactsByKey.put(key, contact);
                }
            }
        }

        void groupPayers(List<ParsedRow> rows) {
            Map<String, ParsedRow> pairs = new HashMap<>();
            for (ParsedRow row : rows) {
                GroupPlan group = link(row, GROUP, plan.groups, OnboardingTemplate.GROUPS);
                PayerPlan payer = link(row, PAYER, plan.payers, OnboardingTemplate.PAYERS);
                ContactPlan rep = row.has(ACCOUNT_REP)
                        ? link(row, ACCOUNT_REP, plan.contactsByKey, OnboardingTemplate.PAYER_CONTACTS) : null;
                GroupPayerForm form = bind(row, new GroupPayerForm());
                // The real payer id is known only once payers are saved; this stands in for the check.
                form.setPayerId(PENDING_ID);
                check(row, form);
                if (group == null || payer == null || (row.has(ACCOUNT_REP) && rep == null)) {
                    continue;
                }
                ParsedRow earlier = pairs.putIfAbsent(group.key + "|" + payer.key, row);
                if (earlier != null) {
                    problems.at(row, PAYER, group.key + " is already enrolled with " + payer.key
                            + " on row " + earlier.number());
                    continue;
                }
                if (rep != null && !repFits(row, rep, payer, group)) {
                    continue;
                }
                group.payers.add(new GroupEnrollment(row, form, payer.key, rep == null ? null : rep.key()));
            }
        }

        /** A group's rep is a contact of that payer, and either payer-wide or tied to that group. */
        private boolean repFits(ParsedRow row, ContactPlan rep, PayerPlan payer, GroupPlan group) {
            if (!rep.payerKey().equals(payer.key)) {
                problems.at(row, ACCOUNT_REP, rep.key() + " is a contact of " + rep.payerKey() + ", not " + payer.key);
                return false;
            }
            if (rep.providerKey() != null || (rep.groupKey() != null && !rep.groupKey().equals(group.key))) {
                problems.at(row, ACCOUNT_REP, rep.key() + " is tied to "
                        + (rep.providerKey() != null ? rep.providerKey() : rep.groupKey())
                        + "; a rep has to be payer-wide or tied to " + group.key);
                return false;
            }
            return true;
        }

        void providerPayers(List<ParsedRow> rows) {
            Map<String, ParsedRow> pairs = new HashMap<>();
            for (ParsedRow row : rows) {
                ProviderPlan provider = link(row, PROVIDER, plan.providers, OnboardingTemplate.PROVIDERS);
                PayerPlan payer = link(row, PAYER, plan.payers, OnboardingTemplate.PAYERS);
                ProviderPayerForm form = bind(row, new ProviderPayerForm());
                form.setPayerId(PENDING_ID);
                check(row, form);
                if (provider == null || payer == null) {
                    continue;
                }
                ParsedRow earlier = pairs.putIfAbsent(provider.key + "|" + payer.key, row);
                if (earlier != null) {
                    problems.at(row, PAYER, provider.key + " is already enrolled with " + payer.key
                            + " on row " + earlier.number());
                    continue;
                }
                provider.payers.add(new ProviderEnrollment(row, form, payer.key));
            }
        }

        // ============ helpers ============

        private boolean isMember(ProviderPlan provider, GroupPlan group) {
            return provider.memberships.stream().anyMatch(membership -> group.key.equals(membership.groupKey()));
        }

        /** The row's own ID, or null if it's missing or already used on this sheet. */
        private String ownId(ParsedRow row, Map<String, ParsedRow> seen) {
            String key = row.key(ID);
            if (key == null) {
                return null;
            }
            ParsedRow earlier = seen.putIfAbsent(key, row);
            if (earlier != null) {
                problems.at(row, ID, key + " is already used on row " + earlier.number());
                return null;
            }
            return key;
        }

        /** The record a link column points at, or null (with a problem) if it isn't there. */
        private <T> T link(ParsedRow row, String column, Map<String, T> targets, Sheet targetSheet) {
            String key = row.key(column);
            if (key == null) {
                return null;
            }
            T target = targets.get(key);
            if (target == null) {
                problems.at(row, column, "\"" + key + "\" isn't a " + targetIdHeader(targetSheet)
                        + " on the " + targetSheet.name() + " sheet");
            }
            return target;
        }

        private String targetIdHeader(Sheet sheet) {
            return sheet.column(ID).map(OnboardingTemplate.Column::header).orElse("ID");
        }

        private boolean knownTaxonomy(ParsedRow row, String code) {
            if (code == null) {
                return false;
            }
            boolean known = taxonomyCodes.computeIfAbsent(code, taxonomyRepository::existsById);
            if (!known) {
                problems.at(row, "code", code + " isn't a taxonomy code CredCloud knows");
            }
            return known;
        }

        private <T> T bindAndCheck(ParsedRow row, T target) {
            bind(row, target);
            check(row, target);
            return target;
        }

        /** Copies each filled-in cell onto the form property of the same name. */
        private <T> T bind(ParsedRow row, T target) {
            BeanWrapperImpl wrapper = new BeanWrapperImpl(target);
            for (OnboardingTemplate.Column column : row.sheet().columns()) {
                if (!column.binds() || !row.has(column.key())) {
                    continue;
                }
                try {
                    wrapper.setPropertyValue(column.key(), row.get(column.key()));
                } catch (BeansException ex) {
                    problems.at(row, column.key(), "This value can't be used here");
                }
            }
            return target;
        }

        /** The form's own field rules, reported against the matching column where there is one. */
        private void check(ParsedRow row, Object form) {
            for (ConstraintViolation<Object> violation : validator.validate(form)) {
                String path = violation.getPropertyPath().toString();
                String column = row.sheet().column(path).isPresent() ? path : null;
                problems.at(row, column, violation.getMessage());
            }
        }
    }
}
