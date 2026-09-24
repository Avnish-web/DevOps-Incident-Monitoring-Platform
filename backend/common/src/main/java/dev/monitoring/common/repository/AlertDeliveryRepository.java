package dev.monitoring.common.repository;

import dev.monitoring.common.domain.AlertDelivery;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertDeliveryRepository extends JpaRepository<AlertDelivery, Long> {

    Page<AlertDelivery> findByChannelId(UUID channelId, Pageable pageable);
}
