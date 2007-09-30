package com.sun.sgs.nio.channels;

/**
 * Defines the standard family of communication protocols.
 * <p>
 * [[Note: JSR-203 creates this interface in {@code java.net}]]
 */
public enum StandardProtocolFamily implements ProtocolFamily {
    /** Internet Protocol Version 4 (IPv4) */
    INET,

    /** Internet Protocol Version 6 (IPv6) */
    INET6;
}
