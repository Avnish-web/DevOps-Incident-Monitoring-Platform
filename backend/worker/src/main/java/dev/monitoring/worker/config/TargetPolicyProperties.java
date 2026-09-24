package dev.monitoring.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SSRF policy, shared name with the API ({@code monitoring.targets.*}).
 *
 * @param allowPrivateAddresses allow checks against loopback/private/link-local addresses.
 *                              Off by default; only for installations that intentionally
 *                              monitor internal services.
 */
@ConfigurationProperties(prefix = "monitoring.targets")
public record TargetPolicyProperties(boolean allowPrivateAddresses) {
}
