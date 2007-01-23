package com.sun.sgs.impl.client.simple;

import com.sun.sgs.client.SessionId;

/**
 * A simple implementation of a SessionId that wraps a byte array.
 *
 * @author      Sten Anderson
 * @version     1.0
 */
public class SimpleSessionId extends SessionId {

    private byte[] id;

    /**
     * Construct a new {@code SimpleSessionId} from the given byte array.
     *
     * @param id the byte representation of the session id.
     */
    public SimpleSessionId(byte[] id) {
        this.id = id;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        return (obj instanceof SessionId && 
                toBytes().equals(((SessionId) obj).toBytes()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] toBytes() {
        return id;
    }

}
