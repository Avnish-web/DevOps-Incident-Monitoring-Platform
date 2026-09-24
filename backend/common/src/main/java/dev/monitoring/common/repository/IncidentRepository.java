package dev.monitoring.common.repository;

import dev.monitoring.common.domain.Incident;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface IncidentRepository extends JpaRepository<Incident, UUID>,
        JpaSpecificationExecutor<Incident> {

    Optional<Incident> findByMonitorIdAndResolvedAtIsNull(UUID monitorId);
}
