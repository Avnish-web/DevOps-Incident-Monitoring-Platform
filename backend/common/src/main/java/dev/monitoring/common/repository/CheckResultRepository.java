package dev.monitoring.common.repository;

import dev.monitoring.common.domain.CheckResult;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckResultRepository extends JpaRepository<CheckResult, Long> {

    /** Served by the (monitor_id, checked_at DESC) index. */
    Page<CheckResult> findByMonitorId(UUID monitorId, Pageable pageable);
}
