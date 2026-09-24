package dev.monitoring.common.net;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** Resolves a host name to its addresses. Abstracted so tests do not depend on real DNS. */
@FunctionalInterface
public interface HostResolver {

    HostResolver SYSTEM = InetAddress::getAllByName;

    InetAddress[] resolve(String host) throws UnknownHostException;
}
