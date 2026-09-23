package dev.bryrich.credapp.user;

import dev.bryrich.credapp.security.CredAppUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;
    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{id}")
    public UserResponse getById(@AuthenticationPrincipal CredAppUserDetails principal, @PathVariable Long id) {
        return UserResponse.from(userService.findByIdAs(principal.getUser(), id));
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@AuthenticationPrincipal CredAppUserDetails principal,
                                             @Valid @RequestBody CreateUserRequest request) {
        User saved = userService.createAs(principal.getUser(),
                request.email(),
                request.password(),
                request.fullName(),
                request.role());

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(saved.getId())
                .toUri();
        return ResponseEntity.created(location).body(UserResponse.from(saved));
    }

}
