/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.germwar.shared.message;

import java.net.ProtocolException;
import java.nio.ByteBuffer;

/**
 * Handles the transformations between instances of {@link AppMessage} and byte
 * arrays.
 *<p>
 *<b>NOT Thread-Safe!</b>
 */
public class Protocol {
    /**
     * Not instantiable.
     */
    private Protocol() { }

    /**
     * Creates an {@link AppMessage} from a byte array.
     *
     * @throws ProtocolException if unrecognized data is encountered.
     */
    public static AppMessage read(byte[] array) throws ProtocolException {
        ByteBuffer buf = ByteBuffer.wrap(array);
        byte opCodeKey = buf.get();
        OpCode opCode = OpCode.lookup(opCodeKey);

        if (opCode == null)
            throw new ProtocolException("Unrecognized opcode: " + opCodeKey);

        switch (opCode) {
        case NACK:
            return NackResponse.fromBytes(buf);

        case ACK:
            return AckResponse.fromBytes(buf);

        case CHAT_REQUEST:
            return ChatRequest.fromBytes(buf);
        
        case CHAT_MESSAGE:
            return ChatMessage.fromBytes(buf);
        
        case LOCATION_UPDATE:
            return LocationUpdate.fromBytes(buf);

        case INITIALIZATION_DATA:
            return InitializationData.fromBytes(buf);

        case TURN_UPDATE:
            return TurnUpdate.fromBytes(buf);

        case MOVE_REQUEST:
            return MoveRequest.fromBytes(buf);

        case SPLIT_REQUEST:
            return SplitRequest.fromBytes(buf);

        default:
            throw new IllegalStateException("Code out of sync..." +
                " OpCode.lookup() returned a value (" + opCode + ") that" +
                " is not handled in Protocol.read().");
        }
    }

    /**
     * Creates a byte array from an {@link AppMessage}.
     */
    public static byte[] write(AppMessage msg) {
        ByteBuffer buf = ByteBuffer.allocate(1024);
        buf.put(msg.getOpCode().getKey());
        msg.write(buf);
        buf.flip();
        
        byte ba[] = new byte[buf.limit()];
        buf.get(ba);
        return ba;
    }
}
