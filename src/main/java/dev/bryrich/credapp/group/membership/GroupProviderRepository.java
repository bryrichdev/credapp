package dev.bryrich.credapp.group.membership;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GroupProviderRepository extends JpaRepository<GroupProvider, GroupProviderId> {

    /** Every provider assigned to one group, with the provider loaded. */
    @Query("""
        SELECT gp FROM GroupProvider gp
        JOIN FETCH gp.provider p
        WHERE gp.group.id = :groupId
        ORDER BY p.lastName, p.firstName
        """)
    List<GroupProvider> findByGroupIdWithProvider(@Param("groupId") Long groupId);

    /** Every group one provider is assigned to, with the group loaded. */
    @Query("""
        SELECT gp FROM GroupProvider gp
        JOIN FETCH gp.group
        WHERE gp.provider.id = :providerId
        """)
    List<GroupProvider> findByProviderIdWithGroup(@Param("providerId") Long providerId);

    List<GroupProvider> findByGroupId(Long groupId);

    List<GroupProvider> findByProviderId(Long providerId);

    Optional<GroupProvider> findByGroupIdAndProviderId(Long groupId, Long providerId);

    boolean existsByGroupIdAndProviderId(Long groupId, Long providerId);

    long countByGroupId(Long groupId);
}
