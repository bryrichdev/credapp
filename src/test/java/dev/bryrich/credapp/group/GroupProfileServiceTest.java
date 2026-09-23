package dev.bryrich.credapp.group;

import dev.bryrich.credapp.common.UserGroupTestSupport;

import dev.bryrich.credapp.TestcontainersConfiguration;
import dev.bryrich.credapp.group.GroupProfileForm.LocationRow;
import dev.bryrich.credapp.group.GroupProfileForm.OwnerRow;
import dev.bryrich.credapp.group.GroupProfileForm.RelationRow;
import dev.bryrich.credapp.group.location.GroupLocationService;
import dev.bryrich.credapp.group.membership.GroupProviderForm;
import dev.bryrich.credapp.group.membership.GroupProviderService;
import dev.bryrich.credapp.owner.GroupOwner;
import dev.bryrich.credapp.owner.GroupOwnerRelation;
import dev.bryrich.credapp.owner.GroupOwnershipService;
import dev.bryrich.credapp.owner.Owner;
import dev.bryrich.credapp.owner.OwnerForm;
import dev.bryrich.credapp.owner.Relationship;
import dev.bryrich.credapp.provider.Provider;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the single group form's save against Postgres: new owners created inline, stakes,
 * and relationships whose composite keys depend on both stakes existing first.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestcontainersConfiguration.class)
@Transactional
class GroupProfileServiceTest extends UserGroupTestSupport {

    @Autowired
    private GroupProfileService profileService;

    @Autowired
    private GroupOwnershipService ownershipService;

    @Autowired
    private GroupLocationService locationService;

    @Autowired
    private GroupProviderService groupProviderService;

    @Autowired
    private EntityManager entityManager;

    private Owner onFile;
    private Provider provider;

    @BeforeEach
    void setUp() {
        onFile = new Owner("Pat", "Existing");
        entityManager.persist(onFile);
        provider = new Provider("Grace", "Hopper");
        entityManager.persist(provider);
        entityManager.flush();
    }

    @Test
    void oneSaveCreatesTheGroupANewOwnerAndTheirRelationship() {
        GroupProfileForm form = baseForm();
        form.getLocations().add(locationRow("Main Clinic"));
        form.getOwners().add(existingOwner("k1", onFile.getId(), "60"));
        form.getOwners().add(newOwner("k2", "Sam", "Newperson", "40"));
        form.getRelations().add(relation("k1", Relationship.PARENT, "k2"));
        form.getProviders().add(providerRow(provider.getId()));

        Group saved = save(null, form);

        List<GroupOwner> owners = ownershipService.findOwners(saved.getId());
        assertThat(owners).extracting(stake -> stake.getOwner().getFirstName())
                .containsExactlyInAnyOrder("Pat", "Sam");
        assertThat(ownershipService.totalPercentOwned(saved.getId())).isEqualByComparingTo("100");
        List<GroupOwnerRelation> relations = ownershipService.findRelations(saved.getId());
        assertThat(relations).hasSize(1);
        assertThat(locationService.findByGroupId(saved.getId())).hasSize(1);
        assertThat(groupProviderService.findProviders(saved.getId())).hasSize(1);
    }

    @Test
    void removingAnOwnerAndTheirRelationshipTogetherWorks() {
        GroupProfileForm form = baseForm();
        form.getOwners().add(existingOwner("k1", onFile.getId(), "60"));
        form.getOwners().add(newOwner("k2", "Sam", "Newperson", "40"));
        form.getRelations().add(relation("k1", Relationship.SPOUSE, "k2"));
        Group saved = save(null, form);

        GroupProfileForm edit = profileService.load(saved.getId());
        edit.getOwners().removeIf(row -> row.getOwnerId().equals(onFile.getId()));
        edit.getRelations().clear();
        save(saved.getId(), edit);

        assertThat(ownershipService.findOwners(saved.getId()))
                .singleElement()
                .extracting(stake -> stake.getOwner().getFirstName())
                .isEqualTo("Sam");
        assertThat(ownershipService.findRelations(saved.getId())).isEmpty();
    }

