package com.sun.sgs.client;

/**
 * Identifies a session between client and server.
 */
public abstract class SessionId {

    /**
     * Enables construction of a session identifier (for subclasses).
     */
    protected SessionId() {
    }

    /**
     * Returns a session identifier whose representation is contained in the
     * specified byte array.
     * 
     * @param id a byte array containing a session identifier
     * @return a session identifier
     * @throws IllegalArgumentException if the specified byte array does not
     *         contain a valid representation of a {@code SessionId}
     */
    public static SessionId fromBytes(byte[] id) {
        return new com.sun.sgs.impl.client.simple.SimpleSessionId(id);
    }

    /**
     * Returns a byte array containing the representation of this session
     * identifier.
     * 
     * @return a byte array containing the representation of this
     *         session identifier
     */
    public abstract byte[] toBytes();

    /**
     * Returns {@code true} if the specified object represents the
     * same session identifier as this one, and {@code false}
     * otherwise.
     * 
     * @param obj an object to compare to
     * @return {@code true} if the specified object represents the
     *         the same session identifier, and {@code false}
     *         otherwise
     */
    @Override
    public abstract boolean equals(Object obj);

}
