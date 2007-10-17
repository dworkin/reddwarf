/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.germwar.shared.message;

import java.nio.ByteBuffer;

/**
 * Sent from a client to request that a chat message be sent to another user.
 * The server should respond with an ACK or NACK to communicate success/failure.
 */
public class ChatRequest implements AppMessage {
    private String recipient, message;
    private int requestId;

    /**
     * Creates a new {@code ChatRequest}.
     */
    public ChatRequest(String recipient, String message, int requestId) {
        this.recipient = recipient;
        this.message = message;
        this.requestId = requestId;
        
        if (recipient.length() > Short.MAX_VALUE) {
            throw new IllegalArgumentException("Recipient name too long (" +
                recipient.length() + ").");
        }

        if (message.length() > Short.MAX_VALUE) {
            throw new IllegalArgumentException("Message too long (" +
                recipient.length() + ").");
        }
    }

    /**
     * Creates a new {@code ChatRequest} by reading fields out of {@code buf}.
     */
    public static ChatRequest fromBytes(ByteBuffer buf) {
        short recipLen = buf.getShort();
        byte[] recipBytes = new byte[recipLen];
        buf.get(recipBytes, 0, recipLen);
        
        short msgLen = buf.getShort();
        byte[] msgBytes = new byte[msgLen];
        buf.get(msgBytes, 0, msgLen);

        int requestId = buf.getInt();
        
        return new ChatRequest(new String(recipBytes), new String(msgBytes),
            requestId);
    }

    /**
     * @return this message's ID.
     */
    public int getId() {
        return requestId;
    }

    /**
     * @return the content of this chat message request
     */
    public String getMessage() {
        return message;
    }

    /**
     * @return the intended recipient of this chat message request
     */
    public String getRecipient() {
        return recipient;
    }

    // implement AppMessage

    /**
     * {@inheritDoc}
     */
    public OpCode getOpCode() {
        return OpCode.CHAT_REQUEST;
    }

    /**
     * {@inheritDoc}
     */
    public ByteBuffer write(ByteBuffer buf) {
        buf.putShort((short)recipient.length());
        buf.put(recipient.getBytes());
        buf.putShort((short)message.length());
        buf.put(message.getBytes());
        buf.putInt(requestId);
        return buf;
    }
}