    @Test
    void changingARelationshipReplacesTheRow() {
        GroupProfileForm form = baseForm();
        form.getOwners().add(existingOwner("k1", onFile.getId(), null));
        form.getOwners().add(newOwner("k2", "Sam", "Newperson", null));
        form.getRelations().add(relation("k1", Relationship.PARENT, "k2"));
        Group saved = save(null, form);

        GroupProfileForm edit = profileService.load(saved.getId());
        edit.getRelations().getFirst().setRelationship(Relationship.SIBLING);
        save(saved.getId(), edit);

        assertThat(ownershipService.findRelations(saved.getId()))
                .singleElement()
                .extracting(GroupOwnerRelation::getRelationship)
                .isEqualTo(Relationship.SIBLING);
    }

    @Test
    void validateFlagsStakesOverOneHundredPercent() {
        GroupProfileForm form = baseForm();
        form.getOwners().add(existingOwner("k1", onFile.getId(), "60"));
        form.getOwners().add(newOwner("k2", "Sam", "Newperson", "50"));

        BindingResult errors = validate(form);

        assertThat(errors.getFieldError("owners[1].percentOwned")).isNotNull();
    }

    @Test
    void validateFlagsTheSameOwnerTwiceAndARelationToARemovedRow() {
        GroupProfileForm form = baseForm();
        form.getOwners().add(existingOwner("k1", onFile.getId(), null));
        form.getOwners().add(existingOwner("k2", onFile.getId(), null));
        form.getRelations().add(relation("k1", Relationship.SPOUSE, "gone"));

        BindingResult errors = validate(form);

        assertThat(errors.getFieldError("owners[1].ownerId")).isNotNull();
        assertThat(errors.getFieldError("relations[0].relatedOwnerKey")).isNotNull();
    }

    @Test
    void validateAsksForAPersonOnARowWithNeitherModeFilledIn() {
        GroupProfileForm form = baseForm();
        form.getOwners().add(existingOwner("k1", null, null));

        assertThat(validate(form).getFieldError("owners[0].ownerId")).isNotNull();
    }

    // ============ helpers ============

    private Group save(Long id, GroupProfileForm form) {
        assertThat(validate(form).getAllErrors()).isEmpty();
        Group saved = profileService.save(id, form);
        entityManager.flush();
        entityManager.clear();
        return saved;
    }

    private BindingResult validate(GroupProfileForm form) {
        BindingResult errors = new BeanPropertyBindingResult(form, "form");
        profileService.validate(form, errors);
        return errors;
    }

    private static GroupProfileForm baseForm() {
        GroupProfileForm form = new GroupProfileForm();
        form.getDetails().setLbn("Harbor Pediatrics");
        form.getDetails().setTaxId("555666777");
        return form;
    }

    private static LocationRow locationRow(String name) {
        LocationRow row = new LocationRow();
        row.setLocationName(name);
        row.setAddress("1 Harbor Way");
        return row;
    }

    private static OwnerRow existingOwner(String key, Long ownerId, String percent) {
        OwnerRow row = new OwnerRow();
        row.setKey(key);
        row.setOwnerId(ownerId);
        row.setPercentOwned(percent == null ? null : new BigDecimal(percent));
        return row;
    }

    private static OwnerRow newOwner(String key, String first, String last, String percent) {
        OwnerForm person = new OwnerForm();
        person.setFirstName(first);
        person.setLastName(last);
        OwnerRow row = new OwnerRow();
        row.setKey(key);
        row.setMode(OwnerRow.NEW);
        row.setNewOwner(person);
        row.setPercentOwned(percent == null ? null : new BigDecimal(percent));
        return row;
    }

    private static RelationRow relation(String ownerKey, Relationship relationship, String relatedKey) {
        RelationRow row = new RelationRow();
        row.setOwnerKey(ownerKey);
        row.setRelationship(relationship);
        row.setRelatedOwnerKey(relatedKey);
        return row;
    }

    private static GroupProviderForm providerRow(Long providerId) {
        GroupProviderForm row = new GroupProviderForm();
        row.setProviderId(providerId);
        return row;
    }
}
