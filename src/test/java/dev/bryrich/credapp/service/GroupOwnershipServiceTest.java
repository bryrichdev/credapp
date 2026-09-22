package dev.bryrich.credapp.service;

import dev.bryrich.credapp.entity.Group;
import dev.bryrich.credapp.entity.GroupOwner;
import dev.bryrich.credapp.entity.Owner;
import dev.bryrich.credapp.exception.GroupNotFoundException;
import dev.bryrich.credapp.exception.OwnerNotFoundException;
import dev.bryrich.credapp.exception.OwnershipPercentExceededException;
import dev.bryrich.credapp.repository.GroupOwnerRelationRepository;
import dev.bryrich.credapp.repository.GroupOwnerRepository;
import dev.bryrich.credapp.repository.GroupRepository;
import dev.bryrich.credapp.repository.OwnerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A CHECK constraint can only see one row, so the 100% ceiling is enforced here. These
 * cover the arithmetic around that ceiling.
 */
class GroupOwnershipServiceTest {

    private GroupOwnerRepository groupOwnerRepository;
    private GroupOwnerRelationRepository relationRepository;
    private GroupRepository groupRepository;
    private OwnerRepository ownerRepository;
    private GroupOwnershipService service;

    private final Group group = new Group("Northside Health", "123456789");
    private final Owner owner = new Owner("Nia", "Okoro");

    @BeforeEach
    void setUp() {
        groupOwnerRepository = mock(GroupOwnerRepository.class);
        relationRepository = mock(GroupOwnerRelationRepository.class);
        groupRepository = mock(GroupRepository.class);
        ownerRepository = mock(OwnerRepository.class);
        service = new GroupOwnershipService(groupOwnerRepository, relationRepository,
                groupRepository, ownerRepository);
    }

    private void groupAndOwnerExist() {
        when(groupRepository.findById(7L)).thenReturn(Optional.of(group));
        when(ownerRepository.findById(2L)).thenReturn(Optional.of(owner));
        when(groupOwnerRepository.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void remainingPercentIsWhatIsLeftOfTheHundred() {
        when(groupOwnerRepository.sumPercentOwnedByGroupId(7L)).thenReturn(new BigDecimal("60.00"));

        assertThat(service.remainingPercent(7L)).isEqualByComparingTo("40.00");
    }

    @Test
    void addingAStakeThatFitsIsAccepted() {
        groupAndOwnerExist();
        when(groupOwnerRepository.sumPercentOwnedByGroupId(7L)).thenReturn(new BigDecimal("60.00"));

        GroupOwner saved = service.addOwner(7L, 2L, new BigDecimal("40.00"), null);

        assertThat(saved.getPercentOwned()).isEqualByComparingTo("40.00");
    }

    @Test
    void addingAStakeThatPushesPastAHundredIsRejected() {
        groupAndOwnerExist();
        when(groupOwnerRepository.sumPercentOwnedByGroupId(7L)).thenReturn(new BigDecimal("60.00"));

        assertThatThrownBy(() -> service.addOwner(7L, 2L, new BigDecimal("40.01"), null))
                .isInstanceOf(OwnershipPercentExceededException.class);

        verify(groupOwnerRepository, never()).save(any());
    }

    @Test
    void anUnrecordedStakeSkipsTheCeilingCheck() {
        groupAndOwnerExist();
        when(groupOwnerRepository.sumPercentOwnedByGroupId(7L)).thenReturn(new BigDecimal("100.00"));

        assertThatCode(() -> service.addOwner(7L, 2L, null, null)).doesNotThrowAnyException();
    }

    @Test
    void updatingAStakeIgnoresTheOwnersExistingShare() {
        GroupOwner existing = new GroupOwner(group, owner, new BigDecimal("50.00"), null);
        when(groupOwnerRepository.findByGroupIdAndOwnerId(7L, 2L)).thenReturn(Optional.of(existing));
        when(groupOwnerRepository.sumPercentOwnedByGroupIdExcluding(7L, 2L))
                .thenReturn(new BigDecimal("30.00"));

        GroupOwner updated = service.updateOwner(7L, 2L, new BigDecimal("70.00"), null);

        assertThat(updated.getPercentOwned()).isEqualByComparingTo("70.00");
    }

    @Test
    void updatingAStakeStillRespectsTheCeiling() {
        GroupOwner existing = new GroupOwner(group, owner, new BigDecimal("50.00"), null);
        when(groupOwnerRepository.findByGroupIdAndOwnerId(7L, 2L)).thenReturn(Optional.of(existing));
        when(groupOwnerRepository.sumPercentOwnedByGroupIdExcluding(7L, 2L))
                .thenReturn(new BigDecimal("30.00"));

        assertThatThrownBy(() -> service.updateOwner(7L, 2L, new BigDecimal("70.01"), null))
                .isInstanceOf(OwnershipPercentExceededException.class);
    }

    @Test
    void addOwnerThrowsWhenTheGroupIsMissing() {
        when(groupRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addOwner(99L, 2L, new BigDecimal("10"), null))
                .isInstanceOf(GroupNotFoundException.class);
    }

    @Test
    void addOwnerThrowsWhenThePersonIsMissing() {
        when(groupRepository.findById(7L)).thenReturn(Optional.of(group));
        when(ownerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addOwner(7L, 99L, new BigDecimal("10"), null))
                .isInstanceOf(OwnerNotFoundException.class);
    }

    @Test
    void findOwnersThrowsWhenTheGroupDoesNotExist() {
        when(groupRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.findOwners(99L))
                .isInstanceOf(GroupNotFoundException.class);

        verify(groupOwnerRepository, never()).findByGroupIdWithOwner(anyLong());
    }

    @Test
    void removeOwnerThrowsWhenTheyAreNotInThatGroup() {
        when(groupOwnerRepository.findByGroupIdAndOwnerId(7L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeOwner(7L, 2L))
                .isInstanceOf(OwnerNotFoundException.class);

        verify(groupOwnerRepository, never()).delete(any());
    }
}
