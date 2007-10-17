/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.germwar.shared.message;

import java.nio.ByteBuffer;

import com.sun.sgs.germwar.shared.Coordinate;

/**
 * Represents a request from a client to the server to move a bacterium from one
 * location to another.
 */
public class MoveRequest implements AppMessage {
    private int bacteriumId;
    private Coordinate src, dest;

    /**
     * Creates a new {@code MoveRequest}.
     */
    public MoveRequest(int bacteriumId, Coordinate src, Coordinate dest) {
        this.bacteriumId = bacteriumId;
        this.src = src;
        this.dest = dest;
    }

    /**
     * Creates a new {@code MoveRequest} by reading fields out of {@code buf}.
     */
    public static MoveRequest fromBytes(ByteBuffer buf) {
        int bacteriumId = buf.getInt();
        Coordinate src = new Coordinate(buf.getInt(), buf.getInt());
        Coordinate dest = new Coordinate(buf.getInt(), buf.getInt());
        return new MoveRequest(bacteriumId, src, dest);
    }

    /**
     * @return the ID of the {@link Bacterium} to move.
     */
    public int getBacteriumId() {
        return bacteriumId;
    }

    /**
     * @return the {@link Coordinate} to move to.
     */
    public Coordinate getDestination() {
        return dest;
    }

    /**
     * @return the {@link Coordinate} to move from.
     */
    public Coordinate getSource() {
        return src;
    }

    // implement AppMessage

    /**
     * {@inheritDoc}
     */
    public OpCode getOpCode() {
        return OpCode.MOVE_REQUEST;
    }

    /**
     * {@inheritDoc}
     */
    public ByteBuffer write(ByteBuffer buf) {
        buf.putInt(bacteriumId);
        buf.putInt(src.getX()).putInt(src.getY());
        return buf.putInt(dest.getX()).putInt(dest.getY());
    }
}
