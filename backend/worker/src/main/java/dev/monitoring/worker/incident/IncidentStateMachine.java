package dev.monitoring.worker.incident;

import dev.monitoring.common.domain.MonitorStatus;

/**
 * Pure incident-detection rules (docs/architecture.md §5.3).
 *
 * <pre>
 * UNKNOWN/UP --(failureThreshold consecutive failures)--> DOWN   : incident opened
 * DOWN       --(recoveryThreshold consecutive successes)--> UP   : incident resolved
 * UNKNOWN    --(first success)--> UP                              : no incident
 * </pre>
 *
 * Thresholds absorb single dropped packets and flapping; they are per-monitor settings.
 */
public final class IncidentStateMachine {

    public enum Event {
        NONE,
        INCIDENT_OPENED,
        INCIDENT_RESOLVED
    }

    public record State(MonitorStatus status, int consecutiveFailures, int consecutiveSuccesses) {
    }

    public record Transition(State next, Event event) {
    }

    private IncidentStateMachine() {
    }

    public static Transition apply(State current, boolean success, int failureThreshold,
                                   int recoveryThreshold) {
        if (success) {
            int successes = current.consecutiveSuccesses() + 1;
            if (current.status() == MonitorStatus.DOWN) {
                return successes >= recoveryThreshold
                        ? new Transition(new State(MonitorStatus.UP, 0, successes),
                                Event.INCIDENT_RESOLVED)
                        : new Transition(new State(MonitorStatus.DOWN, 0, successes), Event.NONE);
            }
            return new Transition(new State(MonitorStatus.UP, 0, successes), Event.NONE);
        }
        int failures = current.consecutiveFailures() + 1;
        if (current.status() != MonitorStatus.DOWN && failures >= failureThreshold) {
            return new Transition(new State(MonitorStatus.DOWN, failures, 0),
                    Event.INCIDENT_OPENED);
        }
        return new Transition(new State(current.status(), failures, 0), Event.NONE);
    }
}
