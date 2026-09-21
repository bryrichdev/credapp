package dev.bryrich.credapp.repository;

import dev.bryrich.credapp.entity.PayerContact;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PayerContactRepository extends JpaRepository<PayerContact, Long> {

    List<PayerContact> findByPayerId(Long payerId);

    List<PayerContact> findByGroupId(Long groupId);

    List<PayerContact> findByProviderId(Long providerId);

    List<PayerContact> findByPayerIdAndGroupId(Long payerId, Long groupId);

    List<PayerContact> findByPayerIdAndProviderId(Long payerId, Long providerId);

    /** Contacts scoped to the payer as a whole, tied to no group or provider. */
    List<PayerContact> findByPayerIdAndGroupIsNullAndProviderIsNull(Long payerId);

    /** Scoped lookup, so a contact can't be edited through the wrong payer. */
    Optional<PayerContact> findByIdAndPayerId(Long id, Long payerId);
}
