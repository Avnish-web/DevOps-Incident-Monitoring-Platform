package dev.monitoring.worker.check;

import dev.monitoring.worker.scheduling.ClaimedMonitor;

/**
 * Executes one check for a claimed monitor. Implementations must enforce their own timeout,
 * must not throw for expected failures, and are called concurrently from the check pool.
 */
public interface CheckRunner {

    void run(ClaimedMonitor monitor);
}
