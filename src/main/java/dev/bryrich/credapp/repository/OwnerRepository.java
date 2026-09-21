package dev.bryrich.credapp.repository;

import dev.bryrich.credapp.entity.Owner;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

/**
 * Note there is no lookup by SSN. SsnConverter uses a fresh random IV per write, so the
 * same SSN encrypts to a different value every time and the column cannot be matched on.
 */
public interface OwnerRepository extends JpaRepository<Owner, Long> {

    Page<Owner> findByLastNameContainingIgnoreCase(String lastName, Pageable pageable);

    List<Owner> findByLastNameIgnoreCaseAndFirstNameIgnoreCase(String lastName, String firstName);

    /** Name plus date of birth is the practical way to spot an owner already on file. */
    List<Owner> findByLastNameIgnoreCaseAndDob(String lastName, LocalDate dob);
}
