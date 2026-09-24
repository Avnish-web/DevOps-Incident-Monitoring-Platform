package dev.monitoring.common.domain;

/** HTTP methods allowed for checks. Only safe, body-less methods are permitted. */
public enum HttpCheckMethod {
    GET,
    HEAD
}
