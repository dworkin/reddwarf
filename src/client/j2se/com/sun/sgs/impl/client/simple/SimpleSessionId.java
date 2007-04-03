/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.client.simple;

import java.util.Arrays;

import com.sun.sgs.client.SessionId;
import com.sun.sgs.impl.sharedutil.HexDumper;

/**
 * A simple implementation of a SessionId that wraps a byte array.
 */
public class SimpleSessionId extends SessionId {

    /** The byte array representation of the session identifier. */
    private final byte[] id;

    /**
     * Construct a new {@code SimpleSessionId} from the given byte array.
     *
     * @param id the byte representation of the session id
     */
    public SimpleSessionId(byte[] id) {
        if (id == null)
            throw new NullPointerException("id must not be null");

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
        return getClass().getName() + "@" + HexDumper.toHexString(id);
    }
}
