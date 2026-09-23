package dev.bryrich.credapp.provider;

import dev.bryrich.credapp.group.membership.ProviderGroupForm;
import dev.bryrich.credapp.license.License;
import dev.bryrich.credapp.license.LicenseForm;
import dev.bryrich.credapp.malpractice.MalpracticeClaim;
import dev.bryrich.credapp.malpractice.MalpracticeClaimForm;
import dev.bryrich.credapp.malpractice.MalpracticePolicy;
import dev.bryrich.credapp.malpractice.MalpracticePolicyForm;
import dev.bryrich.credapp.provider.certification.Certification;
import dev.bryrich.credapp.provider.certification.CertificationForm;
import dev.bryrich.credapp.provider.disclosure.CriminalCharge;
import dev.bryrich.credapp.provider.disclosure.CriminalChargeForm;
import dev.bryrich.credapp.provider.location.ProviderLocationForm;
import dev.bryrich.credapp.provider.privilege.HospitalPrivilege;
import dev.bryrich.credapp.provider.privilege.HospitalPrivilegeForm;
import dev.bryrich.credapp.provider.reference.ProviderReference;
import dev.bryrich.credapp.provider.reference.ProviderReferenceForm;
import dev.bryrich.credapp.taxonomy.ProviderTaxonomyForm;
import jakarta.validation.Valid;
import org.springframework.beans.BeanUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Everything about a provider on one screen: their own fields plus every list attached to
 * them. The same form serves create and edit, and ProviderProfileService saves all of it
 * in one transaction.
 *
 * Each list row reuses the single-item form it replaced, so the field rules stay in one
 * place. Rows that already exist carry their id; a row without one is new. On edit, a saved
 * item whose row was removed from the page is deleted.
 */
public class ProviderProfileForm {

    @Valid
    private ProviderForm details = new ProviderForm();

    @Valid
    private List<ProviderGroupForm> groups = new ArrayList<>();

    @Valid
    private List<ProviderLocationForm> locations = new ArrayList<>();

    @Valid
    private List<ProviderTaxonomyForm> taxonomies = new ArrayList<>();

    @Valid
    private List<LicenseRow> licenses = new ArrayList<>();

    @Valid
    private List<CertificationRow> certifications = new ArrayList<>();

    @Valid
    private List<PrivilegeRow> privileges = new ArrayList<>();

    @Valid
    private List<PolicyRow> policies = new ArrayList<>();

    @Valid
    private List<ClaimRow> claims = new ArrayList<>();

    @Valid
    private List<ReferenceRow> references = new ArrayList<>();

    @Valid
    private List<ChargeRow> charges = new ArrayList<>();

    public ProviderProfileForm() {
    }

    /**
     * Binding grows each list to the highest index it sees, so a gap in the submitted
     * indexes leaves a null behind. The page renumbers rows before submitting; this is the
     * backstop in case it didn't.
     */
    public void compact() {
        groups.removeIf(Objects::isNull);
        locations.removeIf(Objects::isNull);
        taxonomies.removeIf(Objects::isNull);
        licenses.removeIf(Objects::isNull);
        certifications.removeIf(Objects::isNull);
        privileges.removeIf(Objects::isNull);
        policies.removeIf(Objects::isNull);
        claims.removeIf(Objects::isNull);
        references.removeIf(Objects::isNull);
        charges.removeIf(Objects::isNull);
    }

    // ============ rows ============

    public static class LicenseRow extends LicenseForm {
        private Long id;

        public static LicenseRow from(License license) {
            LicenseRow row = new LicenseRow();
            BeanUtils.copyProperties(LicenseForm.from(license), row);
            row.id = license.getId();
            return row;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }
    }

    public static class CertificationRow extends CertificationForm {
        private Long id;

        public static CertificationRow from(Certification certification) {
            CertificationRow row = new CertificationRow();
            BeanUtils.copyProperties(CertificationForm.from(certification), row);
            row.id = certification.getId();
            return row;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }
    }

    public static class PrivilegeRow extends HospitalPrivilegeForm {
        private Long id;

        public static PrivilegeRow from(HospitalPrivilege privilege) {
            PrivilegeRow row = new PrivilegeRow();
            BeanUtils.copyProperties(HospitalPrivilegeForm.from(privilege), row);
            row.id = privilege.getId();
            return row;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }
    }

