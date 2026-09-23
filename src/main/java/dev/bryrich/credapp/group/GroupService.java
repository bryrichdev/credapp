package dev.bryrich.credapp.group;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Consumer;

@Service
public class GroupService {

    private final GroupRepository groupRepository;

    public GroupService(GroupRepository groupRepository) {
        this.groupRepository = groupRepository;
    }

    @Transactional(readOnly = true)
    public Group findById(Long id) {
        return groupRepository.findById(id)
                .orElseThrow(() -> new GroupNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Group findByIdWithLocations(Long id) {
        return groupRepository.findWithLocationsById(id)
                .orElseThrow(() -> new GroupNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Group findByIdWithOwners(Long id) {
        return groupRepository.findWithOwnersById(id)
                .orElseThrow(() -> new GroupNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Group findByIdWithProviders(Long id) {
        return groupRepository.findWithProvidersById(id)
                .orElseThrow(() -> new GroupNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Page<Group> search(String lbn, Pageable pageable) {
        return groupRepository.findByLbnContainingIgnoreCase(lbn, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Group> findAll(Pageable pageable) {
        return groupRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<Group> findAllForSelect() {
        return groupRepository.findAll(Sort.by("lbn"));
    }

    @Transactional
    public Group create(Group group) {
        return groupRepository.save(group);
    }

    @Transactional
    public Group update(Long id, Consumer<Group> changes) {
        Group group = groupRepository.findById(id)
                .orElseThrow(() -> new GroupNotFoundException(id));
        changes.accept(group);
        return group;
    }

    @Transactional
    public void delete(Long id) {
        Group group = groupRepository.findById(id)
                .orElseThrow(() -> new GroupNotFoundException(id));
        groupRepository.delete(group);
    }
}
