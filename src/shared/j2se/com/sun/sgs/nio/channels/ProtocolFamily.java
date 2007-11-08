package com.sun.sgs.nio.channels;

/**
 * Represents a family of communication protocols.
 * <p>
 * [[Note: JSR-203 creates this interface in {@code java.net}]]
 */
public interface ProtocolFamily {

    /**
     * Returns the name of the protocol family.
     * 
     * @return the name of the protocol family
     */
    String name();
}
