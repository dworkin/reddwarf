/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.germwar.shared.message;

import java.net.ProtocolException;
import java.nio.ByteBuffer;

/**
 * Sent by the server to update a client that a new turn is starting.
 */
public class TurnUpdate implements AppMessage {
    private long turn;

    /**
     * Creates a new {@code TurnUpdate} for turn {@code turn}.
     */
    public TurnUpdate(long turn) {
        this.turn = turn;
    }

    /**
     * Creates a new {@code TurnUpdate} by reading fields out of {@code
     * buf}.
     */
    public static TurnUpdate fromBytes(ByteBuffer buf) throws ProtocolException {
        return new TurnUpdate(buf.getLong());
    }

    /**
     * Returns the turn that this message is an update for.
     */
    public long getTurn() {
        return turn;
    }

    // implement AppMessage

    /**
     * {@inheritDoc}
     */
    public OpCode getOpCode() {
        return OpCode.TURN_UPDATE;
    }

    /**
     * {@inheritDoc}
     */
    public ByteBuffer write(ByteBuffer buf) {
        buf.putLong(getTurn());
        return buf;
    }
}
