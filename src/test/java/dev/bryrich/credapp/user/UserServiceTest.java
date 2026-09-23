package dev.bryrich.credapp.user;

import dev.bryrich.credapp.usergroup.UserGroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private UserService userService;
    private UserGroupRepository userGroups;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        userGroups = mock(UserGroupRepository.class);
        when(userGroups.existsById(42L)).thenReturn(true);
        userService = new UserService(userRepository, passwordEncoder, userGroups);
    }

    @Test
    void createHashesThePasswordAndNormalizesTheEmail() {
        when(userRepository.existsByEmail("bryson@example.com")).thenReturn(false);
        when(passwordEncoder.encode("averylongpassword")).thenReturn("hashed-value");
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

        userService.createInGroup("  Bryson@Example.COM  ", "averylongpassword", "Bryson R", Role.ADMIN, 42L);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertThat(saved.getEmail()).isEqualTo("bryson@example.com");
        assertThat(saved.getPasswordHash()).isEqualTo("hashed-value");
        assertThat(saved.getPasswordHash()).isNotEqualTo("averylongpassword");
        assertThat(saved.getFullName()).isEqualTo("Bryson R");
        assertThat(saved.getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.getUserGroupId()).isEqualTo(42L);
    }

    @Test
    void createKeepsTheDefaultRoleWhenNoneIsGiven() {
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("averylongpassword")).thenReturn("hashed-value");
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

        userService.createInGroup("new@example.com", "averylongpassword", "New Person", null, 42L);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        assertThat(captor.getValue().getRole()).isEqualTo(Role.COORDINATOR);
    }

    @Test
    void createRejectsAnEmailThatIsAlreadyTaken() {
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.createInGroup(
                "Taken@Example.com", "averylongpassword", "Someone", Role.COORDINATOR, 42L))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void findByEmailNormalizesBeforeQuerying() {
        User user = new User("bryson@example.com", "hashed-value", 42L);
        when(userRepository.findByEmail("bryson@example.com")).thenReturn(Optional.of(user));

        Optional<User> found = userService.findByEmail("  BRYSON@Example.com ");

        assertThat(found).contains(user);
        verify(userRepository).findByEmail("bryson@example.com");
    }

    @Test
    void findByIdThrowsWhenTheUserIsMissing() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(99L))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void creatingAnAccountRequiresAnExplicitExistingGroup() {
        for (Long groupId : java.util.Arrays.asList(null, 0L, -1L, 999L)) {
            assertThatThrownBy(() -> userService.createInGroup(
                    "new@example.com", "averylongpassword", "New Person", Role.COORDINATOR, groupId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("An existing user group is required");
        }
        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    void userCannotBeConstructedOrAssignedWithoutAGroup() {
        for (Long groupId : java.util.Arrays.asList(null, 0L, -1L)) {
            assertThatThrownBy(() -> new User("new@example.com", "hash", groupId))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        User user = new User("new@example.com", "hash", 42L);
        assertThatThrownBy(() -> user.setUserGroupId(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(user.getUserGroupId()).isEqualTo(42L);
    }
}
