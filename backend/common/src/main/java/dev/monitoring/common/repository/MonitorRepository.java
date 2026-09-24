package dev.monitoring.common.repository;

import dev.monitoring.common.domain.Monitor;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MonitorRepository extends JpaRepository<Monitor, UUID> {

    /**
     * Loads a monitor with a row lock ({@code SELECT ... FOR UPDATE}). Used when changing
     * incident state, so the API and the worker never interleave state updates.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Monitor m WHERE m.id = :id")
    Optional<Monitor> findByIdForUpdate(@Param("id") UUID id);
}
