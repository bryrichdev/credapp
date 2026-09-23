package dev.bryrich.credapp.payer.enrollment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface GroupPayerRepository extends JpaRepository<GroupPayer, Long> {

    @Query("SELECT e FROM GroupPayer e JOIN FETCH e.payer LEFT JOIN FETCH e.accountRep "
            + "WHERE e.group.id = :groupId ORDER BY LOWER(e.payer.name)")
    List<GroupPayer> findByGroupId(@Param("groupId") Long groupId);

    @Query("SELECT e FROM GroupPayer e JOIN FETCH e.group g LEFT JOIN FETCH e.accountRep "
            + "WHERE e.payer.id = :payerId ORDER BY LOWER(g.lbn)")
    List<GroupPayer> findByPayerId(@Param("payerId") Long payerId);

    /** The enrollments of several groups, for showing a provider their groups' reps. */
    @Query("SELECT e FROM GroupPayer e JOIN FETCH e.group JOIN FETCH e.payer LEFT JOIN FETCH e.accountRep "
            + "WHERE e.group.id IN :groupIds")
    List<GroupPayer> findByGroupIdIn(@Param("groupIds") Collection<Long> groupIds);

    /** A contact moved to another group can no longer be this group's rep. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE GroupPayer e SET e.accountRep = null WHERE e.accountRep.id = :contactId AND e.group.id <> :groupId")
    int clearRepOutsideGroup(@Param("contactId") Long contactId, @Param("groupId") Long groupId);

    /** A contact moved to a provider can't be any group's rep. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE GroupPayer e SET e.accountRep = null WHERE e.accountRep.id = :contactId")
    int clearRep(@Param("contactId") Long contactId);
}
