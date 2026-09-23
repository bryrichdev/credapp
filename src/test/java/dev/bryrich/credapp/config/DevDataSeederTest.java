package dev.bryrich.credapp.config;

import dev.bryrich.credapp.user.*;
import dev.bryrich.credapp.usergroup.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DevDataSeederTest {
    private final UserService users = mock(UserService.class);
    private final UserGroupRepository groups = mock(UserGroupRepository.class);

    @Test
    void freshDevDatabaseDoesNotCreateAnAccountOrGroupWithoutAnExplicitName() {
        when(users.findByEmail("dev@example.com")).thenReturn(Optional.empty());
        new DevDataSeeder(users, groups, "dev@example.com", "long-test-password", "").run();
        verifyNoInteractions(groups);
        verify(users, never()).createInGroup(any(), any(), any(), any(), any());
    }

    @Test
    void explicitDevGroupIsAssignedToTheBootstrappedAccount() {
        when(users.findByEmail("dev@example.com")).thenReturn(Optional.empty());
        UserGroup saved = new UserGroup("Clinic team");
        ReflectionTestUtils.setField(saved, "id", 84L);
        when(groups.save(any(UserGroup.class))).thenReturn(saved);
        new DevDataSeeder(users, groups, "dev@example.com", "long-test-password", "Clinic team").run();
        verify(groups).save(argThat(group -> group.getName().equals("Clinic team")));
        verify(users).createInGroup("dev@example.com", "long-test-password", "Dev Superuser", Role.SUPERUSER, 84L);
    }

    @Test
    void existingDevAccountKeepsItsGroup() {
        User account = new User("dev@example.com", "hash", 84L);
        account.setRole(Role.SUPERUSER);
        when(users.findByEmail("dev@example.com")).thenReturn(Optional.of(account));
        new DevDataSeeder(users, groups, "dev@example.com", "long-test-password", "Another name").run();
        verifyNoInteractions(groups);
        verify(users, never()).createInGroup(any(), any(), any(), any(), any());
    }
}
