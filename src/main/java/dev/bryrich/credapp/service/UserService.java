package dev.bryrich.credapp.service;

import dev.bryrich.credapp.entity.Role;
import dev.bryrich.credapp.entity.User;
import dev.bryrich.credapp.exception.EmailAlreadyExistsException;
import dev.bryrich.credapp.exception.UserNotFoundException;
import dev.bryrich.credapp.repository.UserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {
    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,  PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(User.normalizeEmail(email));
    }

    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(User.normalizeEmail(email));
    }

    @Transactional(readOnly = true)
    public List<User> findAllByRole(Role role) {
        return userRepository.findAllByRole(role);
    }

    @Transactional
    public User create(String email, String password, String fullName, Role role) {
        String normalized = User.normalizeEmail(email);
        if (userRepository.existsByEmail(normalized)) {
            throw new EmailAlreadyExistsException(normalized);
        }

        if (password == null || password.length() < 12) {
            throw new IllegalArgumentException("password must be at least 12 characters");
        }

        User user = new User(normalized, passwordEncoder.encode(password));
        user.setFullName(fullName);
        if (role != null) {
            user.setRole(role);
        }
        return userRepository.save(user);
    }
}
