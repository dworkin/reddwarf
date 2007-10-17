/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.germwar.shared.message;

import java.nio.ByteBuffer;

/**
 * Represents a successful acknowledgement of some other message.
 */
public class AckResponse implements AppMessage {
    private int referenceId;

    /**
     * Creates a new {@code AckResponse}.
     */
    public AckResponse(int referenceId) {
        this.referenceId = referenceId;
    }

    /**
     * Creates a new {@code AckResponse} by reading fields out of {@code buf}.
     */
    public static AckResponse fromBytes(ByteBuffer buf) {
        int referenceId = buf.getInt();
        return new AckResponse(referenceId);
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
        return OpCode.ACK;
    }

    /**
     * {@inheritDoc}
     */
    public ByteBuffer write(ByteBuffer buf) {
        return buf.putInt(referenceId);
    }
}
