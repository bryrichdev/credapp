package dev.bryrich.credapp.service;

import dev.bryrich.credapp.entity.Group;
import dev.bryrich.credapp.entity.GroupTaxonomy;
import dev.bryrich.credapp.entity.GroupTaxonomyId;
import dev.bryrich.credapp.entity.Taxonomy;
import dev.bryrich.credapp.exception.GroupNotFoundException;
import dev.bryrich.credapp.exception.GroupTaxonomyNotFoundException;
import dev.bryrich.credapp.exception.TaxonomyNotFoundException;
import dev.bryrich.credapp.repository.GroupRepository;
import dev.bryrich.credapp.repository.GroupTaxonomyRepository;
import dev.bryrich.credapp.repository.TaxonomyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** A group's specialties, replacing the free-text groups.specialty column dropped in V5. */
@Service
public class GroupTaxonomyService {

    private final GroupTaxonomyRepository groupTaxonomyRepository;
    private final GroupRepository groupRepository;
    private final TaxonomyRepository taxonomyRepository;

    public GroupTaxonomyService(GroupTaxonomyRepository groupTaxonomyRepository,
                                GroupRepository groupRepository,
                                TaxonomyRepository taxonomyRepository) {
        this.groupTaxonomyRepository = groupTaxonomyRepository;
        this.groupRepository = groupRepository;
        this.taxonomyRepository = taxonomyRepository;
    }

    @Transactional(readOnly = true)
    public List<GroupTaxonomy> findByGroupId(Long groupId) {
        if (!groupRepository.existsById(groupId)) {
            throw new GroupNotFoundException(groupId);
        }
        return groupTaxonomyRepository.findByGroupIdWithTaxonomy(groupId);
    }

    @Transactional(readOnly = true)
    public GroupTaxonomy findPrimary(Long groupId) {
        return groupTaxonomyRepository.findByGroupIdAndPrimaryTrue(groupId).orElse(null);
    }

    @Transactional
    public GroupTaxonomy assign(Long groupId, String code, boolean primary) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException(groupId));
        Taxonomy taxonomy = taxonomyRepository.findById(code)
                .orElseThrow(() -> new TaxonomyNotFoundException(code));

        if (primary) {
            groupTaxonomyRepository.clearPrimaryForGroup(groupId);
        }

        return groupTaxonomyRepository.findByGroupId(groupId).stream()
                .filter(existing -> existing.getTaxonomy().getCode().equals(code))
                .findFirst()
                .map(existing -> {
                    existing.setPrimary(primary);
                    return existing;
                })
                .orElseGet(() -> groupTaxonomyRepository.save(
                        new GroupTaxonomy(group, taxonomy, primary)));
    }

    @Transactional
    public void unassign(Long groupId, String code) {
        GroupTaxonomy assignment = groupTaxonomyRepository
                .findById(new GroupTaxonomyId(groupId, code))
                .orElseThrow(() -> new GroupTaxonomyNotFoundException(groupId, code));
        groupTaxonomyRepository.delete(assignment);
    }
}
