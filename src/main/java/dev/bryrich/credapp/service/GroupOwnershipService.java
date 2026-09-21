package dev.bryrich.credapp.service;

import dev.bryrich.credapp.entity.Group;
import dev.bryrich.credapp.entity.GroupOwner;
import dev.bryrich.credapp.entity.GroupOwnerRelation;
import dev.bryrich.credapp.entity.Owner;
import dev.bryrich.credapp.entity.enums.Relationship;
import dev.bryrich.credapp.exception.GroupNotFoundException;
import dev.bryrich.credapp.exception.OwnerNotFoundException;
import dev.bryrich.credapp.exception.OwnershipPercentExceededException;
import dev.bryrich.credapp.repository.GroupOwnerRelationRepository;
import dev.bryrich.credapp.repository.GroupOwnerRepository;
import dev.bryrich.credapp.repository.GroupRepository;
import dev.bryrich.credapp.repository.OwnerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Who owns a group, and how those owners are related to each other. Both live here
 * because they are the same screen and the same section of the payer forms.
 */
@Service
public class GroupOwnershipService {

    private static final BigDecimal MAX_PERCENT = new BigDecimal("100");

    private final GroupOwnerRepository groupOwnerRepository;
    private final GroupOwnerRelationRepository relationRepository;
    private final GroupRepository groupRepository;
    private final OwnerRepository ownerRepository;

    public GroupOwnershipService(GroupOwnerRepository groupOwnerRepository,
                                 GroupOwnerRelationRepository relationRepository,
                                 GroupRepository groupRepository,
                                 OwnerRepository ownerRepository) {
        this.groupOwnerRepository = groupOwnerRepository;
        this.relationRepository = relationRepository;
        this.groupRepository = groupRepository;
        this.ownerRepository = ownerRepository;
    }

    // ----- owners -----

    /** Every owner of a group, with the person loaded, ordered by stake. */
    @Transactional(readOnly = true)
    public List<GroupOwner> findOwners(Long groupId) {
        requireGroup(groupId);
        return groupOwnerRepository.findByGroupIdWithOwner(groupId);
    }

    @Transactional(readOnly = true)
    public List<GroupOwner> findGroupsOwnedBy(Long ownerId) {
        if (!ownerRepository.existsById(ownerId)) {
            throw new OwnerNotFoundException(ownerId);
        }
        return groupOwnerRepository.findByOwnerIdWithGroup(ownerId);
    }

    @Transactional(readOnly = true)
    public GroupOwner findOwner(Long groupId, Long ownerId) {
        return groupOwnerRepository.findByGroupIdAndOwnerId(groupId, ownerId)
                .orElseThrow(() -> new OwnerNotFoundException(ownerId, groupId));
    }

    @Transactional(readOnly = true)
    public BigDecimal totalPercentOwned(Long groupId) {
        return groupOwnerRepository.sumPercentOwnedByGroupId(groupId);
    }

    /** Percentage still unclaimed, for showing on the form. */
    @Transactional(readOnly = true)
    public BigDecimal remainingPercent(Long groupId) {
        return MAX_PERCENT.subtract(totalPercentOwned(groupId));
    }

    @Transactional
    public GroupOwner addOwner(Long groupId, Long ownerId, BigDecimal percentOwned,
                               LocalDate effectiveDate) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException(groupId));
        Owner owner = ownerRepository.findById(ownerId)
                .orElseThrow(() -> new OwnerNotFoundException(ownerId));

        checkPercentFits(groupId, groupOwnerRepository.sumPercentOwnedByGroupId(groupId), percentOwned);

        return groupOwnerRepository.save(new GroupOwner(group, owner, percentOwned, effectiveDate));
    }

    @Transactional
    public GroupOwner updateOwner(Long groupId, Long ownerId, BigDecimal percentOwned,
                                  LocalDate effectiveDate) {
        GroupOwner groupOwner = groupOwnerRepository.findByGroupIdAndOwnerId(groupId, ownerId)
                .orElseThrow(() -> new OwnerNotFoundException(ownerId, groupId));

        checkPercentFits(groupId,
                groupOwnerRepository.sumPercentOwnedByGroupIdExcluding(groupId, ownerId),
                percentOwned);

        groupOwner.setPercentOwned(percentOwned);
        groupOwner.setEffectiveDate(effectiveDate);
        return groupOwner;
    }

    /**
     * Removes someone from a group. Their relationships inside that group go with them,
     * via the ON DELETE CASCADE on group_owner_relationships.
     */
    @Transactional
    public void removeOwner(Long groupId, Long ownerId) {
        GroupOwner groupOwner = groupOwnerRepository.findByGroupIdAndOwnerId(groupId, ownerId)
                .orElseThrow(() -> new OwnerNotFoundException(ownerId, groupId));
        groupOwnerRepository.delete(groupOwner);
    }

    // ----- relationships between those owners -----

    /** Every relationship in a group, with both people loaded. */
    @Transactional(readOnly = true)
    public List<GroupOwnerRelation> findRelations(Long groupId) {
        requireGroup(groupId);
        return relationRepository.findByGroupIdWithOwners(groupId);
    }

    @Transactional(readOnly = true)
    public List<GroupOwnerRelation> findRelationsFor(Long groupId, Long ownerId) {
        return relationRepository.findByGroupIdAndOwnerIdEitherSide(groupId, ownerId);
    }

    /**
     * Records that ownerA is {relationship} of ownerB within this group. Both must already
     * be owners of it. GroupOwnerRelation.of puts the ids in the order gor_ordered wants
     * and flips the relationship if it had to swap them.
     */
    @Transactional
    public GroupOwnerRelation addRelation(Long groupId, Long ownerA, Long ownerB,
                                          Relationship relationship) {
        requireOwnerOfGroup(groupId, ownerA);
        requireOwnerOfGroup(groupId, ownerB);

        // Replacing an existing pair: the delete has to reach the database before the
        // insert, or the two rows collide on the primary key.
        relationRepository.findPair(groupId, ownerA, ownerB).ifPresent(existing -> {
            relationRepository.delete(existing);
            relationRepository.flush();
        });

        return relationRepository.save(
                GroupOwnerRelation.of(groupId, ownerA, ownerB, relationship));
    }

    @Transactional
    public void removeRelation(Long groupId, Long ownerA, Long ownerB) {
        relationRepository.findPair(groupId, ownerA, ownerB)
                .ifPresent(relationRepository::delete);
    }

    // ----- helpers -----

    private void requireGroup(Long groupId) {
        if (!groupRepository.existsById(groupId)) {
            throw new GroupNotFoundException(groupId);
        }
    }

    private void requireOwnerOfGroup(Long groupId, Long ownerId) {
        if (!groupOwnerRepository.existsByGroupIdAndOwnerId(groupId, ownerId)) {
            throw new OwnerNotFoundException(ownerId, groupId);
        }
    }

    private void checkPercentFits(Long groupId, BigDecimal existingTotal, BigDecimal addition) {
        if (addition == null) {
            return;
        }
        BigDecimal total = existingTotal.add(addition);
        if (total.compareTo(MAX_PERCENT) > 0) {
            throw new OwnershipPercentExceededException(groupId, total);
        }
    }
}
