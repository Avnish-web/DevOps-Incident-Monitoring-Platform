package dev.monitoring.common.repository;

import dev.monitoring.common.domain.Incident;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentRepository extends JpaRepository<Incident, UUID> {
}
