package dev.bryrich.credapp.payer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PayerContactRepository extends JpaRepository<PayerContact, Long> {

    List<PayerContact> findByPayerId(Long payerId);

    /** Contacts plus whatever each one is scoped to, so a listing doesn't N+1. */
    @Query("""
        SELECT c FROM PayerContact c
        LEFT JOIN FETCH c.group
        LEFT JOIN FETCH c.provider
        WHERE c.payer.id = :payerId
        """)
    List<PayerContact> findByPayerIdWithScope(@Param("payerId") Long payerId);

    List<PayerContact> findByGroupId(Long groupId);

    List<PayerContact> findByProviderId(Long providerId);

    List<PayerContact> findByPayerIdAndGroupId(Long payerId, Long groupId);

    List<PayerContact> findByPayerIdAndProviderId(Long payerId, Long providerId);

    /** Contacts scoped to the payer as a whole, tied to no group or provider. */
    List<PayerContact> findByPayerIdAndGroupIsNullAndProviderIsNull(Long payerId);

    /** Scoped lookup, so a contact can't be edited through the wrong payer. */
    Optional<PayerContact> findByIdAndPayerId(Long id, Long payerId);

    /**
     * Who can be a group's account rep: contacts tied to no one in particular, or to that
     * group. groupId is null for a group not saved yet, which leaves only the former.
     */
    @Query("SELECT c FROM PayerContact c JOIN FETCH c.payer p LEFT JOIN c.group g "
            + "WHERE c.provider IS NULL AND (g IS NULL OR g.id = :groupId) "
            + "ORDER BY LOWER(p.name), LOWER(COALESCE(c.name, c.role))")
    List<PayerContact> findRepCandidates(@Param("groupId") Long groupId);
}
