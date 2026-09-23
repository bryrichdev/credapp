package dev.bryrich.credapp.taxonomy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GroupTaxonomyRepository extends JpaRepository<GroupTaxonomy, GroupTaxonomyId> {

    /** Every specialty for one group, primary first, with the code loaded. */
    @Query("""
        SELECT gt FROM GroupTaxonomy gt
        JOIN FETCH gt.taxonomy t
        WHERE gt.group.id = :groupId
        ORDER BY gt.primary DESC, t.specialty
        """)
    List<GroupTaxonomy> findByGroupIdWithTaxonomy(@Param("groupId") Long groupId);

    List<GroupTaxonomy> findByGroupId(Long groupId);

    Optional<GroupTaxonomy> findByGroupIdAndPrimaryTrue(Long groupId);

    boolean existsByGroupIdAndTaxonomyCode(Long groupId, String code);

    /** Same one-primary rule as providers; clear before promoting another row. */
    @Modifying
    @Query("UPDATE GroupTaxonomy gt SET gt.primary = false WHERE gt.group.id = :groupId")
    void clearPrimaryForGroup(@Param("groupId") Long groupId);
}
