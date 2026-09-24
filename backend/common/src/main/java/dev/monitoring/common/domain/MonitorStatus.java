package dev.monitoring.common.domain;

/** Current health of a monitor as determined by incident detection. */
public enum MonitorStatus {
    /** Not checked yet, or not enough results to decide. */
    UNKNOWN,
    UP,
    DOWN
}
