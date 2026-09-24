package dev.monitoring.common.repository;

import dev.monitoring.common.domain.AlertChannel;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertChannelRepository extends JpaRepository<AlertChannel, UUID> {
}
