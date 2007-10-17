/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.germwar.shared.message;

import java.nio.ByteBuffer;

/**
 * Sent by the server to a client to deliver a chat message from some other
 * client.
 */
public class ChatMessage implements AppMessage {
    private String sender, message;

    /**
     * Creates a new {@code ChatMessage}.
     */
    public ChatMessage(String sender, String message) {
        this.sender = sender;
        this.message = message;
        
        if (sender.length() > Short.MAX_VALUE) {
            throw new IllegalArgumentException("Sender name too long (" +
                sender.length() + ").");
        }

        if (message.length() > Short.MAX_VALUE) {
            throw new IllegalArgumentException("Message too long (" +
                sender.length() + ").");
        }
    }

    /**
     * Creates a new {@code ChatMessage} by reading fields out of {@code buf}.
     */
    public static ChatMessage fromBytes(ByteBuffer buf) {
        short senderLen = buf.getShort();
        byte[] senderBytes = new byte[senderLen];
        buf.get(senderBytes, 0, senderLen);
        
        short msgLen = buf.getShort();
        byte[] msgBytes = new byte[msgLen];
        buf.get(msgBytes, 0, msgLen);
        
        return new ChatMessage(new String(senderBytes), new String(msgBytes));
    }

    /**
     * @return the content of this chat message
     */
    public String getMessage() {
        return message;
    }

    /**
     * @return the sender of this chat message
     */
    public String getSender() {
        return sender;
    }

    // implement AppMessage

    /**
     * {@inheritDoc}
     */
    public OpCode getOpCode() {
        return OpCode.CHAT_MESSAGE;
    }

    /**
     * {@inheritDoc}
     */
    public ByteBuffer write(ByteBuffer buf) {
        buf.putShort((short)sender.length());
        buf.put(sender.getBytes());
        buf.putShort((short)message.length());
        buf.put(message.getBytes());
        return buf;
    }
}
