package dev.bryrich.credapp.service;

import dev.bryrich.credapp.entity.Group;
import dev.bryrich.credapp.entity.MalpracticePolicy;
import dev.bryrich.credapp.entity.Provider;
import dev.bryrich.credapp.entity.enums.CoverageScope;
import dev.bryrich.credapp.exception.GroupNotFoundException;
import dev.bryrich.credapp.exception.MalpracticePolicyNotFoundException;
import dev.bryrich.credapp.exception.PolicyOwnerException;
import dev.bryrich.credapp.exception.ProviderNotFoundException;
import dev.bryrich.credapp.repository.GroupRepository;
import dev.bryrich.credapp.repository.MalpracticePolicyRepository;
import dev.bryrich.credapp.repository.ProviderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MalpracticePolicyServiceTest {

    private MalpracticePolicyRepository policyRepository;
    private ProviderRepository providerRepository;
    private GroupRepository groupRepository;
    private MalpracticePolicyService service;

    private final Provider provider = new Provider("Ada", "Byron");
    private final Group group = new Group("Northside Health", "123456789");

    @BeforeEach
    void setUp() {
        policyRepository = mock(MalpracticePolicyRepository.class);
        providerRepository = mock(ProviderRepository.class);
        groupRepository = mock(GroupRepository.class);
        service = new MalpracticePolicyService(policyRepository, providerRepository, groupRepository);
        when(policyRepository.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void addForProviderOwnsThePolicyByProviderAndNotByGroup() {
        when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));

        service.addForProvider(1L, "P-1", "Acme Mutual", "Claims-made",
                LocalDate.of(2026, 1, 1), CoverageScope.INDIVIDUAL);

        ArgumentCaptor<MalpracticePolicy> captor =
                ArgumentCaptor.forClass(MalpracticePolicy.class);
        verify(policyRepository).save(captor.capture());
        MalpracticePolicy saved = captor.getValue();

        assertThat(saved.getProvider()).isSameAs(provider);
        assertThat(saved.getGroup()).isNull();
        assertThat(saved.getSharedIndividual()).isEqualTo(CoverageScope.INDIVIDUAL);
    }

    @Test
    void addForGroupOwnsThePolicyByGroupAndNotByProvider() {
        when(groupRepository.findById(7L)).thenReturn(Optional.of(group));

        service.addForGroup(7L, "P-2", "Acme Mutual", "Occurrence",
                LocalDate.of(2026, 1, 1), CoverageScope.SHARED);

        ArgumentCaptor<MalpracticePolicy> captor =
                ArgumentCaptor.forClass(MalpracticePolicy.class);
        verify(policyRepository).save(captor.capture());
        MalpracticePolicy saved = captor.getValue();

        assertThat(saved.getGroup()).isSameAs(group);
        assertThat(saved.getProvider()).isNull();
    }

    @Test
    void addForProviderThrowsWhenTheProviderIsMissing() {
        when(providerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addForProvider(99L, "P-1", "Acme", "Claims-made",
                LocalDate.now(), CoverageScope.INDIVIDUAL))
                .isInstanceOf(ProviderNotFoundException.class);

        verify(policyRepository, never()).save(any());
    }

    @Test
    void addForGroupThrowsWhenTheGroupIsMissing() {
        when(groupRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addForGroup(99L, "P-1", "Acme", "Occurrence",
                LocalDate.now(), CoverageScope.SHARED))
                .isInstanceOf(GroupNotFoundException.class);
    }

    @Test
    void requireExactlyOneOwnerRejectsBothAndNeither() {
        assertThatThrownBy(() -> service.requireExactlyOneOwner(1L, 7L))
                .isInstanceOf(PolicyOwnerException.class);

        assertThatThrownBy(() -> service.requireExactlyOneOwner(null, null))
                .isInstanceOf(PolicyOwnerException.class);
    }

    @Test
    void requireExactlyOneOwnerAcceptsEitherOneAlone() {
        assertThatCode(() -> service.requireExactlyOneOwner(1L, null)).doesNotThrowAnyException();
        assertThatCode(() -> service.requireExactlyOneOwner(null, 7L)).doesNotThrowAnyException();
    }

    @Test
    void findExpiringSoonRejectsAWindowSmallerThanOneDay() {
        assertThatThrownBy(() -> service.findExpiringSoon(0))
                .isInstanceOf(IllegalArgumentException.class);

        verify(policyRepository, never()).findExpiringBetween(any(), any());
    }

    @Test
    void findExpiringSoonQueriesFromTodayToTheEndOfTheWindow() {
        when(policyRepository.findExpiringBetween(any(), any())).thenReturn(List.of());

        service.findExpiringSoon(30);

        verify(policyRepository).findExpiringBetween(LocalDate.now(), LocalDate.now().plusDays(30));
    }

    @Test
    void findByIdThrowsWhenThePolicyIsMissing() {
        when(policyRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(404L))
                .isInstanceOf(MalpracticePolicyNotFoundException.class);
    }
}
