package dev.monitoring.common.repository;

import dev.monitoring.common.domain.CheckResult;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckResultRepository extends JpaRepository<CheckResult, Long> {
}
