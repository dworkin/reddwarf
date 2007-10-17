/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.germwar.shared.message;

import java.nio.ByteBuffer;

/**
 * Represents a failure acknowledgement of some other message.
 */
public class NackResponse implements AppMessage {
    private int referenceId;

    /**
     * Creates a new {@code NackResponse}.
     */
    public NackResponse(int referenceId) {
        this.referenceId = referenceId;
    }

    /**
     * Creates a new {@code NackResponse} by reading fields out of {@code buf}.
     */
    public static NackResponse fromBytes(ByteBuffer buf) {
        int referenceId = buf.getInt();
        return new NackResponse(referenceId);
    }

    /**
     * @return the ID of the original message (that this message is responding
     *         to).
     */
    public int getOriginalMsgId() {
        return referenceId;
    }

    // implement AppMessage

    /**
     * {@inheritDoc}
     */
    public OpCode getOpCode() {
        return OpCode.NACK;
    }

    /**
     * {@inheritDoc}
     */
    public ByteBuffer write(ByteBuffer buf) {
        return buf.putInt(referenceId);
    }
}
