package com.sun.sgs.impl.client.simple;

import java.util.Arrays;

import com.sun.sgs.client.SessionId;

/**
 * A simple implementation of a SessionId that wraps a byte array.
 */
public class SimpleSessionId extends SessionId {

    /** The byte array representation of the session identifier. */
    private final byte[] id;

    /** The prefix to use when creating a toString() representation */
    private static final String SESSION_STRING_PREFIX = "Session@";

    /**
     * Construct a new {@code SimpleSessionId} from the given byte array.
     *
     * @param id the byte representation of the session id
     */
    public SimpleSessionId(byte[] id) {
        this.id = id;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        return (obj instanceof SessionId && 
                Arrays.equals(id, (((SessionId) obj).toBytes())));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Arrays.hashCode(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] toBytes() {
        return id;
    }

    /**
     * {@inheritDoc}
     */   
    @Override
    public String toString() {
        int len = SESSION_STRING_PREFIX.length() + id.length * 2;
        StringBuilder buf = new StringBuilder(len);
        buf.append(SESSION_STRING_PREFIX);
        for (byte b : id) {
            buf.append(String.format("%02X", b));
        }
        return buf.toString();
    }
}
