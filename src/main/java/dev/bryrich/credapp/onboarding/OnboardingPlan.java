package dev.bryrich.credapp.onboarding;

import dev.bryrich.credapp.group.GroupForm;
import dev.bryrich.credapp.group.GroupProfileForm;
import dev.bryrich.credapp.group.location.GroupLocationForm;
import dev.bryrich.credapp.owner.OwnerForm;
import dev.bryrich.credapp.provider.ProviderForm;
import dev.bryrich.credapp.provider.ProviderProfileForm;
import dev.bryrich.credapp.provider.location.PcpScp;
import dev.bryrich.credapp.taxonomy.GroupTaxonomyForm;
import dev.bryrich.credapp.taxonomy.ProviderTaxonomyForm;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything an upload will create, linked up by the IDs the sheets use but not yet saved.
 * Each item keeps the row it came from so a problem found while saving can point back at it.
 * Owners come first (groups reference them), then groups, then providers.
 */
public class OnboardingPlan {

    /** A form row and the sheet row it was read from. */
    public record Item<T>(ParsedRow row, T form) {
    }

    public static class OwnerPlan {
        final String key;
        final ParsedRow row;
        final OwnerForm form;

        OwnerPlan(String key, ParsedRow row, OwnerForm form) {
            this.key = key;
            this.row = row;
            this.form = form;
        }
    }

    public static class GroupPlan {
        final String key;
        final ParsedRow row;
        final GroupForm details;
        /** Keyed by location ID. */
        final Map<String, Item<GroupLocationForm>> locations = new LinkedHashMap<>();
        /** Ownership rows; the form row's key is the owner ID and ownerId is filled in on save. */
        final List<Item<GroupProfileForm.OwnerRow>> owners = new ArrayList<>();
        final List<Item<GroupProfileForm.RelationRow>> relations = new ArrayList<>();
        final List<Item<GroupTaxonomyForm>> taxonomies = new ArrayList<>();
        /** Keyed by policy ID, or a generated key when the row has none. */
        final Map<String, Item<GroupProfileForm.PolicyRow>> policies = new LinkedHashMap<>();

        GroupPlan(String key, ParsedRow row, GroupForm details) {
            this.key = key;
            this.row = row;
            this.details = details;
        }

        String label() {
            return details.getLbn() + " (" + key + ")";
        }
    }

    public record Membership(ParsedRow row, String groupKey, LocalDate effectiveDate) {
    }

    public record PracticeLocation(ParsedRow row, String locationKey, PcpScp pcpScp) {
    }

    /** A claim, with the policy ID it names, if any. */
    public record Claim(ParsedRow row, ProviderProfileForm.ClaimRow form, String policyKey) {
    }

    /** A privilege naming another provider in this file as admitting; saved once both exist. */
    public record LinkedPrivilege(ParsedRow row, ProviderProfileForm.PrivilegeRow form, String admittingKey) {
    }

    public static class ProviderPlan {
        final String key;
        final ParsedRow row;
        final ProviderForm details;
        final List<Membership> memberships = new ArrayList<>();
        final List<PracticeLocation> locations = new ArrayList<>();
        final List<Item<ProviderTaxonomyForm>> taxonomies = new ArrayList<>();
        final List<Item<ProviderProfileForm.LicenseRow>> licenses = new ArrayList<>();
        final List<Item<ProviderProfileForm.CertificationRow>> certifications = new ArrayList<>();
        final List<Item<ProviderProfileForm.PrivilegeRow>> privileges = new ArrayList<>();
        final List<LinkedPrivilege> linkedPrivileges = new ArrayList<>();
        /** Keyed by policy ID, or a generated key when the row has none. */
        final Map<String, Item<ProviderProfileForm.PolicyRow>> policies = new LinkedHashMap<>();
        final List<Claim> claims = new ArrayList<>();
        final List<Item<ProviderProfileForm.ReferenceRow>> references = new ArrayList<>();
        final List<Item<ProviderProfileForm.ChargeRow>> charges = new ArrayList<>();

        ProviderPlan(String key, ParsedRow row, ProviderForm details) {
            this.key = key;
            this.row = row;
            this.details = details;
        }

        String label() {
            return details.getLastName() + ", " + details.getFirstName() + " (" + key + ")";
        }
    }

    final Map<String, OwnerPlan> owners = new LinkedHashMap<>();
    final Map<String, GroupPlan> groups = new LinkedHashMap<>();
    final Map<String, ProviderPlan> providers = new LinkedHashMap<>();
    /** Location ID to the group it belongs to. */
    final Map<String, GroupPlan> locationGroups = new LinkedHashMap<>();
    /** Policy ID to the group holding it, for claims against a group's policy. */
    final Map<String, GroupPlan> groupPolicyOwners = new LinkedHashMap<>();
    /** Policy ID to the provider holding it. */
    final Map<String, ProviderPlan> providerPolicyOwners = new LinkedHashMap<>();

    public List<String> groupLabels() {
        return groups.values().stream().map(GroupPlan::label).toList();
    }

    public List<String> providerLabels() {
        return providers.values().stream().map(ProviderPlan::label).toList();
    }
}
