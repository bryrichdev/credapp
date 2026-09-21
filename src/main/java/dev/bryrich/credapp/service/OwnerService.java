package dev.bryrich.credapp.service;

import dev.bryrich.credapp.entity.Owner;
import dev.bryrich.credapp.exception.OwnerNotFoundException;
import dev.bryrich.credapp.repository.OwnerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

@Service
public class OwnerService {

    private final OwnerRepository ownerRepository;

    public OwnerService(OwnerRepository ownerRepository) {
        this.ownerRepository = ownerRepository;
    }

    @Transactional(readOnly = true)
    public Owner findById(Long id) {
        return ownerRepository.findById(id)
                .orElseThrow(() -> new OwnerNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Page<Owner> search(String lastName, Pageable pageable) {
        return ownerRepository.findByLastNameContainingIgnoreCase(lastName, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Owner> findAll(Pageable pageable) {
        return ownerRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<Owner> findAllForSelect() {
        return ownerRepository.findAll(Sort.by("lastName", "firstName"));
    }

    /**
     * Candidates for "is this person already on file?" before creating a duplicate. The
     * SSN can't help here, since it encrypts differently on every write.
     */
    @Transactional(readOnly = true)
    public List<Owner> findPossibleMatches(String lastName, LocalDate dob) {
        return dob == null
                ? ownerRepository.findByLastNameIgnoreCase(lastName)
                : ownerRepository.findByLastNameIgnoreCaseAndDob(lastName, dob);
    }

    @Transactional
    public Owner create(Owner owner) {
        return ownerRepository.save(owner);
    }

    @Transactional
    public Owner update(Long id, Consumer<Owner> changes) {
        Owner owner = ownerRepository.findById(id)
                .orElseThrow(() -> new OwnerNotFoundException(id));
        changes.accept(owner);
        return owner;
    }

    @Transactional
    public void delete(Long id) {
        Owner owner = ownerRepository.findById(id)
                .orElseThrow(() -> new OwnerNotFoundException(id));
        ownerRepository.delete(owner);
    }
}
