package dev.monitoring.common.repository;

import dev.monitoring.common.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** Callers pass an already-normalized (lower-case, trimmed) address. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
