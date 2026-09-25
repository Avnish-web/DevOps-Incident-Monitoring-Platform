package dev.monitoring.common.repository;

import dev.monitoring.common.domain.AlertChannel;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertChannelRepository extends JpaRepository<AlertChannel, UUID> {

    Optional<AlertChannel> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<AlertChannel> findAllByOwnerId(UUID ownerId, Sort sort);

    long countByOwnerId(UUID ownerId);
}
