package dev.monitoring.worker.check;

import dev.monitoring.worker.scheduling.ClaimedMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Placeholder that only logs the dispatch. Replaced by the HTTP check (with response-time
 * measurement and result recording) in Phase 6. It deliberately records nothing, so no
 * fabricated results reach the database.
 */
@Component
public class LoggingCheckRunner implements CheckRunner {

    private static final Logger log = LoggerFactory.getLogger(LoggingCheckRunner.class);

    @Override
    public void run(ClaimedMonitor monitor) {
        log.info("Check dispatched (execution not implemented yet): method={} timeoutMs={}",
                monitor.httpMethod(), monitor.timeoutMs());
    }
}
