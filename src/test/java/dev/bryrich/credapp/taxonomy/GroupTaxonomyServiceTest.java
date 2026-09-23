package dev.bryrich.credapp.taxonomy;

import dev.bryrich.credapp.group.Group;
import dev.bryrich.credapp.group.GroupNotFoundException;
import dev.bryrich.credapp.group.GroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GroupTaxonomyServiceTest {

    private GroupTaxonomyRepository groupTaxonomyRepository;
    private GroupRepository groupRepository;
    private TaxonomyRepository taxonomyRepository;
    private GroupTaxonomyService service;

    private final Group group = new Group("Northside Health", "123456789");
    private final Taxonomy familyMedicine = new Taxonomy("207Q00000X", "Family Medicine", null);

    @BeforeEach
    void setUp() {
        groupTaxonomyRepository = mock(GroupTaxonomyRepository.class);
        groupRepository = mock(GroupRepository.class);
        taxonomyRepository = mock(TaxonomyRepository.class);
        service = new GroupTaxonomyService(groupTaxonomyRepository, groupRepository, taxonomyRepository);
    }

    @Test
    void findByGroupIdThrowsWhenTheGroupDoesNotExist() {
        when(groupRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.findByGroupId(99L))
                .isInstanceOf(GroupNotFoundException.class);

        verify(groupTaxonomyRepository, never()).findByGroupIdWithTaxonomy(anyLong());
    }

    @Test
    void assigningAPrimaryClearsTheExistingPrimaryFirst() {
        when(groupRepository.findById(7L)).thenReturn(Optional.of(group));
        when(taxonomyRepository.findById("207Q00000X")).thenReturn(Optional.of(familyMedicine));
        when(groupTaxonomyRepository.findByGroupId(7L)).thenReturn(List.of());
        when(groupTaxonomyRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        service.assign(7L, "207Q00000X", true);

        var order = inOrder(groupTaxonomyRepository);
        order.verify(groupTaxonomyRepository).clearPrimaryForGroup(7L);
        order.verify(groupTaxonomyRepository).save(any());
    }

    @Test
    void assigningACodeTheGroupAlreadyHasUpdatesTheFlagInPlace() {
        GroupTaxonomy existing = new GroupTaxonomy(group, familyMedicine, false);
        when(groupRepository.findById(7L)).thenReturn(Optional.of(group));
        when(taxonomyRepository.findById("207Q00000X")).thenReturn(Optional.of(familyMedicine));
        when(groupTaxonomyRepository.findByGroupId(7L)).thenReturn(List.of(existing));

        GroupTaxonomy result = service.assign(7L, "207Q00000X", true);

        assertThat(result).isSameAs(existing);
        assertThat(result.isPrimary()).isTrue();
        verify(groupTaxonomyRepository, never()).save(any());
    }

    @Test
    void unassignThrowsWhenTheGroupDoesNotHaveThatCode() {
        when(groupTaxonomyRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unassign(7L, "207Q00000X"))
                .isInstanceOf(GroupTaxonomyNotFoundException.class);
    }
}
