package dev.bryrich.credapp.group;

import dev.bryrich.credapp.group.location.GroupLocation;
import dev.bryrich.credapp.group.location.GroupLocationForm;
import dev.bryrich.credapp.group.membership.GroupProviderForm;
import dev.bryrich.credapp.malpractice.MalpracticePolicy;
import dev.bryrich.credapp.malpractice.MalpracticePolicyForm;
import dev.bryrich.credapp.owner.GroupOwner;
import dev.bryrich.credapp.owner.GroupOwnerRelation;
import dev.bryrich.credapp.owner.OwnerForm;
import dev.bryrich.credapp.owner.Relationship;
import dev.bryrich.credapp.payer.enrollment.GroupPayerForm;
import dev.bryrich.credapp.taxonomy.GroupTaxonomyForm;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.BeanUtils;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Everything about a group on one screen: its own fields plus locations, owners and how
 * they're related, providers, specialties and policies. The same form serves create and
 * edit, and GroupProfileService saves all of it in one transaction.
 *
 * Rows that already exist carry their id (or, for link tables, the id of the other side);
 * a row without one is new. On edit, a saved item whose row was removed is deleted.
 */
public class GroupProfileForm {

    @Valid
    private GroupForm details = new GroupForm();

    private List<@Valid LocationRow> locations = new ArrayList<>();

    private List<@Valid OwnerRow> owners = new ArrayList<>();

    private List<@Valid RelationRow> relations = new ArrayList<>();

    private List<@Valid GroupProviderForm> providers = new ArrayList<>();

    private List<@Valid GroupTaxonomyForm> taxonomies = new ArrayList<>();

    private List<@Valid PolicyRow> policies = new ArrayList<>();

    private List<@Valid GroupPayerForm> payers = new ArrayList<>();

    public GroupProfileForm() {
    }

    /** Drops the nulls binding leaves behind if the submitted indexes had a gap. */
    public void compact() {
        locations.removeIf(Objects::isNull);
        owners.removeIf(Objects::isNull);
        relations.removeIf(Objects::isNull);
        providers.removeIf(Objects::isNull);
        taxonomies.removeIf(Objects::isNull);
        policies.removeIf(Objects::isNull);
        payers.removeIf(Objects::isNull);
    }

    // ============ rows ============

    public static class LocationRow extends GroupLocationForm {
        private Long id;

