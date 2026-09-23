package dev.bryrich.credapp.ssn;

import dev.bryrich.credapp.owner.Owner;
import dev.bryrich.credapp.owner.OwnerNotFoundException;
import dev.bryrich.credapp.owner.OwnerRepository;
import dev.bryrich.credapp.provider.Provider;
import dev.bryrich.credapp.provider.ProviderRepository;
import dev.bryrich.credapp.user.Role;
import dev.bryrich.credapp.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reading a stored SSN has to leave a trail. These check the role gate and that the audit
 * row is written even when there is nothing on file to return.
 */
class SsnAccessServiceTest {

    private SsnAccessLogRepository logRepository;
    private OwnerRepository ownerRepository;
    private ProviderRepository providerRepository;
    private SsnAccessService service;

    @BeforeEach
    void setUp() {
        logRepository = mock(SsnAccessLogRepository.class);
        ownerRepository = mock(OwnerRepository.class);
        providerRepository = mock(ProviderRepository.class);
        service = new SsnAccessService(logRepository, ownerRepository, providerRepository);
    }

    private User actor(Role role) {
        User user = new User("someone@credapp.local", "hashed-value", 42L);
        user.setRole(role);
        ReflectionTestUtils.setField(user, "id", 3L);
        return user;
    }

    private Owner ownerWithSsn(String ssn) {
        Owner owner = new Owner("Nia", "Okoro");
        owner.setSsn(ssn);
        return owner;
    }

    @Test
    void aReadOnlyAccountCanRevealAnSsnAndTheAccessIsLogged() {
        when(ownerRepository.findById(2L)).thenReturn(Optional.of(ownerWithSsn("123456789")));
        assertThat(service.revealOwnerSsn(actor(Role.READONLY), 2L, "10.0.0.1")).isEqualTo("123456789");
        verify(logRepository).save(any(SsnAccessLog.class));
    }

    @Test
    void anAbsentActorCannotRevealAnSsn() {
        assertThatThrownBy(() -> service.revealOwnerSsn(null, 2L, "10.0.0.1"))
                .isInstanceOf(SsnAccessDeniedException.class);

        verify(logRepository, never()).save(any());
    }

    @Test
    void aCoordinatorCanRevealAndTheAccessIsLogged() {
        when(ownerRepository.findById(2L)).thenReturn(Optional.of(ownerWithSsn("123456789")));

        String ssn = service.revealOwnerSsn(actor(Role.COORDINATOR), 2L, "10.0.0.1");

        assertThat(ssn).isEqualTo("123456789");

        ArgumentCaptor<SsnAccessLog> captor = ArgumentCaptor.forClass(SsnAccessLog.class);
        verify(logRepository).save(captor.capture());
        SsnAccessLog entry = captor.getValue();

        assertThat(entry.getSubjectType()).isEqualTo(SsnSubjectType.OWNER);
        assertThat(entry.getSubjectId()).isEqualTo(2L);
        assertThat(entry.getSubjectName()).isEqualTo("Nia Okoro");
        assertThat(entry.getUserId()).isEqualTo(3L);
        assertThat(entry.getUserEmail()).isEqualTo("someone@credapp.local");
        assertThat(entry.getIpAddress()).isEqualTo("10.0.0.1");
    }

    @Test
    void lookingAtARecordWithNothingOnFileIsStillLogged() {
        when(ownerRepository.findById(2L)).thenReturn(Optional.of(ownerWithSsn(null)));

        assertThat(service.revealOwnerSsn(actor(Role.ADMIN), 2L, "10.0.0.1")).isNull();

        verify(logRepository).save(any(SsnAccessLog.class));
    }

    @Test
    void aMissingOwnerIsReportedWithoutWritingAnAuditRow() {
        when(ownerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revealOwnerSsn(actor(Role.ADMIN), 99L, "10.0.0.1"))
                .isInstanceOf(OwnerNotFoundException.class);

        verify(logRepository, never()).save(any());
    }

    @Test
    void revealingAProviderSsnLogsAgainstTheProviderSubject() {
        Provider provider = new Provider("Ada", "Byron");
        provider.setSsn("987654321");
        when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));

        service.revealProviderSsn(actor(Role.SUPERUSER), 1L, "10.0.0.2");

        ArgumentCaptor<SsnAccessLog> captor = ArgumentCaptor.forClass(SsnAccessLog.class);
        verify(logRepository).save(captor.capture());

        assertThat(captor.getValue().getSubjectType()).isEqualTo(SsnSubjectType.PROVIDER);
        assertThat(captor.getValue().getSubjectName()).isEqualTo("Ada Byron");
    }

    @Test
    void onFileChecksDoNotDecryptAnything() {
        when(ownerRepository.hasSsn(2L)).thenReturn(true);

        assertThat(service.ownerSsnOnFile(2L)).isTrue();

        verify(ownerRepository, never()).findById(any());
        verify(logRepository, never()).save(any());
    }
}
