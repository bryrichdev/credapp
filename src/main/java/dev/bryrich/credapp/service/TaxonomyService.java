package dev.bryrich.credapp.service;

import dev.bryrich.credapp.entity.Taxonomy;
import dev.bryrich.credapp.exception.TaxonomyNotFoundException;
import dev.bryrich.credapp.repository.TaxonomyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Reference data. Rows are seeded from the published NUCC code set, not entered by users. */
@Service
public class TaxonomyService {

    private final TaxonomyRepository taxonomyRepository;

    public TaxonomyService(TaxonomyRepository taxonomyRepository) {
        this.taxonomyRepository = taxonomyRepository;
    }

    @Transactional(readOnly = true)
    public Taxonomy findByCode(String code) {
        return taxonomyRepository.findById(code)
                .orElseThrow(() -> new TaxonomyNotFoundException(code));
    }

    @Transactional(readOnly = true)
    public List<Taxonomy> findAllForSelect() {
        return taxonomyRepository.findAllByOrderBySpecialtyAsc();
    }

    @Transactional(readOnly = true)
    public List<Taxonomy> search(String specialty) {
        if (specialty == null || specialty.isBlank()) {
            return findAllForSelect();
        }
        return taxonomyRepository.findBySpecialtyContainingIgnoreCaseOrderBySpecialtyAsc(specialty);
    }

    @Transactional(readOnly = true)
    public List<Taxonomy> findByGrouping(String grouping) {
        return taxonomyRepository.findByGroupingOrderBySpecialtyAsc(grouping);
    }
}
