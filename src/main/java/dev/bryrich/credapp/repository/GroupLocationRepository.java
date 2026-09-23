package dev.bryrich.credapp.repository;

import dev.bryrich.credapp.entity.GroupLocation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface GroupLocationRepository extends JpaRepository<GroupLocation, Long> {

    List<GroupLocation> findByGroupId(Long groupId);

    /** Scoped lookup, so a location can't be edited through the wrong group. */
    Optional<GroupLocation> findByIdAndGroupId(Long id, Long groupId);

    long countByGroupId(Long groupId);

    /** Every location with its group, for the provider form's location picker. */
    @Query("""
            SELECT l FROM GroupLocation l
            JOIN FETCH l.group g
            ORDER BY g.lbn, l.locationName
            """)
    List<GroupLocation> findAllWithGroup();
}
