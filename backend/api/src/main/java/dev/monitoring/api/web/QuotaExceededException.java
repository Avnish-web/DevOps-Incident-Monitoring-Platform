package dev.monitoring.api.web;

/**
 * A per-user resource limit was reached (HTTP 409). Limits stop a single account from
 * turning the worker into a traffic generator against third-party hosts.
 */
public class QuotaExceededException extends RuntimeException {

    public QuotaExceededException(String resource, long limit) {
        super("Limit reached: at most " + limit + " " + resource + " per user");
    }
}
