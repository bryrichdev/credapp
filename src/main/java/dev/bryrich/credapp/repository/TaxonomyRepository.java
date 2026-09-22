package dev.bryrich.credapp.repository;

import dev.bryrich.credapp.entity.Taxonomy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaxonomyRepository extends JpaRepository<Taxonomy, String> {

    List<Taxonomy> findAllByOrderBySpecialtyAsc();

    List<Taxonomy> findBySpecialtyContainingIgnoreCaseOrderBySpecialtyAsc(String specialty);

    List<Taxonomy> findByGroupingOrderBySpecialtyAsc(String grouping);
}
