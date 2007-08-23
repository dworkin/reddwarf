/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.germwar.shared.message;

import java.nio.ByteBuffer;

/**
 * Sent by the server to a client when the client logs in to provide various
 * bits of initialization information.
 */
public class InitializationData implements AppMessage {
    private long playerId;

    /**
     * Creates a new {@code InitializationData}.
     */
    public InitializationData(long playerId) {
        this.playerId = playerId;
    }

    /**
     * Creates a new {@code InitializationData} by reading fields out of {@code
     * buf}.
     */
    public static InitializationData fromBytes(ByteBuffer buf) {
        return new InitializationData(buf.getLong());
    }

    /**
     * @return this client's player-ID
     */
    public long playerId() {
        return playerId;
    }

    // implement AppMessage

    /**
     * {@inheritDoc}
     */
    public OpCode getOpCode() {
        return OpCode.INITIALIZATION_DATA;
    }

    /**
     * {@inheritDoc}
     */
    public ByteBuffer write(ByteBuffer buf) {
        buf.putLong(playerId);
        return buf;
    }
}
