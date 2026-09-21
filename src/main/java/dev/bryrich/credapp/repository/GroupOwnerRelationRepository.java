package dev.bryrich.credapp.repository;

import dev.bryrich.credapp.entity.GroupOwnerRelation;
import dev.bryrich.credapp.entity.GroupOwnerRelationId;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Rows are stored once per pair, lower owner id first (see gor_ordered). Queries that ask
 * about a single owner therefore have to check both columns.
 */
public interface GroupOwnerRelationRepository
        extends JpaRepository<GroupOwnerRelation, GroupOwnerRelationId> {

    /** Every relationship in one group, with both people loaded. Fills the payer form. */
    @Query("""
        SELECT r FROM GroupOwnerRelation r
        JOIN FETCH r.owner go1
        JOIN FETCH go1.owner
        JOIN FETCH r.relatedOwner go2
        JOIN FETCH go2.owner
        WHERE r.id.groupId = :groupId
        """)
    List<GroupOwnerRelation> findByGroupIdWithOwners(@Param("groupId") Long groupId);

    /** Every relationship one owner has inside a group, in either direction. */
    @Query("""
        SELECT r FROM GroupOwnerRelation r
        WHERE r.id.groupId = :groupId
          AND (r.id.ownerId = :ownerId OR r.id.relatedOwnerId = :ownerId)
        """)
    List<GroupOwnerRelation> findByGroupIdAndOwnerIdEitherSide(@Param("groupId") Long groupId,
                                                               @Param("ownerId") Long ownerId);

    List<GroupOwnerRelation> findByIdGroupId(Long groupId);

    /**
     * Looks up one pair without caring which id the caller put first. Build the id with
     * GroupOwnerRelation.of(...) when inserting, so the ordering rule stays in one place.
     */
    @Query("""
        SELECT r FROM GroupOwnerRelation r
        WHERE r.id.groupId = :groupId
          AND ((r.id.ownerId = :ownerA AND r.id.relatedOwnerId = :ownerB)
            OR (r.id.ownerId = :ownerB AND r.id.relatedOwnerId = :ownerA))
        """)
    Optional<GroupOwnerRelation> findPair(@Param("groupId") Long groupId,
                                          @Param("ownerA") Long ownerA,
                                          @Param("ownerB") Long ownerB);

    long countByIdGroupId(Long groupId);
}
