package com.sun.sgs.client;

import java.nio.ByteBuffer;

/**
 * Represents an address of a client session for identification and
 * communication purposes.
 */
public abstract class ClientAddress {

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

    /**
     * Returns <code>true</code> if the specified object represents
     * the same client address as this one, and <code>false</code>
     * otherwise.
     *
     * @return <code>true</code> if the specified object represents
     * the the same client, and <code>false</code> otherwise
     */
    public abstract boolean equals(Object obj);
}
