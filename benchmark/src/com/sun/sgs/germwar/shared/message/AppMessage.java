/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.germwar.shared.message;

import java.nio.ByteBuffer;

/**
 * Represents an application-level message sent either from client to server or
 * server to client.
 */
public interface AppMessage {
    /**
     * Returns this message's op-code.
     */
    OpCode getOpCode();

    /**
     * Writes all of the data of this object to {@code buf}.
     */
    ByteBuffer write(ByteBuffer buf);
}
