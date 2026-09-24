package dev.monitoring.worker.scheduling;

import dev.monitoring.worker.check.CheckRunner;
import dev.monitoring.worker.config.WorkerProperties;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.stereotype.Component;

/**
 * Polls for due monitors and runs their checks on a bounded pool.
 *
 * <p>Backpressure: a semaphore with one permit per pool thread limits how many monitors are
 * claimed. A busy worker claims fewer rows and leaves the rest for other replicas, instead
 * of queueing work it cannot start.
 *
 * <p>Shutdown: stops claiming first, then waits up to {@code shutdownGrace} for running
 * checks. Checks that are cut off are simply retried at the monitor's next interval.
 */
@Component
public class CheckScheduler implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(CheckScheduler.class);

    private final MonitorClaimRepository claims;
    private final CheckRunner runner;
    private final WorkerProperties properties;
    private final Semaphore slots;

    private volatile boolean running;
    private volatile Instant lastPollCompletedAt;
    private volatile boolean lastPollSucceeded;
    private ScheduledExecutorService poller;
    private ExecutorService checkPool;

    public CheckScheduler(MonitorClaimRepository claims, CheckRunner runner,
                          WorkerProperties properties) {
        this.claims = claims;
        this.runner = runner;
        this.properties = properties;
        this.slots = new Semaphore(properties.concurrency());
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        checkPool = Executors.newFixedThreadPool(properties.concurrency(),
                new CustomizableThreadFactory("check-"));
        poller = Executors.newSingleThreadScheduledExecutor(
                new CustomizableThreadFactory("check-scheduler-"));
        running = true;
        poller.scheduleWithFixedDelay(this::pollSafely, 0,
                properties.pollInterval().toMillis(), TimeUnit.MILLISECONDS);
        log.info("Check scheduler started: concurrency={} batchSize={} pollInterval={}",
                properties.concurrency(), properties.batchSize(), properties.pollInterval());
    }

    @Override
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        long graceMs = properties.shutdownGrace().toMillis();
        log.info("Check scheduler stopping: {} check(s) in flight", inFlight());
        poller.shutdown();
        checkPool.shutdown();
        try {
            poller.awaitTermination(graceMs, TimeUnit.MILLISECONDS);
            if (!checkPool.awaitTermination(graceMs, TimeUnit.MILLISECONDS)) {
                log.warn("Grace period elapsed; interrupting {} running check(s)", inFlight());
                checkPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            checkPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("Check scheduler stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return properties.schedulerEnabled();
    }

    /** Never lets an exception escape: that would silently cancel the fixed-delay schedule. */
    private void pollSafely() {
        try {
            poll();
            lastPollSucceeded = true;
        } catch (Exception e) {
            lastPollSucceeded = false;
            log.warn("Claiming due monitors failed, will retry: {}", e.toString());
        } finally {
            lastPollCompletedAt = Instant.now();
        }
    }

    void poll() {
        if (!running) {
            return;
        }
        // Only this thread acquires permits, so the available count can only grow meanwhile.
        int permits = Math.min(properties.batchSize(), slots.availablePermits());
        if (permits == 0 || !slots.tryAcquire(permits)) {
            return;
        }
        List<ClaimedMonitor> claimed;
        try {
            claimed = claims.claimDue(permits);
        } catch (RuntimeException e) {
            slots.release(permits);
            throw e;
        }
        slots.release(permits - claimed.size());
        for (ClaimedMonitor monitor : claimed) {
            submit(monitor);
        }
    }

    private void submit(ClaimedMonitor monitor) {
        try {
            checkPool.execute(() -> runCheck(monitor));
        } catch (RejectedExecutionException e) {
            // Pool is shutting down; the claim lapses and the monitor runs at its next interval.
            slots.release();
        }
    }

    private void runCheck(ClaimedMonitor monitor) {
        MDC.put("monitorId", monitor.id().toString());
        try {
            runner.run(monitor);
        } catch (Exception e) {
            log.error("Check runner failed unexpectedly", e);
        } finally {
            MDC.remove("monitorId");
            slots.release();
        }
    }

    public int inFlight() {
        return properties.concurrency() - slots.availablePermits();
    }

    public Instant lastPollCompletedAt() {
        return lastPollCompletedAt;
    }

    public boolean lastPollSucceeded() {
        return lastPollSucceeded;
    }
}