    /**
     * A policy on this provider. The key lets a claim on the same page point at it before
     * either is saved: "p" plus the id for a saved policy, anything else for a new one.
     */
    public static class PolicyRow extends MalpracticePolicyForm {
        private Long id;
        private String key;

        public static PolicyRow from(MalpracticePolicy policy) {
            PolicyRow row = new PolicyRow();
            BeanUtils.copyProperties(MalpracticePolicyForm.from(policy), row);
            row.id = policy.getId();
            row.key = keyFor(policy);
            return row;
        }

        public static String keyFor(MalpracticePolicy policy) {
            return keyFor(policy.getId());
        }

        public static String keyFor(Long policyId) {
            return "p" + policyId;
        }

        /** The owner is the provider this page is for, so neither id is filled in here. */
        @Override
        public boolean isOwnerExclusive() {
            return true;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }
    }

    /** A claim. policyKey matches a PolicyRow key, or "p" plus the id of a policy on file. */
    public static class ClaimRow extends MalpracticeClaimForm {
        private Long id;
        private String policyKey;

        public static ClaimRow from(MalpracticeClaim claim) {
            ClaimRow row = new ClaimRow();
            BeanUtils.copyProperties(MalpracticeClaimForm.from(claim), row);
            row.id = claim.getId();
            row.policyKey = claim.getPolicy() == null ? null : PolicyRow.keyFor(claim.getPolicy());
            return row;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getPolicyKey() {
            return policyKey;
        }

        public void setPolicyKey(String policyKey) {
            this.policyKey = policyKey;
        }
    }

    public static class ReferenceRow extends ProviderReferenceForm {
        private Long id;

        public static ReferenceRow from(ProviderReference reference) {
            ReferenceRow row = new ReferenceRow();
            BeanUtils.copyProperties(ProviderReferenceForm.from(reference), row);
            row.id = reference.getId();
            return row;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }
    }

    public static class ChargeRow extends CriminalChargeForm {
        private Long id;

        public static ChargeRow from(CriminalCharge charge) {
            ChargeRow row = new ChargeRow();
            BeanUtils.copyProperties(CriminalChargeForm.from(charge), row);
            row.id = charge.getId();
            return row;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }
    }

    // ============ accessors ============

    public ProviderForm getDetails() {
        return details;
    }

    public void setDetails(ProviderForm details) {
        this.details = details;
    }

    public List<ProviderGroupForm> getGroups() {
        return groups;
    }

    public void setGroups(List<ProviderGroupForm> groups) {
        this.groups = groups;
    }

    public List<ProviderLocationForm> getLocations() {
        return locations;
    }

    public void setLocations(List<ProviderLocationForm> locations) {
        this.locations = locations;
    }

    public List<ProviderTaxonomyForm> getTaxonomies() {
        return taxonomies;
    }

    public void setTaxonomies(List<ProviderTaxonomyForm> taxonomies) {
        this.taxonomies = taxonomies;
    }

    public List<LicenseRow> getLicenses() {
        return licenses;
    }

    public void setLicenses(List<LicenseRow> licenses) {
        this.licenses = licenses;
    }

    public List<CertificationRow> getCertifications() {
        return certifications;
    }

    public void setCertifications(List<CertificationRow> certifications) {
        this.certifications = certifications;
    }

    public List<PrivilegeRow> getPrivileges() {
        return privileges;
    }

    public void setPrivileges(List<PrivilegeRow> privileges) {
        this.privileges = privileges;
    }

    public List<PolicyRow> getPolicies() {
        return policies;
    }

    public void setPolicies(List<PolicyRow> policies) {
        this.policies = policies;
    }

    public List<ClaimRow> getClaims() {
        return claims;
    }

    public void setClaims(List<ClaimRow> claims) {
        this.claims = claims;
    }

    public List<ReferenceRow> getReferences() {
        return references;
    }

    public void setReferences(List<ReferenceRow> references) {
        this.references = references;
    }

    public List<ChargeRow> getCharges() {
        return charges;
    }

    public void setCharges(List<ChargeRow> charges) {
        this.charges = charges;
    }
}
