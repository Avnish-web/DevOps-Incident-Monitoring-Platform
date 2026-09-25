package dev.monitoring.api.incident;

import dev.monitoring.api.incident.IncidentResponse.IncidentStatus;
import dev.monitoring.api.security.CurrentUser;
import dev.monitoring.api.web.NotFoundException;
import dev.monitoring.api.web.PageResponse;
import dev.monitoring.common.domain.Incident;
import dev.monitoring.common.domain.Monitor;
import dev.monitoring.common.repository.IncidentRepository;
import dev.monitoring.common.repository.MonitorRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only access to incidents. Incidents are created and resolved by the worker. */
@Service
@Transactional(readOnly = true)
public class IncidentService {

    private final IncidentRepository incidents;
    private final MonitorRepository monitors;
    private final CurrentUser currentUser;

    public IncidentService(IncidentRepository incidents, MonitorRepository monitors,
                           CurrentUser currentUser) {
        this.incidents = incidents;
        this.monitors = monitors;
        this.currentUser = currentUser;
    }

    public PageResponse<IncidentResponse> list(IncidentStatus status, UUID monitorId,
                                               Pageable pageable) {
        Page<Incident> page = incidents.findAll(filter(status, monitorId, currentUser.id()), pageable);
        Map<UUID, String> names = monitorNames(
                page.getContent().stream().map(Incident::getMonitorId).collect(Collectors.toSet()));
        Instant now = Instant.now();
        return PageResponse.of(page,
                i -> IncidentResponse.from(i, names.get(i.getMonitorId()), now));
    }

    public IncidentResponse get(UUID id) {
        Incident incident = incidents.findById(id)
                .filter(i -> monitors.existsByIdAndOwnerId(i.getMonitorId(), currentUser.id()))
                .orElseThrow(() -> new NotFoundException("Incident"));
        String name = monitors.findById(incident.getMonitorId()).map(Monitor::getName).orElse(null);
        return IncidentResponse.from(incident, name, Instant.now());
    }

    private static Specification<Incident> filter(IncidentStatus status, UUID monitorId, UUID ownerId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // Only incidents of monitors owned by the current user.
            Subquery<UUID> owned = query.subquery(UUID.class);
            Root<Monitor> monitor = owned.from(Monitor.class);
            owned.select(monitor.get("id")).where(cb.equal(monitor.get("ownerId"), ownerId));
            predicates.add(root.get("monitorId").in(owned));
            if (monitorId != null) {
                predicates.add(cb.equal(root.get("monitorId"), monitorId));
            }
            if (status == IncidentStatus.OPEN) {
                predicates.add(cb.isNull(root.get("resolvedAt")));
            } else if (status == IncidentStatus.RESOLVED) {
                predicates.add(cb.isNotNull(root.get("resolvedAt")));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** One query for all monitor names on the page (avoids N+1 lookups). */
    private Map<UUID, String> monitorNames(Set<UUID> ids) {
        return monitors.findAllById(ids).stream()
                .collect(Collectors.toMap(Monitor::getId, Monitor::getName, (a, b) -> a));
    }
}
