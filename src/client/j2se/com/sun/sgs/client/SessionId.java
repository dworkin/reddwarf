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
     * Returns a session identifier whose representation is contained in
     * the specified byte buffer.
     *
     * @param id a byte array containing a session identifier
     *
     * @return a session identifier
     *
     * @throws IllegalArgumentException if the specified byte array
     * does not contain a valid representation of a
     * <code>SessionId</code>
     */
    public static SessionId fromBytes(final byte[] id) {
	return new SessionId() {
	    public byte[] toBytes() { return id; }
	    public boolean equals(Object obj) {
		if (! (obj instanceof SessionId))
		    return false;
		return id.equals(((SessionId)obj).toBytes());
	    }
	};
    }

    /**
     * Returns a byte array containing the representation of this
     * session identifier.
     *
     * @return a read-only byte buffer containing the representation
     * of this session identifier
     */
    public abstract byte[] toBytes();

    /**
     * Returns <code>true</code> if the specified object represents
     * the same session identifier as this one, and <code>false</code>
     * otherwise.
     *
     * @param obj an object to compare to
     *
     * @return <code>true</code> if the specified object represents
     * the the same session identifier, and <code>false</code> otherwise
     */
    public abstract boolean equals(Object obj);
}
