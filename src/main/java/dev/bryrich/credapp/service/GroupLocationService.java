package dev.bryrich.credapp.service;

import dev.bryrich.credapp.entity.Group;
import dev.bryrich.credapp.entity.GroupLocation;
import dev.bryrich.credapp.exception.GroupLocationNotFoundException;
import dev.bryrich.credapp.exception.GroupNotFoundException;
import dev.bryrich.credapp.repository.GroupLocationRepository;
import dev.bryrich.credapp.repository.GroupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Consumer;

@Service
public class GroupLocationService {

    private final GroupLocationRepository locationRepository;
    private final GroupRepository groupRepository;

    public GroupLocationService(GroupLocationRepository locationRepository,
                                GroupRepository groupRepository) {
        this.locationRepository = locationRepository;
        this.groupRepository = groupRepository;
    }

    @Transactional(readOnly = true)
    public List<GroupLocation> findByGroupId(Long groupId) {
        if (!groupRepository.existsById(groupId)) {
            throw new GroupNotFoundException(groupId);
        }
        return locationRepository.findByGroupId(groupId);
    }

    @Transactional(readOnly = true)
    public GroupLocation findByIdAndGroupId(Long id, Long groupId) {
        return locationRepository.findByIdAndGroupId(id, groupId)
                .orElseThrow(() -> new GroupLocationNotFoundException(id, groupId));
    }

    @Transactional
    public GroupLocation addLocation(Long groupId, GroupLocation location) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException(groupId));
        group.addLocation(location);
        return locationRepository.save(location);
    }

    @Transactional
    public GroupLocation update(Long id, Long groupId, Consumer<GroupLocation> changes) {
        GroupLocation location = locationRepository.findByIdAndGroupId(id, groupId)
                .orElseThrow(() -> new GroupLocationNotFoundException(id, groupId));
        changes.accept(location);
        return location;
    }

    @Transactional
    public void delete(Long id, Long groupId) {
        GroupLocation location = locationRepository.findByIdAndGroupId(id, groupId)
                .orElseThrow(() -> new GroupLocationNotFoundException(id, groupId));
        locationRepository.delete(location);
    }
}
