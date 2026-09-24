package dev.monitoring.common.domain;

/** Classified reason for a failed check (see docs/architecture.md §5.2). */
public enum CheckErrorType {
    TIMEOUT,
    DNS_FAILURE,
    CONNECTION_REFUSED,
    TLS_ERROR,
    UNEXPECTED_STATUS,
    BODY_MISMATCH,
    /** Target resolved to a forbidden address (SSRF protection). */
    BLOCKED_TARGET,
    OTHER
}
