package dev.monitoring.worker.check;

import java.net.UnknownHostException;

/**
 * A host resolved to an address the SSRF policy forbids. Extends {@link UnknownHostException}
 * because that is the only checked exception a DNS resolver may throw; the check classifies
 * it as {@code BLOCKED_TARGET}, not as a DNS failure.
 */
public class BlockedTargetException extends UnknownHostException {

    public BlockedTargetException() {
        super("Target resolves to a private, loopback or reserved address");
    }
}
