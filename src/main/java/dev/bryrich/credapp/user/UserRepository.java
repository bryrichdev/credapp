package dev.bryrich.credapp.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findAllByRole(Role role);

    long countByRole(Role role);

    Page<User> findByEmailContainingIgnoreCaseOrFullNameContainingIgnoreCase(
            String email, String fullName, Pageable pageable);

    @Query("""
            select u from User u where (:groupId is null or u.userGroupId = :groupId)
            and (lower(u.email) like lower(concat('%', :term, '%'))
                 or lower(u.fullName) like lower(concat('%', :term, '%')))
            """)
    Page<User> searchInGroup(@Param("groupId") Long groupId, @Param("term") String term, Pageable pageable);

    /** Per user group: [userGroupId, accounts, accounts waiting for approval]. */
    @Query("""
            select u.userGroupId, count(u),
                   sum(case when u.membershipStatus = dev.bryrich.credapp.user.MembershipStatus.PENDING then 1 else 0 end)
            from User u group by u.userGroupId
            """)
    List<Object[]> countByUserGroup();
}
