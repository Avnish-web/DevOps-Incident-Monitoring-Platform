package dev.monitoring.worker.check;

import dev.monitoring.worker.scheduling.ClaimedMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/** Runs the HTTP check for a claimed monitor and records the outcome. */
@Component
public class HttpCheckRunner implements CheckRunner {

    private static final Logger log = LoggerFactory.getLogger(HttpCheckRunner.class);

    private final HttpChecker checker;
    private final CheckResultRecorder recorder;

    public HttpCheckRunner(HttpChecker checker, CheckResultRecorder recorder) {
        this.checker = checker;
        this.recorder = recorder;
    }

    @Override
    public void run(ClaimedMonitor monitor) {
        CheckOutcome outcome = checker.check(monitor);
        if (outcome.success()) {
            log.debug("Check succeeded: status={} latencyMs={}",
                    outcome.statusCode(), outcome.latencyMs());
        } else {
            log.info("Check failed: errorType={} status={} latencyMs={} message={}",
                    outcome.errorType(), outcome.statusCode(), outcome.latencyMs(),
                    outcome.errorMessage());
        }
        try {
            recorder.record(monitor, outcome);
        } catch (DataIntegrityViolationException e) {
            log.info("Monitor was deleted during the check; result discarded");
        } catch (DataAccessException e) {
            // Database unavailable: this result is lost, the next check runs on schedule.
            log.warn("Could not record check result: {}", e.toString());
        }
    }
}
