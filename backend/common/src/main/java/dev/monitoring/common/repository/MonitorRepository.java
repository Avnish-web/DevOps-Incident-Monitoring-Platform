package dev.monitoring.common.repository;

import dev.monitoring.common.domain.Monitor;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonitorRepository extends JpaRepository<Monitor, UUID> {
}
