package dev.bryrich.credapp.owner;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * Note there is no lookup by SSN. SsnConverter uses a fresh random IV per write, so the
 * same SSN encrypts to a different value every time and the column cannot be matched on.
 */
public interface OwnerRepository extends JpaRepository<Owner, Long> {

    Page<Owner> findByLastNameContainingIgnoreCase(String lastName, Pageable pageable);

    List<Owner> findByLastNameIgnoreCase(String lastName);

    List<Owner> findByLastNameIgnoreCaseAndFirstNameIgnoreCase(String lastName, String firstName);

    /**
     * Whether an SSN is stored, without decrypting it. Loading the entity would run
     * SsnConverter and produce a plaintext read that no audit row accounts for.
     */
    @Query("SELECT CASE WHEN o.ssn IS NOT NULL THEN true ELSE false END FROM Owner o WHERE o.id = :id")
    boolean hasSsn(@Param("id") Long id);

    /** Name plus date of birth is the practical way to spot an owner already on file. */
    List<Owner> findByLastNameIgnoreCaseAndDob(String lastName, LocalDate dob);
}
