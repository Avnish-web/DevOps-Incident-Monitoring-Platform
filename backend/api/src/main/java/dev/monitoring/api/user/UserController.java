package dev.monitoring.api.user;

import dev.monitoring.common.domain.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** User administration. Restricted to ADMIN by the security filter chain. */
@RestController
@RequestMapping(path = "/api/v1/users", produces = MediaType.APPLICATION_JSON_VALUE)
public class UserController {

    public record CreateUserRequest(@NotBlank @Size(max = 254) String email,
                                    @NotBlank @Size(max = 128) String password,
                                    @NotNull User.Role role) {
    }

    public record UserResponse(UUID id, String email, User.Role role, boolean enabled,
                               Instant createdAt, Instant lastLoginAt) {
        static UserResponse from(User u) {
            return new UserResponse(u.getId(), u.getEmail(), u.getRole(), u.isEnabled(),
                    u.getCreatedAt(), u.getLastLoginAt());
        }
    }

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping
    public List<UserResponse> list() {
        return service.list().stream().map(UserResponse::from).toList();
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        User user = service.create(request.email(), request.password(), request.role());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(user.getId()).toUri();
        return ResponseEntity.created(location).body(UserResponse.from(user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
