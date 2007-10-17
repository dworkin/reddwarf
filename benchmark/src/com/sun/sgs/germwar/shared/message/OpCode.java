/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.germwar.shared.message;

/**
 * Op-codes for all types of messages that this application uses.
 */
public enum OpCode {
    NACK ((byte)0),
    ACK ((byte)1),
    CHAT_REQUEST ((byte)2),
    CHAT_MESSAGE ((byte)3),
    LOCATION_UPDATE ((byte)4),
    INITIALIZATION_DATA ((byte)5),
    TURN_UPDATE ((byte)6),
    MOVE_REQUEST ((byte)7),
    SPLIT_REQUEST ((byte)8);

    /** More space efficient than using Enum.ordinal() */
    private byte key;

    /**
     * Creates a new {@code OpCode}.
     */
    OpCode(byte key) {
        this.key = key;
    }
    
    /**
     * Returns this {@code OpCodes's} key.
     */
    public byte getKey() { return key; }
    
    /**
     * Returns the {@code OpCode} with the specified key.
     */
    public static OpCode lookup(byte key) {
        for (OpCode oc : OpCode.values()) {
            if (oc.getKey() == key)
                return oc;
        }
        
        return null;
    }
}
