package com.sun.sgs.client;

import java.nio.ByteBuffer;

/**
 * Represents an address of a client session for identification and
 * communication purposes.
 */
public abstract class ClientAddress implements Comparable {

    /**
     * Enables construction of a client address (for subclasses).
     */
    protected ClientAddress() {
    }

    /**
     * Returns a client address whose representation is contained in
     * the specified byte buffer.
     *
     * @param address a byte buffer containing a client address
     * representation
     *
     * @return a client address
     *
     * @throws IllegalArgumentException if the specified address is not a
     * valid representation of a <code>ClientAddress</code>
     */
    public static ClientAddress fromBytes(ByteBuffer address) {
	// TBI
	return null;
    }

    /**
     * Returns a read-only byte buffer containing the representation
     * of this client address.
     *
     * @return a read-only byte buffer containing the representation
     * of this client address.
     */
    public abstract ByteBuffer toBytes();
}
