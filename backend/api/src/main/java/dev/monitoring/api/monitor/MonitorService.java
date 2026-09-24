package dev.monitoring.api.monitor;

import dev.monitoring.api.web.PageResponse;
import dev.monitoring.api.web.PreconditionFailedException;
import dev.monitoring.common.domain.Monitor;
import dev.monitoring.common.domain.MonitorType;
import dev.monitoring.common.net.TargetUrlValidator;
import dev.monitoring.common.repository.MonitorRepository;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MonitorService {

    private static final Logger log = LoggerFactory.getLogger(MonitorService.class);

    private final MonitorRepository monitors;
    private final TargetUrlValidator urlValidator;

    public MonitorService(MonitorRepository monitors, TargetUrlValidator urlValidator) {
        this.monitors = monitors;
        this.urlValidator = urlValidator;
    }

    public MonitorResponse create(MonitorRequest request) {
        urlValidator.validate(request.url());
        Monitor monitor = new Monitor(request.name().strip(), MonitorType.HTTP, request.url(),
                request.intervalSecondsOrDefault(), request.timeoutMsOrDefault());
        applySettings(monitor, request);
        Monitor saved = monitors.saveAndFlush(monitor);
        log.info("Created monitor {}", saved.getId());
        return MonitorResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public MonitorResponse get(UUID id) {
        return MonitorResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<MonitorResponse> list(Pageable pageable) {
        return PageResponse.of(monitors.findAll(pageable), MonitorResponse::from);
    }

    /**
     * Replaces a monitor's configuration.
     *
     * @param expectedVersion version from the client's {@code If-Match} header, or null to skip
     *                        the precondition. Concurrent writes are still caught by optimistic
     *                        locking at flush time (HTTP 409).
     */
    public MonitorResponse update(UUID id, MonitorRequest request, Long expectedVersion) {
        Monitor monitor = find(id);
        if (expectedVersion != null && expectedVersion != monitor.getVersion()) {
            throw new PreconditionFailedException(
                    "Monitor was modified by someone else; reload it and try again");
        }
        urlValidator.validate(request.url());

        boolean wasEnabled = monitor.isEnabled();
        boolean checkChanged = !monitor.getUrl().equals(request.url())
                || monitor.getHttpMethod() != request.httpMethodOrDefault()
                || monitor.getTimeoutMs() != request.timeoutMsOrDefault()
                || !Objects.equals(monitor.getExpectedStatus(), request.expectedStatus());

        monitor.setName(request.name().strip());
        monitor.setUrl(request.url());
        monitor.setIntervalSeconds(request.intervalSecondsOrDefault());
        monitor.setTimeoutMs(request.timeoutMsOrDefault());
        applySettings(monitor, request);

        // Check soon so the user sees the effect of a changed target or a resumed monitor.
        if (monitor.isEnabled() && (checkChanged || !wasEnabled)) {
            monitor.setNextCheckAt(Instant.now());
        }
        Monitor saved = monitors.saveAndFlush(monitor);
        log.info("Updated monitor {}", id);
        return MonitorResponse.from(saved);
    }

    public void delete(UUID id) {
        monitors.delete(find(id));
        log.info("Deleted monitor {}", id);
    }

    private Monitor find(UUID id) {
        return monitors.findById(id).orElseThrow(() -> new MonitorNotFoundException(id));
    }

    private static void applySettings(Monitor monitor, MonitorRequest request) {
        monitor.setHttpMethod(request.httpMethodOrDefault());
        monitor.setExpectedStatus(request.expectedStatus());
        monitor.setFailureThreshold(request.failureThresholdOrDefault());
        monitor.setRecoveryThreshold(request.recoveryThresholdOrDefault());
        monitor.setEnabled(request.enabledOrDefault());
    }
}
