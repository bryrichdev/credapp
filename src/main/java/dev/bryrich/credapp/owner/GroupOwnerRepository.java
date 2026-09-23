package dev.bryrich.credapp.owner;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface GroupOwnerRepository extends JpaRepository<GroupOwner, GroupOwnerId> {

    /** Every owner of one group, with the person loaded. This is the 855 ownership section. */
    @Query("""
        SELECT go FROM GroupOwner go
        JOIN FETCH go.owner o
        WHERE go.group.id = :groupId
        ORDER BY go.percentOwned DESC, o.lastName, o.firstName
        """)
    List<GroupOwner> findByGroupIdWithOwner(@Param("groupId") Long groupId);

    /** Every group one person owns, with the group loaded. */
    @Query("""
        SELECT go FROM GroupOwner go
        JOIN FETCH go.group
        WHERE go.owner.id = :ownerId
        """)
    List<GroupOwner> findByOwnerIdWithGroup(@Param("ownerId") Long ownerId);

    List<GroupOwner> findByGroupId(Long groupId);

    List<GroupOwner> findByOwnerId(Long ownerId);

    Optional<GroupOwner> findByGroupIdAndOwnerId(Long groupId, Long ownerId);

    boolean existsByGroupIdAndOwnerId(Long groupId, Long ownerId);

    /**
     * Total percentage already recorded for a group. A CHECK constraint can't see other
     * rows, so call this before saving to keep a group from going over 100.
     */
    @Query("SELECT COALESCE(SUM(go.percentOwned), 0) FROM GroupOwner go WHERE go.group.id = :groupId")
    BigDecimal sumPercentOwnedByGroupId(@Param("groupId") Long groupId);

    /** Same total, ignoring one owner's existing row, for use when editing that row. */
    @Query("""
        SELECT COALESCE(SUM(go.percentOwned), 0) FROM GroupOwner go
        WHERE go.group.id = :groupId AND go.owner.id <> :excludedOwnerId
        """)
    BigDecimal sumPercentOwnedByGroupIdExcluding(@Param("groupId") Long groupId,
                                                 @Param("excludedOwnerId") Long excludedOwnerId);
}
