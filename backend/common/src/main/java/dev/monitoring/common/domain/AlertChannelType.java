package dev.monitoring.common.domain;

public enum AlertChannelType {
    /** Generic HTTPS webhook receiving a signed JSON payload. */
    WEBHOOK,
    /** Slack incoming webhook (https://hooks.slack.com/services/...). */
    SLACK,
    /** E-mail via the configured SMTP server. */
    EMAIL
}