        public static LocationRow from(GroupLocation location) {
            LocationRow row = new LocationRow();
            BeanUtils.copyProperties(GroupLocationForm.from(location), row);
            row.id = location.getId();
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
     * One owner's stake. The person is either picked from owners already on file
     * (mode "existing", ownerId) or typed in to be created on save (mode "new", newOwner).
     * The key lets a relationship row on the same page point at this row before either is
     * saved: "o" plus the owner id for a stake on file, anything else for a new row.
     */
    public static class OwnerRow {
        public static final String EXISTING = "existing";
        public static final String NEW = "new";

        private String key;

        private String mode = EXISTING;

        private Long ownerId;

        /** Only bound in "new" mode; the page disables these fields otherwise. */
        @Valid
        private OwnerForm newOwner;

        @DecimalMin(value = "0.01", message = "Percent owned must be greater than 0")
        @DecimalMax(value = "100.00", message = "Percent owned cannot exceed 100")
        @Digits(integer = 3, fraction = 2, message = "Percent owned allows at most two decimal places")
        private BigDecimal percentOwned;

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate effectiveDate;

        public static OwnerRow from(GroupOwner stake) {
            OwnerRow row = new OwnerRow();
            row.ownerId = stake.getOwner().getId();
            row.key = keyFor(row.ownerId);
            row.percentOwned = stake.getPercentOwned();
            row.effectiveDate = stake.getEffectiveDate();
            return row;
        }

        public static String keyFor(Long ownerId) {
            return "o" + ownerId;
        }

        /** Not a bean getter on purpose: "isNewOwner" would clash with the newOwner property. */
        public boolean createsNewOwner() {
            return NEW.equals(mode);
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public Long getOwnerId() {
            return ownerId;
        }

        public void setOwnerId(Long ownerId) {
            this.ownerId = ownerId;
        }

        public OwnerForm getNewOwner() {
            return newOwner;
        }

        public void setNewOwner(OwnerForm newOwner) {
            this.newOwner = newOwner;
        }

        public BigDecimal getPercentOwned() {
            return percentOwned;
        }

        public void setPercentOwned(BigDecimal percentOwned) {
            this.percentOwned = percentOwned;
        }

        public LocalDate getEffectiveDate() {
            return effectiveDate;
        }

        public void setEffectiveDate(LocalDate effectiveDate) {
            this.effectiveDate = effectiveDate;
        }
    }

    /** Reads as "ownerKey is {relationship} of relatedOwnerKey", both OwnerRow keys. */
    public static class RelationRow {
        @NotBlank(message = "Owner is required")
        private String ownerKey;

        @NotNull(message = "Relationship is required")
        private Relationship relationship;

        @NotBlank(message = "Related owner is required")
        private String relatedOwnerKey;

        public static RelationRow from(GroupOwnerRelation relation) {
            RelationRow row = new RelationRow();
            row.ownerKey = OwnerRow.keyFor(relation.getId().getOwnerId());
            row.relationship = relation.getRelationship();
            row.relatedOwnerKey = OwnerRow.keyFor(relation.getId().getRelatedOwnerId());
            return row;
        }

        public String getOwnerKey() {
            return ownerKey;
        }

        public void setOwnerKey(String ownerKey) {
            this.ownerKey = ownerKey;
        }

        public Relationship getRelationship() {
            return relationship;
        }

        public void setRelationship(Relationship relationship) {
            this.relationship = relationship;
        }

        public String getRelatedOwnerKey() {
            return relatedOwnerKey;
        }

        public void setRelatedOwnerKey(String relatedOwnerKey) {
            this.relatedOwnerKey = relatedOwnerKey;
        }
    }

    public static class PolicyRow extends MalpracticePolicyForm {
        private Long id;

        public static PolicyRow from(MalpracticePolicy policy) {
            PolicyRow row = new PolicyRow();
            BeanUtils.copyProperties(MalpracticePolicyForm.from(policy), row);
            row.id = policy.getId();
            return row;
        }

        /** The owner is the group this page is for, so neither id is filled in here. */
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
    }

    // ============ accessors ============

    public GroupForm getDetails() {
        return details;
    }

    public void setDetails(GroupForm details) {
        this.details = details;
    }

    public List<LocationRow> getLocations() {
        return locations;
    }

    public void setLocations(List<LocationRow> locations) {
        this.locations = locations;
    }

    public List<OwnerRow> getOwners() {
        return owners;
    }

    public void setOwners(List<OwnerRow> owners) {
        this.owners = owners;
    }

    public List<RelationRow> getRelations() {
        return relations;
    }

    public void setRelations(List<RelationRow> relations) {
        this.relations = relations;
    }

    public List<GroupProviderForm> getProviders() {
        return providers;
    }

    public void setProviders(List<GroupProviderForm> providers) {
        this.providers = providers;
    }

    public List<GroupTaxonomyForm> getTaxonomies() {
        return taxonomies;
    }

    public void setTaxonomies(List<GroupTaxonomyForm> taxonomies) {
        this.taxonomies = taxonomies;
    }

    public List<PolicyRow> getPolicies() {
        return policies;
    }

    public void setPolicies(List<PolicyRow> policies) {
        this.policies = policies;
    }

    public List<GroupPayerForm> getPayers() {
        return payers;
    }

    public void setPayers(List<GroupPayerForm> payers) {
        this.payers = payers;
    }
}
