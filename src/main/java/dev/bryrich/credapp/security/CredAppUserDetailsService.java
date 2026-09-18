package dev.bryrich.credapp.security;

import dev.bryrich.credapp.entity.User;
import dev.bryrich.credapp.service.UserService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CredAppUserDetailsService implements UserDetailsService {

    private final UserService userService;

    public CredAppUserDetailsService(UserService userService) {
        this.userService = userService;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        User user = userService.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No user with email " + email));
        return new CredAppUserDetails(user);
    }
}