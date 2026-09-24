package dev.monitoring.common.domain;

/** Why an incident was closed. */
public enum IncidentResolution {
    /** The target passed enough consecutive checks. */
    RECOVERED,
    /** The monitor was paused while the target was down. */
    MONITOR_PAUSED,
    /** The monitor's target or check settings changed while it was down. */
    MONITOR_CHANGED
}
